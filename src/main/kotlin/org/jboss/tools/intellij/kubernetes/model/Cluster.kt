/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
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
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

interface ICluster {
    val client: NamespacedKubernetesClient
    fun startWatch()
    fun close()
    fun invalidate()
    fun getAllNamespaces(): List<Namespace>
    fun setCurrentNamespace(name: String)
    fun getCurrentNamespace(): Namespace?
    fun getNamespace(name: String): Namespace?
    fun getNamespaceProvider(name: String?): NamespaceProvider?
    fun getNamespaceProvider(resource: HasMetadata): NamespaceProvider?
    fun getNamespaceProvider(namespace: Namespace): NamespaceProvider?
}

open class Cluster(private val modelChange: IModelChangeObservable) : ICluster {

    override val client = createClient()

    private val namespaceProviders: MutableMap<String, NamespaceProvider> = mutableMapOf()
        get() {
            if (field.isEmpty()) {
                val namespaceProviders = loadAllNamespaces()
                    .map { Pair(it.metadata.name, createNamespaceProvider(it)) }
                field.putAll(namespaceProviders)
            }
            return field
        }

    protected open var watch: KubernetesResourceWatch = KubernetesResourceWatch(
        addOperation = { add(it) },
        removeOperation = { remove(it) })

    override fun startWatch() {
        val watchables = getWatchableResources() ?: return
        watch.addAll(watchables)
    }

    private fun stopWatch() {
        val watchables = getWatchableResources() ?: return
        watch.removeAll(watchables)
    }

    override fun close() {
        client.close()
    }

    override fun invalidate() {
        namespaceProviders.clear()
    }

    override fun getAllNamespaces(): List<Namespace> {
        return namespaceProviders.entries.map { it.value.namespace }
    }

    override fun setCurrentNamespace(name: String) {
        stopWatch()
        client.configuration.namespace = name
        val currentNamespace = getCurrentNamespace()
        if (currentNamespace != null) {
            getNamespaceProvider(currentNamespace)?.invalidate()
        }
        modelChange.fireCurrentNamespace(getNamespace(name))
        startWatch()
    }

    override fun getCurrentNamespace(): Namespace? {
        var currentNamespaceName: String? = client.namespace
        if (currentNamespaceName == null) {
            currentNamespaceName = getAllNamespaces().firstOrNull()?.metadata?.name
            if (currentNamespaceName == null) {
                return null
            }
        }
        return getNamespace(currentNamespaceName)
    }

    override fun getNamespace(name: String): Namespace? {
        return getNamespaceProvider(name)?.namespace
    }

    override fun getNamespaceProvider(name: String?): NamespaceProvider? {
        return namespaceProviders[name]
    }

    override fun getNamespaceProvider(namespace: Namespace): NamespaceProvider? {
        return getNamespaceProvider(namespace.metadata?.name)
    }

    override fun getNamespaceProvider(resource: HasMetadata): NamespaceProvider? {
        val name = when(resource) {
            is Namespace -> resource.metadata.name
            else -> resource.metadata.namespace
        }
        return getNamespaceProvider(name)
    }

    private fun loadAllNamespaces(): Sequence<Namespace> {
        return  client.namespaces().list().items.asSequence()
    }

    fun add(resource: HasMetadata): Boolean {
        val added = when(resource) {
            is Namespace -> addNamespace(resource)
            else -> addNamespaceChild(resource)
        }
        if (added) {
            modelChange.fireAdded(resource)
        }
        return added
    }

    private fun addNamespace(namespace: Namespace): Boolean {
        val provider = createNamespaceProvider(namespace)
        return namespaceProviders.putIfAbsent(namespace.metadata.name, provider) == null
    }

    private fun addNamespaceChild(resource: HasMetadata): Boolean {
        val provider = getNamespaceProvider(resource)
        return provider != null
                && provider.add(resource)
    }

    fun remove(resource: HasMetadata): Boolean {
        val removed = when (resource) {
            is Namespace -> removeNamespace(resource)
            else -> removeNamespaceChild(resource)
        }
        if (removed) {
            modelChange.fireRemoved(resource)
        }
        return removed
    }

    private fun removeNamespace(namespace: Namespace): Boolean {
        return namespaceProviders.remove(namespace.metadata.name) != null
    }

    private fun removeNamespaceChild(resource: HasMetadata): Boolean {
        val provider = getNamespaceProvider(resource)
        return provider != null
                && provider.remove(resource)
    }

    protected open fun createNamespaceProvider(namespace: Namespace) = NamespaceProvider(client, namespace)

    protected open fun createClient(): NamespacedKubernetesClient {
        return DefaultKubernetesClient(ConfigBuilder().build())
    }

    protected open fun getWatchableResources(): List<WatchableResourceSupplier>? {
        val currentNamespace = getCurrentNamespace() ?: return null
        return getNamespaceProvider(currentNamespace)?.getWatchableResources() ?: return null
    }

}
