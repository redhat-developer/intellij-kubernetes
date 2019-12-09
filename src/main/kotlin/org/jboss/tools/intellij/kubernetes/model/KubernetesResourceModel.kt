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
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import java.util.function.Consumer

object KubernetesResourceModel {

    private val watch = KubernetesResourceWatch(
        ResourceAdded(),
        ResourceRemoved())

    private var cluster = createCluster()
    private val observable = ResourceChangedObservableImpl()

    private fun createCluster(): Cluster {
        val cluster = Cluster()
        watch.start(cluster.client)
        return cluster;
    }

    fun addListener(listener: ResourceChangedObservableImpl.ResourceChangeListener) {
        observable.addListener(listener);
    }

    fun getClient(): NamespacedKubernetesClient {
        return cluster.client
    }

    fun getAllNamespaces(): List<Namespace> {
        return cluster.getAllNamespaces()
    }

    fun getPods(namespace: String): List<Pod> {
        return cluster.getNamespaceProvider(namespace)?.getPods() ?: emptyList()
    }

    fun refresh(resource: Any?) {
        when(resource) {
            is NamespacedKubernetesClient -> refresh()
            is Namespace -> refresh(resource)
            is HasMetadata -> refresh(resource)
        }
    }

    private fun refresh() {
        cluster.client.close()
        cluster = createCluster()
        observable.fireModified(listOf(cluster.client))
    }

    private fun refresh(resource: Namespace) {
        cluster.clearNamespaceProvider(resource)
        observable.fireModified(listOf(resource))
    }

    fun add(resource: HasMetadata) {
        when(resource) {
            is Namespace -> add(resource)
        }
    }

    private fun add(namespace: Namespace) {
        if (cluster.add(namespace)) {
            observable.fireModified(listOf(cluster.client))
        }
    }

    fun remove(resource: HasMetadata) {
        when(resource) {
            is Namespace -> remove(resource)
        }
    }

    private fun remove(namespace: Namespace) {
        if (cluster.remove(namespace)) {
            observable.fireModified(listOf(cluster.client))
        }
    }

    class ResourceAdded: Consumer<HasMetadata> {
        override fun accept(resource: HasMetadata) {
            add(resource)
        }
    }

    class ResourceRemoved: Consumer<HasMetadata> {
        override fun accept(resource: HasMetadata) {
            remove(resource)
        }
    }

}