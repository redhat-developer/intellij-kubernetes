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
import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.model.context.ActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.ContextFactory
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import org.jboss.tools.intellij.kubernetes.model.context.Context
import org.jboss.tools.intellij.kubernetes.model.util.KubeConfigContexts

interface IResourceModel {
    val allContexts: List<IContext>
    val currentContext: IActiveContext<out HasMetadata, out KubernetesClient>?
    fun getClient(): KubernetesClient?
    fun setCurrentContext(context: IContext?)
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R>
    fun getKind(resource: HasMetadata): Class<out HasMetadata>
    fun invalidate(element: Any?)
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
}

class ResourceModel(
    private val observable: IModelChangeObservable = ModelChangeObservable(),
    private val contextFactory: (IModelChangeObservable, NamedContext?) -> IActiveContext<out HasMetadata, out KubernetesClient> =
        ContextFactory()::create,
    private val config: KubeConfigContexts = KubeConfigContexts()
) : IResourceModel {

    override val allContexts: MutableList<IContext> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field.addAll(config.contexts.mapNotNull {
                    if (config.isCurrent(it)) {
                        currentContext ?: createContext(it)
                    } else {
                        Context(it)
                    }
                })
            }
            return field
        }

    override var currentContext: IActiveContext<out HasMetadata, out KubernetesClient>? = null
        get() {
            if (field == null) {
                field = createContext(config.current)
            }
            return field
        }

    override fun setCurrentContext(context: IContext?) {

    }

    private fun createContext(namedContext: NamedContext?): IActiveContext<out HasMetadata, out KubernetesClient> {
        val context = contextFactory(observable, namedContext)
        context.startWatch()
        this.currentContext = context
        return context
    }

    private fun closeCurrentContext() {
        currentContext?.close()
        currentContext = null
    }

    override fun getClient(): KubernetesClient? {
        return currentContext?.client
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener)
    }

    override fun setCurrentNamespace(namespace: String) {
        currentContext?.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return currentContext?.getCurrentNamespace() ?: return null
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${currentContext?.client?.masterUrl}", e)
        }
    }

    override fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R> {
        try {
            return currentContext?.getResources(kind) ?: return emptyList()
        } catch (e: KubernetesClientException) {
            if (isNotFound(e)) {
                return emptyList()
            }
            throw ResourceException("Could not get ${kind.simpleName}s for server ${currentContext?.client?.masterUrl}", e)
        }
    }

    override fun getKind(resource: HasMetadata): Class<out HasMetadata> {
        return resource::class.java
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is ResourceModel -> invalidate()
            is Class<*> -> invalidate(element)
            is HasMetadata -> invalidate(element)
        }
    }

    private fun invalidate() {
        closeCurrentContext()
        allContexts.clear()
        observable.fireModified(this)
    }

    private fun invalidate(kind: Class<*>) {
        val hasMetadataClass = kind as? Class<HasMetadata> ?: return
        currentContext?.invalidate(hasMetadataClass) ?: return
        observable.fireModified(hasMetadataClass)
    }

    private fun invalidate(resource: HasMetadata) {
        currentContext?.invalidate(resource) ?: return
        observable.fireModified(resource)
    }

    private fun isNotFound(e: KubernetesClientException): Boolean {
        return e.code == 404
    }

}