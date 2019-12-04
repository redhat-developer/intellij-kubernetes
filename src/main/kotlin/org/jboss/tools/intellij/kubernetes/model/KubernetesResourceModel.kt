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
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

object KubernetesResourceModel {

    private var cluster = createCluster()
    private val observable = ResourceChangedObservableImpl();

    private fun createCluster(): Cluster {
        return Cluster()
    }

    fun addListener(listener: ResourceChangedObservableImpl.ResourcesChangedListener) {
        observable.addListener(listener);
    }

    fun getCluster(): NamespacedKubernetesClient {
        return cluster.client
    }

    fun getNamespaces(): List<HasMetadata> {
        return cluster.getAllNamespaces();
    }

    fun getPods(namespace: String): List<Pod> {
        return cluster.getNamespaceProvider(namespace)?.getPods() ?: emptyList()
    }

    fun refresh(resource: Any?) {
        when(resource) {
            is NamespacedKubernetesClient -> refreshRoot()
            is HasMetadata -> refreshResource(resource)
        }
    }

    private fun refreshRoot() {
        cluster = createCluster()
        observable.fireModified(listOf(cluster.client))
    }

    private fun refreshResource(resource: HasMetadata) {
        cluster.clearNamespaceProvider(resource)
        observable.fireModified(listOf(resource))
    }
}