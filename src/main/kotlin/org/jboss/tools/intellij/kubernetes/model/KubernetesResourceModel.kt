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
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

interface IKubernetesResourceModel {
    fun getClient(): NamespacedKubernetesClient
    fun addListener(listener: ResourceChangedObservableImpl.ResourceChangeListener)
    fun getAllNamespaces(): List<Namespace>
    fun getNamespace(name: String): Namespace?
    fun getAllResources(namespace: String): Collection<HasMetadata>
    fun getResources(namespace: String, kind: Class<out HasMetadata>): Collection<HasMetadata>
    fun refresh()
    fun refresh(resource: Any?)
}

class KubernetesResourceModel(
        private val clusterFactory: (ResourceChangeObservable) -> Cluster = { observable -> Cluster(observable) })
    : IKubernetesResourceModel {

    private val observable = ResourceChangedObservableImpl()
    private var cluster = createCluster(observable, clusterFactory)

    private fun createCluster(observable: ResourceChangeObservable, clusterFactory: (ResourceChangeObservable) -> Cluster): Cluster {
        val cluster = clusterFactory(observable)
        cluster.watch()
        return cluster
    }

    override fun getClient(): NamespacedKubernetesClient {
        return cluster.client
    }

    override fun addListener(listener: ResourceChangedObservableImpl.ResourceChangeListener) {
        observable.addListener(listener);
    }

    override fun getAllNamespaces(): List<Namespace> {
        return cluster.getAllNamespaces()
    }

    override fun getNamespace(name: String): Namespace? {
        return cluster.getNamespaceProvider(name)?.namespace
    }

    override fun getAllResources(namespace: String): Collection<HasMetadata> {
        return cluster.getNamespaceProvider(namespace)?.getAllResources() ?: emptyList()
    }

    override fun getResources(namespace: String, kind: Class<out HasMetadata>): Collection<HasMetadata> {
        return cluster.getNamespaceProvider(namespace)?.getResources(kind) ?: emptyList()
    }

    override fun refresh(resource: Any?) {
        when(resource) {
            is NamespacedKubernetesClient -> refresh()
            is Namespace -> refresh(resource)
            is HasMetadata -> refresh(resource as Namespace)
        }
    }

    override fun refresh() {
        val oldClient = cluster.client
        oldClient.close()
        cluster = clusterFactory(observable)
        observable.fireModified(listOf(oldClient))
    }

    private fun refresh(resource: Namespace) {
        val provider = cluster.getNamespaceProvider(resource)
        if (provider != null) {
            provider.clear()
            observable.fireModified(listOf(resource))
        }
    }
}