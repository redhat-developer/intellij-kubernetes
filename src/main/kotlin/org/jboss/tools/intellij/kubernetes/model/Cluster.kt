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

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.*
import io.fabric8.kubernetes.client.ConfigBuilder

open class Cluster(private val resourceChange: ResourceChangeObservable) {

    val client = createClient()

    private val namespaceProviders: MutableMap<String, NamespaceProvider> = mutableMapOf()
        get() {
            if (field.isEmpty()) {
                val namespaceProviders = loadAllNamespaces()
                    .map { Pair(it.metadata.name, NamespaceProvider(client, it)) }
                field.putAll(namespaceProviders)
            }
            return field
        }

    fun watch() {
        createResourceWatch(getWatchableProviders(client))
    }

    fun getAllNamespaces(): List<Namespace> {
        return namespaceProviders.entries.map { it.value.namespace }
    }

    internal fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders[name]
    }

    internal fun getNamespaceProvider(namespace: Namespace): NamespaceProvider? {
        return getNamespaceProvider(namespace.metadata.name)
    }

    internal fun getNamespaceProvider(resource: HasMetadata): NamespaceProvider? {
        return getNamespaceProvider(resource.metadata.namespace)
    }

    private fun loadAllNamespaces(): Sequence<Namespace> {
        return  client.namespaces().list().items.asSequence()
    }

    fun add(resource: HasMetadata) {
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
        return listOf(
            { client.namespaces() as WatchableResource },
            { client.pods().inAnyNamespace() as WatchableResource })
    }

}
