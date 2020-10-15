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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.model.context.ContextFactory
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import java.util.function.Predicate

interface IResourceModel {

    fun setCurrentContext(context: IContext)
    fun getCurrentContext(): IActiveContext<out HasMetadata, out KubernetesClient>?
    fun getAllContexts(): List<IContext>
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> resources(kind: ResourceKind<R>): Namespaceable<R>
    fun resources(definition: CustomResourceDefinition): ListableCustomResources
    fun watch(kind: ResourceKind<out HasMetadata>)
    fun invalidate(element: Any?)
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
}

open class ResourceModel(
        private val observable: IModelChangeObservable = ModelChangeObservable(),
        private val contexts: IContexts = Contexts(observable, ContextFactory()::create)
) : IResourceModel {

    override fun setCurrentContext(context: IContext) {
        if (contexts.setCurrent(context)) {
            observable.fireModified(this)
        }
    }

    override fun getCurrentContext(): IActiveContext<out HasMetadata, out KubernetesClient>? {
        return contexts.current
    }

    override fun getAllContexts(): List<IContext> {
        return contexts.all
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener)
    }

    override fun setCurrentNamespace(namespace: String) {
        contexts.current?.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return contexts.current?.getCurrentNamespace()
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${contexts.current?.masterUrl}", e)
        }
    }

    override fun <R: HasMetadata> resources(kind: ResourceKind<R>): Namespaceable<R> {
        return Namespaceable(kind, this)
    }

    override fun resources(definition: CustomResourceDefinition): ListableCustomResources {
        return ListableCustomResources(definition,this)
    }

    fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, namespaced: ResourcesIn, filter: Predicate<R>? = null): Collection<R> {
        try {
            val resources: Collection<R> = contexts.current?.getAllResources(kind, namespaced) ?: return emptyList()
            return if (filter == null) {
                resources
            } else {
                resources.filter { filter.test(it) }
            }
        } catch (e: KubernetesClientException) {
            if (isNotFound(e)) {
                return emptyList()
            }
            throw ResourceException("Could not get ${kind.kind}s for server ${contexts.current?.masterUrl}", e)
        }
    }

    fun getAllResources(definition: CustomResourceDefinition): Collection<HasMetadata> {
        try {
            return contexts.current?.getResources(definition) ?: emptyList()
        } catch(e: IllegalArgumentException) {
            throw ResourceException("Could not get custom resources for ${definition.metadata}: ${e.cause}", e)
        }
    }

    override fun watch(kind: ResourceKind<out HasMetadata>) {
        contexts.current?.watch(kind)
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is ResourceModel -> invalidate()
            is IActiveContext<*, *> -> invalidate(element)
            is ResourceKind<*> -> invalidate(element)
            is HasMetadata -> replace(element)
        }
    }

    private fun invalidate() {
        logger<ResourceModel>().debug("Invalidating all contexts.")
        if (contexts.clear()) {
            observable.fireModified(this)
        }
    }

    private fun invalidate(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
        context.invalidate()
    }

    private fun invalidate(kind: ResourceKind<*>) {
        contexts.current?.invalidate(kind)
    }

    private fun replace(resource: HasMetadata) {
        contexts.current?.replace(resource)
    }

    private fun isNotFound(e: KubernetesClientException): Boolean {
        return e.code == 404
    }
}
