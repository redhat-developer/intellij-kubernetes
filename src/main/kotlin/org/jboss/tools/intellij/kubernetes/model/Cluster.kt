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
    fun watch()
    fun close()
    fun clear()
    fun getAllNamespaces(): List<Namespace>
    fun getNamespace(name: String): Namespace?
    fun getNamespaceProvider(name: String): NamespaceProvider?
    fun getNamespaceProvider(resource: HasMetadata): NamespaceProvider?
    fun getNamespaceProvider(namespace: Namespace): NamespaceProvider?
    fun add(resource: HasMetadata)
}

open class Cluster(private val resourceChange: IResourceChangeObservable) : ICluster {

    override val client = createClient()

    private val namespaceProviders: MutableMap<String, NamespaceProvider> = mutableMapOf()
        get() {
            if (field.isEmpty()) {
                val namespaceProviders = loadAllNamespaces()
                    .map { Pair(it.metadata.name, NamespaceProvider(client, it)) }
                field.putAll(namespaceProviders)
            }
            return field
        }

    override fun watch() {
        createResourceWatch(getWatchableProviders(client))
    }

    override fun close() {
        client.close()
    }

    override fun clear() {
        namespaceProviders.clear()
    }

    override fun getAllNamespaces(): List<Namespace> {
        return namespaceProviders.entries.map { it.value.namespace }
    }

    override fun getNamespace(name: String): Namespace? {
        return getNamespaceProvider(name)?.namespace
    }

    override fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders[name]
    }

    override fun getNamespaceProvider(namespace: Namespace): NamespaceProvider? {
        return getNamespaceProvider(namespace.metadata.name)
    }

    override fun getNamespaceProvider(resource: HasMetadata): NamespaceProvider? {
        return when(resource) {
            is Namespace -> getNamespaceProvider(resource)
            else -> getNamespaceProvider(resource.metadata.namespace)
        }
    }

    private fun loadAllNamespaces(): Sequence<Namespace> {
        return  client.namespaces().list().items.asSequence()
    }

    override fun add(resource: HasMetadata) {
        val added = when(resource) {
            is Namespace -> addNamespace(resource)
            else -> addNamespaceChild(resource)
        }
        if (added) {
            resourceChange.fireAdded(listOf(resource))
        }
    }

    private fun addNamespace(namespace: Namespace): Boolean {
        val provider = NamespaceProvider(client, namespace)
        return namespaceProviders.putIfAbsent(namespace.metadata.name, provider) == null
    }

    private fun addNamespaceChild(resource: HasMetadata): Boolean {
        val provider = getNamespaceProvider(resource)
        return provider != null
                && provider.add(resource)
    }

    private fun remove(resource: HasMetadata) {
        val removed = when (resource) {
            is Namespace -> removeNamespace(resource)
            else -> removeNamespaceChild(resource)
        }
        if (removed) {
            resourceChange.fireRemoved(listOf(resource))
        }
    }

    private fun removeNamespace(namespace: Namespace): Boolean {
        return namespaceProviders.remove(namespace.metadata.name) != null
    }

    private fun removeNamespaceChild(resource: HasMetadata): Boolean {
        val provider = getNamespaceProvider(resource)
        return provider != null
                && provider.remove(resource)
    }

    protected open fun createClient(): NamespacedKubernetesClient {
        return DefaultKubernetesClient(ConfigBuilder().build())
    }

    protected open fun createResourceWatch(watchableProviders: List<WatchableResourceSupplier?>): KubernetesResourceWatch {
        val watch = KubernetesResourceWatch(
            addOperation = { add(it) },
            removeOperation = { remove(it) })
        watch.addAll(watchableProviders)
        return watch
    }

    protected open fun getWatchableProviders(client: NamespacedKubernetesClient): List<() -> WatchableResource> {
        if (client == null) {
            return emptyList()
        }
        return listOf(
            { client.namespaces() as WatchableResource },
            { client.pods().inAnyNamespace() as WatchableResource })
    }
}
