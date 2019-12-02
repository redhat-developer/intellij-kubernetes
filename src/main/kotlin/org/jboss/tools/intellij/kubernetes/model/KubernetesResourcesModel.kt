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

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

object KubernetesResourcesModel {

    interface ResourcesChangedListener {
        fun removed(removed: List<Any>)
        fun added(removed: List<Any>)
        fun modified(removed: List<Any>)
    }

    private var cluster = createCluster()
    private var listeners = mutableListOf<ResourcesChangedListener>()

    private fun createCluster(): Cluster {
        return Cluster(DefaultKubernetesClient(ConfigBuilder().build()))
    }

    fun getClient(): NamespacedKubernetesClient {
        return cluster.client
    }

    fun getNamespaces(): List<Namespace> {
        return cluster.getNamespaces();
    }

    fun getPods(namespace: String): List<Pod> {
        return cluster.getPods(namespace)
    }

    fun addListener(listener: ResourcesChangedListener) {
        listeners.add(listener)
    }

    private fun fireRemoved(removed: List<Any>) {
        listeners.forEach{listener -> listener.removed(removed)}
    }

    private fun fireAdded(added: List<Any>) {
        listeners.forEach{listener -> listener.added(added)}
    }

    fun refresh() {
        val oldClient = cluster.client
        cluster = createCluster()
        fireRemoved(listOf(oldClient))
    }
}