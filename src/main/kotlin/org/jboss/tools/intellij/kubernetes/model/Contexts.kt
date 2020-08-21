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
package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient
import org.jboss.tools.intellij.kubernetes.model.context.Context
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IContext

interface IContexts {
	val current: IActiveContext<out HasMetadata, out KubernetesClient>?
	val all: List<IContext>
	fun setCurrent(context: IContext)
	fun clear()
}

open class Contexts(
		private val observable: IModelChangeObservable = ModelChangeObservable(),
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

	protected open val config: KubeConfig by lazy {
		KubeConfig(::refresh)
	}

	override fun setCurrent(context: IContext) {
		if (context == current) {
			return
		}
		var newContext: IActiveContext<out HasMetadata, out KubernetesClient>?
		synchronized(this) {
			closeCurrent()
			newContext = create(context.context)
			current = newContext
			replaceInAllContexts(newContext!!)
		}
		if (newContext != null) {
			observable.fireModified(newContext!!)
		}
	}

	override fun clear() {
		clear(true)
	}

	private fun clear(notify: Boolean) {
		synchronized(this) {
			all.clear()
			closeCurrent(notify)
		}
	}

	protected open fun refresh() {
		clear(false)
		observable.fireModified(this) // invalidates root bcs there's no tree node for this
	}

	private fun closeCurrent() {
		closeCurrent(true)
	}

	private fun closeCurrent(notify: Boolean) {
		val current = this.current
		current?.close() ?: return
		this.current = null
		if (notify) {
			observable.fireModified(current)
		}
	}

	private fun create(namedContext: NamedContext): IActiveContext<out HasMetadata, out KubernetesClient> {
		val context = factory(observable, namedContext)
		context.startWatch()
		return context
	}

	private fun replaceInAllContexts(activeContext: IActiveContext<*, *>) {
		val contextInAll = all.find { it.context == activeContext.context } ?: return
		val indexOf = all.indexOf(contextInAll)
		if (indexOf < 0) {
			return
		}
		all[indexOf] = activeContext
	}
}