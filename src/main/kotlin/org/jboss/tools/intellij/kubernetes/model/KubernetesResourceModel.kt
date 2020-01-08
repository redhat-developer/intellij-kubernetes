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
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

interface IKubernetesResourceModel {
    fun getClient(): NamespacedKubernetesClient
    fun addListener(listener: ResourceChangeObservable.ResourceChangeListener)
    fun getAllNamespaces(): List<Namespace>
    fun getNamespace(name: String): Namespace?
    fun getResources(namespace: String, kind: Class<out HasMetadata>): Collection<HasMetadata>
    fun clear()
    fun clear(resource: Any?)
}

class KubernetesResourceModel(
    private val observable: IResourceChangeObservable = ResourceChangeObservable(),
    private val clusterFactory: (IResourceChangeObservable) -> ICluster = { Cluster(it) }
) : IKubernetesResourceModel {

    private var cluster = createCluster(observable, clusterFactory)

    private fun createCluster(
        observable: IResourceChangeObservable,
        clusterFactory: (IResourceChangeObservable) -> ICluster
    ): ICluster {
        val cluster = clusterFactory(observable)
        cluster.watch()
        return cluster
    }

    override fun getClient(): NamespacedKubernetesClient {
        return cluster.client
    }

    override fun addListener(listener: ResourceChangeObservable.ResourceChangeListener) {
        observable.addListener(listener);
    }

    override fun getAllNamespaces(): List<Namespace> {
        return cluster.getAllNamespaces()
    }

    override fun getNamespace(name: String): Namespace? {
        return cluster.getNamespace(name)
    }

    override fun getResources(namespace: String, kind: Class<out HasMetadata>): Collection<HasMetadata> {
        return cluster.getNamespaceProvider(namespace)?.getResources(kind) ?: emptyList()
    }

    override fun clear(resource: Any?) {
        when(resource) {
            is NamespacedKubernetesClient -> clear()
            is HasMetadata -> clear(resource)
        }
    }

    override fun clear() {
        val oldClient = cluster.client
        cluster.close()
        cluster = createCluster(observable, clusterFactory)
        observable.fireModified(oldClient)
    }

    private fun clear(resource: HasMetadata) {
        val provider = cluster.getNamespaceProvider(resource)
        if (provider != null) {
            provider.clear()
            observable.fireModified(resource)
        }
    }
}