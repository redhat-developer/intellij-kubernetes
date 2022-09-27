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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.isNotFound
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.LogWatch
import java.io.OutputStream
import java.util.function.Predicate

interface IResourceModel {

    companion object Factory {
        fun getInstance(): IResourceModel {
            return ApplicationManager.getApplication().getService(IResourceModel::class.java)
        }
    }

    fun setCurrentContext(context: IContext)
    fun getCurrentContext(): IActiveContext<out HasMetadata, out KubernetesClient>?
    fun getAllContexts(): List<IContext>
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun isCurrentNamespace(resource: HasMetadata): Boolean
    fun <R: HasMetadata> resources(kind: ResourceKind<R>): Namespaceable<R>
    fun resources(definition: CustomResourceDefinition): ListableCustomResources
    fun watch(kind: ResourceKind<out HasMetadata>)
    fun watch(definition: CustomResourceDefinition)
    fun stopWatch(kind: ResourceKind<out HasMetadata>)
    fun stopWatch(definition: CustomResourceDefinition)
    fun invalidate(element: Any?)
    fun delete(resources: List<HasMetadata>)
    fun watchLog(resource: HasMetadata, out: OutputStream): LogWatch?
    fun canFollowLogs(resource: HasMetadata): Boolean
    fun addListener(listener: IResourceModelListener)
    fun removeListener(listener: IResourceModelListener)
}

/**
 * The single resource model that UI can query for all resources and use to create, modify them.
 *
 * <h3>WARNING<h3>: no argument constructor required because this class is instantiated by IJ ServiceManager
 *
 * @see [issue 180](https://github.com/redhat-developer/intellij-kubernetes/issues/180)
 * @see [com.redhat.devtools.intellij.kubernetes.tree.ResourceTreeToolWindowFactory.createTree]
 * @see [com.redhat.devtools.intellij.kubernetes.actions.getResourceModel]
 */
open class ResourceModel : IResourceModel {

    protected open val modelChange: IResourceModelObservable by lazy {
        ResourceModelObservable()
    }

    protected open val allContexts: IAllContexts by lazy {
        AllContexts(IActiveContext.Factory::create, modelChange)
    }

    override fun setCurrentContext(context: IContext) {
        allContexts.setCurrentContext(context)
    }

    override fun getCurrentContext(): IActiveContext<out HasMetadata, out KubernetesClient>? {
        return allContexts.current
    }

    override fun getAllContexts(): List<IContext> {
        return allContexts.all
    }

    override fun setCurrentNamespace(namespace: String) {
        allContexts.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return allContexts.current?.getCurrentNamespace()
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${allContexts.current?.masterUrl}", e)
        }
    }

    override fun isCurrentNamespace(resource: HasMetadata): Boolean {
        try {
            return allContexts.current?.isCurrentNamespace(resource) ?: false
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${allContexts.current?.masterUrl}", e
            )
        }
    }

    override fun <R: HasMetadata> resources(kind: ResourceKind<R>): Namespaceable<R> {
        return Namespaceable(kind, this)
    }

    override fun resources(definition: CustomResourceDefinition): ListableCustomResources {
        return ListableCustomResources(definition,this)
    }

    fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, resourceIn: ResourcesIn, filter: Predicate<R>? = null): Collection<R> {
        try {
            val resources: Collection<R> = allContexts.current?.getAllResources(kind, resourceIn) ?: return emptyList()
            return if (filter == null) {
                resources
            } else {
                resources.filter { filter.test(it) }
            }
        } catch (e: KubernetesClientException) {
            if (e.isNotFound()) {
                return emptyList()
            }
            throw ResourceException("Could not get ${kind.kind}s for server ${allContexts.current?.masterUrl}", e)
        }
    }

    fun getAllResources(definition: CustomResourceDefinition): Collection<HasMetadata> {
        try {
            return allContexts.current?.getAllResources(definition) ?: emptyList()
        } catch(e: IllegalArgumentException) {
            throw ResourceException("Could not get custom resources for ${definition.metadata}: ${e.cause}", e)
        }
    }

    override fun watch(kind: ResourceKind<out HasMetadata>) {
        allContexts.current?.watch(kind)
    }

    override fun watch(definition: CustomResourceDefinition) {
        allContexts.current?.watch(definition)
    }

    override fun stopWatch(kind: ResourceKind<out HasMetadata>) {
        allContexts.current?.stopWatch(kind)
    }

    override fun stopWatch(definition: CustomResourceDefinition) {
        allContexts.current?.stopWatch(definition)
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is IResourceModel -> invalidate()
            is IActiveContext<*, *> -> invalidate(element)
            is ResourceKind<*> -> invalidate(element)
            is HasMetadata -> replaced(element)
        }
    }

    override fun addListener(listener: IResourceModelListener) {
        modelChange.addListener(listener)
    }

    override fun removeListener(listener: IResourceModelListener) {
        modelChange.removeListener(listener)
    }

    private fun invalidate() {
        logger<ResourceModel>().debug("Invalidating all contexts.")
        allContexts.refresh()
    }

    private fun invalidate(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
        context.invalidate()
    }

    private fun invalidate(kind: ResourceKind<*>) {
        allContexts.current?.invalidate(kind)
    }

    private fun replaced(resource: HasMetadata) {
        allContexts.current?.replaced(resource)
    }

    override fun delete(resources: List<HasMetadata>) {
        allContexts.current?.delete(resources)
    }

    override fun watchLog(resource: HasMetadata, out: OutputStream): LogWatch? {
        return allContexts.current?.watchLog(resource, out)
    }

    override fun canFollowLogs(resource: HasMetadata): Boolean {
        return true == allContexts.current?.canWatchLog(resource)
    }

}
