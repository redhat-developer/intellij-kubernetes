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

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.model.cluster.ClusterFactory
import org.jboss.tools.intellij.kubernetes.model.cluster.ICluster

interface IResourceModel {
    fun getClient(): KubernetesClient?
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R>
    fun getKind(resource: HasMetadata): Class<out HasMetadata>
    fun invalidate(element: Any?)
}

class ResourceModel(
    private val observable: IModelChangeObservable = ModelChangeObservable(),
    private val clusterFactory: (IModelChangeObservable) -> ICluster<out HasMetadata, out KubernetesClient> =
        ClusterFactory()::create
) : IResourceModel {

    private var cluster: ICluster<out HasMetadata, out KubernetesClient>? = null
        get() {
            if (field == null) {
                field = createCluster(observable, clusterFactory)
            }
            return field
        }

    private fun createCluster(
        observable: IModelChangeObservable,
        clusterFactory: (IModelChangeObservable) -> ICluster<out HasMetadata, out KubernetesClient>
    ): ICluster<out HasMetadata, out KubernetesClient> {
        val cluster = clusterFactory(observable)
        cluster.startWatch()
        return cluster
    }

    override fun getClient(): KubernetesClient? {
        return cluster?.client
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener);
    }

    override fun setCurrentNamespace(namespace: String) {
        cluster?.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return cluster?.getCurrentNamespace() ?: return null
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${cluster?.client?.masterUrl}", e)
        }
    }

    override fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R> {
        try {
            return cluster?.getResources(kind) ?: return emptyList()
        } catch (e: KubernetesClientException) {
            throw ResourceException("Could not get ${kind.simpleName}s for server ${cluster?.client?.masterUrl}", e)
        }
    }

    override fun getKind(resource: HasMetadata): Class<out HasMetadata> {
        return resource::class.java
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is KubernetesClient -> invalidate(element)
            is HasMetadata -> invalidate(element)
        }
    }

    private fun invalidate(client: KubernetesClient) {
        cluster?.close()
        cluster = createCluster(observable, clusterFactory)
        observable.fireModified(client)
    }

    private fun invalidate(resource: HasMetadata) {
        cluster?.invalidate(resource)
        observable.fireModified(resource)
    }
}