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
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

interface IKubernetesResourceModel {
    fun getClient(): NamespacedKubernetesClient
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
    fun setCurrentNamespace(namespace: Namespace)
    fun getCurrentNamespace(): Namespace?
    fun getAllNamespaces(): List<Namespace>
    fun getNamespace(name: String): Namespace?
    /**
     * Returns all resources for the current cluster and current namespace.
     * Returns an empty collection if none are present.
     */
    fun getResources(kind: Class<out HasMetadata>): Collection<HasMetadata>
    fun invalidate()
    fun invalidate(resource: Any?)
}

class KubernetesResourceModel(
    private val observable: IModelChangeObservable = ModelChangeObservable(),
    private val clusterFactory: (IModelChangeObservable) -> ICluster = { Cluster(it) }
) : IKubernetesResourceModel {

    private var cluster = createCluster(observable, clusterFactory)

    private fun createCluster(
        observable: IModelChangeObservable,
        clusterFactory: (IModelChangeObservable) -> ICluster
    ): ICluster {
        val cluster = clusterFactory(observable)
        cluster.startWatch()
        return cluster
    }

    override fun getClient(): NamespacedKubernetesClient {
        return cluster.client
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener);
    }

    override fun getAllNamespaces(): List<Namespace> {
        try {
            return cluster.getAllNamespaces()
        } catch (e: KubernetesClientException) {
            throw KubernetesResourceException("Could not get all namespaces for server ${cluster.client.masterUrl}", e)
        }
    }

    override fun setCurrentNamespace(namespace: Namespace) {
        cluster.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): Namespace? {
        try {
            return cluster.getCurrentNamespace()
        } catch (e: KubernetesClientException) {
            throw KubernetesResourceException("Could not get current namespace for server ${cluster.client.masterUrl}",
                e)
        }
    }

        override fun getNamespace(name: String): Namespace? {
        return cluster.getNamespace(name)
    }

    override fun getResources(kind: Class<out HasMetadata>): Collection<HasMetadata> {
        try {
            val namespace = cluster.getCurrentNamespace()
            return cluster.getNamespaceProvider(namespace?.metadata?.name)?.getResources(kind) ?: emptyList()
        } catch (e: KubernetesClientException) {
            throw KubernetesResourceException("Could not get ${kind.simpleName}s for server ${cluster.client.masterUrl}", e)
        }
    }

    override fun invalidate(resource: Any?) {
        when(resource) {
            is NamespacedKubernetesClient -> invalidate()
            is HasMetadata -> invalidate(resource)
        }
    }

    override fun invalidate() {
        val oldClient = cluster.client
        cluster.close()
        cluster = createCluster(observable, clusterFactory)
        observable.fireModified(oldClient)
    }

    private fun invalidate(resource: HasMetadata) {
        val provider = cluster.getNamespaceProvider(resource)
        if (provider != null) {
            provider.invalidate()
            observable.fireModified(resource)
        }
    }
}