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
import org.jboss.tools.intellij.kubernetes.model.cluster.ActiveCluster
import org.jboss.tools.intellij.kubernetes.model.cluster.ClusterFactory
import org.jboss.tools.intellij.kubernetes.model.cluster.IActiveCluster
import org.jboss.tools.intellij.kubernetes.model.cluster.ICluster
import org.jboss.tools.intellij.kubernetes.model.cluster.Cluster
import org.jboss.tools.intellij.kubernetes.model.util.KubeConfigClusters

interface IResourceModel {
    val allClusters: List<ICluster>
    val currentCluster: IActiveCluster<out HasMetadata, out KubernetesClient>?
    fun getClient(): KubernetesClient?
    fun isOpenShift(): Boolean
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R>
    fun getKind(resource: HasMetadata): Class<out HasMetadata>
    fun invalidate(element: Any?)
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
}

class ResourceModel(
    private val observable: IModelChangeObservable = ModelChangeObservable(),
    private val clusterFactory: (IModelChangeObservable) -> IActiveCluster<out HasMetadata, out KubernetesClient> =
        ClusterFactory()::create
) : IResourceModel {

    private val config = KubeConfigClusters()
    override var currentCluster: IActiveCluster<out HasMetadata, out KubernetesClient>? = null
        get() {
            if (field == null) {
                field = createCurrentCluster()
            }
            return field
        }
    private val _allClusters: MutableList<ICluster> = mutableListOf()
    override val allClusters: List<ICluster>
        get() {
            if (_allClusters.isEmpty()) {
                val clusters = config.clusters.map {
                    if (config.isCurrent(it)) {
                        createCurrentCluster()
                    } else {
                        Cluster(it.cluster.server)
                    }
                }
                _allClusters.addAll(clusters)
            }
            return _allClusters
        }

    private fun createCurrentCluster(): IActiveCluster<out HasMetadata, out KubernetesClient> {
        val cluster = clusterFactory(observable)
        cluster.startWatch()
        this.currentCluster = cluster
        return cluster
    }

    private fun closeCurrentCluster() {
        currentCluster?.close()
    }

    override fun isOpenShift(): Boolean {
        return currentCluster?.isOpenShift() ?: return false
    }

    override fun getClient(): KubernetesClient? {
        return currentCluster?.client
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener);
    }

    override fun setCurrentNamespace(namespace: String) {
        currentCluster?.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return currentCluster?.getCurrentNamespace() ?: return null
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${currentCluster?.client?.masterUrl}", e)
        }
    }

    override fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R> {
        try {
            return currentCluster?.getResources(kind) ?: return emptyList()
        } catch (e: KubernetesClientException) {
            if (isNotFound(e)) {
                return emptyList()
            }
            throw ResourceException("Could not get ${kind.simpleName}s for server ${currentCluster?.client?.masterUrl}", e)
        }
    }

    override fun getKind(resource: HasMetadata): Class<out HasMetadata> {
        return resource::class.java
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is ResourceModel -> invalidate()
            is ActiveCluster<*, *> -> invalidate(element)
            is Class<*> -> invalidate(element)
            is HasMetadata -> invalidate(element)
        }
    }

    private fun invalidate() {
        closeCurrentCluster()
        createCurrentCluster()
        _allClusters.clear()
        observable.fireModified(this)
    }

    private fun invalidate(cluster: ActiveCluster<*,*>) {
        closeCurrentCluster()
        createCurrentCluster()
        observable.fireModified(cluster)
    }

    private fun invalidate(kind: Class<*>) {
        val hasMetadataClass = kind as? Class<HasMetadata> ?: return
        currentCluster?.invalidate(hasMetadataClass) ?: return
        observable.fireModified(hasMetadataClass)
    }

    private fun invalidate(resource: HasMetadata) {
        currentCluster?.invalidate(resource) ?: return
        observable.fireModified(resource)
    }

    private fun isNotFound(e: KubernetesClientException): Boolean {
        return e.code == 404
    }

}