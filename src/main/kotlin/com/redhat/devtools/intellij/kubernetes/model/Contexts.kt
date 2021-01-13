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

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient
import com.redhat.devtools.intellij.kubernetes.model.context.Context
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext

interface IContexts {
	val current: IActiveContext<out HasMetadata, out KubernetesClient>?
	val all: List<IContext>
	fun setCurrent(context: IContext): Boolean
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
		return factory(modelObservable, namedContext)
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