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

object KubernetesResourceModel {

    private val watch = createWatch(
        { add(it) },
        { remove(it) }
    )

    private var cluster = createCluster(createClient())
    private val observable = ResourceChangedObservableImpl()

    fun getClient(): NamespacedKubernetesClient {
        return cluster.client
    }

    private fun createCluster(client: NamespacedKubernetesClient): Cluster {
        val cluster = Cluster(client)
        watch.start(client)
        return cluster
    }

    private fun createWatch(onAdded: (HasMetadata) -> Unit, onRemoved: (HasMetadata) -> Unit): KubernetesResourceWatch {
        return KubernetesResourceWatch(onAdded, onRemoved)
    }

    private fun createClient(): NamespacedKubernetesClient {
        return DefaultKubernetesClient(ConfigBuilder().build())
    }

    fun addListener(listener: ResourceChangedObservableImpl.ResourceChangeListener) {
        observable.addListener(listener);
    }

    fun getAllNamespaces(): List<Namespace> {
        return cluster.getAllNamespaces()
    }

    fun getNamespace(name: String): Namespace? {
        return cluster.getNamespaceProvider(name)?.namespace
    }

    fun getAllResources(namespace: String): Collection<HasMetadata> {
        return cluster.getNamespaceProvider(namespace)?.getAllResources() ?: emptyList()
    }

    fun getResources(namespace: String, kind: Class<out HasMetadata>): Collection<HasMetadata> {
        return cluster.getNamespaceProvider(namespace)?.getResources(kind) ?: emptyList()
    }

    fun refresh(resource: Any?) {
        when(resource) {
            is NamespacedKubernetesClient -> refresh()
            is Namespace -> refresh(resource)
            is HasMetadata -> refresh(resource)
        }
    }

    private fun refresh() {
        val oldClient = cluster.client
        oldClient.close()
        cluster = createCluster(createClient())
        observable.fireModified(listOf(oldClient))
    }

    private fun refresh(resource: Namespace) {
        val provider = cluster.getNamespaceProvider(resource)
        if (provider != null) {
            provider.clear()
            observable.fireModified(listOf(resource))
        }
    }

    fun add(resource: HasMetadata) {
        val added = when(resource) {
            is Namespace -> addNamespace(resource)
            else -> addNamespaceChild(resource)
        }
        if (added) {
            observable.fireAdded(listOf(resource))
        }
    }

    private fun addNamespace(namespace: Namespace): Boolean {
        return cluster.add(namespace)
    }

    private fun addNamespaceChild(resource: HasMetadata): Boolean {
        val provider = cluster.getNamespaceProvider(resource)
        return provider != null
            && provider.add(resource)
    }

    fun remove(resource: HasMetadata) {
        val removed = when (resource) {
            is Namespace -> removeNamespace(resource)
            else -> removeNamespaceChild(resource)
        }
        if (removed) {
            observable.fireRemoved(listOf(resource))
        }
    }

    private fun removeNamespace(namespace: Namespace): Boolean {
        return cluster.remove(namespace)
    }

    private fun removeNamespaceChild(resource: HasMetadata): Boolean {
        val provider = cluster.getNamespaceProvider(resource)
        return provider != null
            && provider.remove(resource)
    }
}