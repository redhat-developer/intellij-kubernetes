/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.utils.ExecHelper
import com.redhat.devtools.intellij.kubernetes.model.context.Context
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.NAME_PREFIX_CONTEXT
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_IS_OPENSHIFT
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_KUBERNETES_VERSION
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_OPENSHIFT_VERSION
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient

interface IContexts {
	val current: IActiveContext<out HasMetadata, out KubernetesClient>?
	val all: List<IContext>
	fun setCurrent(context: IContext): Boolean
	fun setCurrentNamespace(namespace: String): Boolean
	fun clear(): Boolean
}

open class Contexts(
	private val modelObservable: IModelChangeObservable = ModelChangeObservable(),
	private val factory: (IModelChangeObservable, NamedContext) -> IActiveContext<out HasMetadata, out KubernetesClient>
) : IContexts {

	override var current: IActiveContext<out HasMetadata, out KubernetesClient>? = null
		get() {
			synchronized(this) {
				if (field == null
						&& config.currentContext != null) {
					field = create(config.currentContext!!)
				}
				return field
			}
		}

	override val all: MutableList<IContext> = mutableListOf()
		get() {
			synchronized(this) {
				if (field.isEmpty()) {
					field.addAll(config.contexts.mapNotNull {
						if (config.isCurrent(it)) {
							current
						} else {
							Context(it)
						}
					})
				}
			}
			return field
		}

	protected open val config: ClientConfig by lazy {
		ClientConfig(::refresh)
	}

	override fun setCurrent(context: IContext): Boolean {
		if (context == current) {
			return false
		}
		synchronized(this) {
			if (current != null) {
				replaceActiveContext(Context(current!!.context), all)
			}
			closeCurrent()
			val newContext = create(context.context)
			current = newContext
			replaceContext(newContext, all)
			config.setCurrentContext(context.context)
			config.save()
		}
		return true
	}

	override fun setCurrentNamespace(namespace: String): Boolean {
		synchronized(this) {
			if (current == null
				|| !current!!.setCurrentNamespace(namespace)) {
				return false
			}
			config.setCurrentNamespace(namespace)
			config.save()
		}
		return true
	}

	override fun clear(): Boolean {
		synchronized(this) {
			all.clear()
			return closeCurrent()
		}
	}

	protected open fun refresh() {
		if (clear()) {
			modelObservable.fireModified(this) // invalidates root bcs there's no tree node for this
		}
	}

	private fun closeCurrent(): Boolean {
		val current = this.current
		current?.close() ?: return false
		this.current = null
		return true
	}

	private fun create(namedContext: NamedContext): IActiveContext<out HasMetadata, out KubernetesClient> {
		val context = factory(modelObservable, namedContext)
		reportTelemetry(context)
		return context
	}

	open protected fun reportTelemetry(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
		ExecHelper.submit {
			val telemetry = TelemetryService.instance.action(NAME_PREFIX_CONTEXT + "use")
				.property(PROP_IS_OPENSHIFT, context.isOpenShift().toString())
			try {
				telemetry
					.property(PROP_KUBERNETES_VERSION, context.version.kubernetesVersion)
					.property(PROP_OPENSHIFT_VERSION, context.version.openshiftVersion)
					.send()
			} catch (e: RuntimeException) {
				telemetry
					.property(PROP_KUBERNETES_VERSION, "error retrieving")
					.property(PROP_OPENSHIFT_VERSION, "error retrieving")
					.send()
				logger<Contexts>().warn("Could not report context/cluster versions", e)
			}
		}
	}

	private fun replaceContext(activeContext: IActiveContext<*, *>, all: MutableList<IContext>) {
		val indexOf = indexOf(activeContext.context, all) ?: return
		all[indexOf] = activeContext
	}

	private fun replaceActiveContext(context: IContext, all: MutableList<IContext>) {
		val indexOf = indexOf(context.context, all) ?: return
		all[indexOf] = context
	}

	private fun indexOf(context: NamedContext, all: MutableList<IContext>): Int? {
		val contextInAll = all.find { it.context == context } ?: return null
		val indexOf = all.indexOf(contextInAll)
		if (indexOf < 0) {
			return null
		}
		return indexOf
	}

}