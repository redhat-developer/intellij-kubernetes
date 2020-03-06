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
package org.jboss.tools.intellij.kubernetes.model.cluster

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.WatchableResourceSupplier
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider

abstract class AbstractCluster<N: HasMetadata, C: KubernetesClient>(
    private val modelChange: IModelChangeObservable,
    override val client: C
) : ICluster<N, C> {

    protected var namespace: String? = client.configuration.namespace
    protected abstract val resourceProviders: Map<Class<out HasMetadata>, IResourcesProvider<out HasMetadata>>

    override fun <T: HasMetadata> getResources(kind: Class<T>): Collection<T> {
        try {
            val provider = resourceProviders[kind] ?: return emptyList()
            return provider.getAllResources() as Collection<T>
        } catch(e: KubernetesClientException) {
            throw ResourceException(
                "Could not load ${kind.simpleName}s"
                        + if (namespace != null) { " in namespace $namespace" } else { "" }
                        + " on server ${client.masterUrl}", e)
        }
    }

    protected open var watch: ResourceWatch =
        ResourceWatch(
            addOperation = { add(it) },
            removeOperation = { remove(it) })

    override fun setCurrentNamespace(namespace: String) {
        val currentNamespace = getCurrentNamespace()
        if (namespace == currentNamespace) {
            return
        }

        if (currentNamespace != null) {
            stopWatch(currentNamespace)
        }
        client.configuration.namespace = namespace
        resourceProviders
            .filter { it.value is INamespacedResourcesProvider<*> }
            .forEach { (it.value as INamespacedResourcesProvider).namespace = namespace }
        startWatch(namespace)
        modelChange.fireCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        var name: String? = client.configuration.namespace
        if (name == null) {
            name = getNamespaces().firstOrNull()?.metadata?.name
        }
        return name
    }

    override fun add(resource: HasMetadata): Boolean {
        val provider = resourceProviders[resource::class.java] ?: return false
        val added = provider.add(resource)
        if (added) {
            modelChange.fireAdded(resource)
        }
        return added
    }

    override fun remove(resource: HasMetadata): Boolean {
        val provider = resourceProviders[resource::class.java] ?: return false
        val removed = provider.remove(resource)
        if (removed) {
            modelChange.fireAdded(resource)
        }
        return removed
    }

    protected open fun getWatchableResources(namespace: String): List<WatchableResourceSupplier?> {
        return resourceProviders.values
            .map {
                when (it) {
                    is INamespacedResourcesProvider ->
                        it.getWatchableResource(namespace)
                    is INonNamespacedResourcesProvider ->
                        it.getWatchableResource()
                    else -> null
                }
            }
    }

    override fun invalidate() {
        resourceProviders.values.forEach { it.invalidate() }
    }

    override fun invalidate(resource: HasMetadata) {
        resourceProviders[resource::class.java]?.invalidate(resource)
    }

    override fun startWatch() {
        val namespace = getCurrentNamespace() ?: return
        startWatch(namespace)
    }

    private fun startWatch(namespace: String) {
        try {
            watch.addAll(getWatchableResources(namespace))
        } catch (e: ResourceException) {
            logger<ResourceWatch>().warn("Could not start watching resources on server ${client.masterUrl}", e)
        }
    }

    private fun stopWatch(namespace: String) {
        watch.removeAll(getWatchableResources(namespace))
    }

    override fun close() {
        client.close()
    }

}
