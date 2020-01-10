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

class PodsProvider(private val client: NamespacedKubernetesClient, private val namespace: Namespace)
    : IResourceKindProvider<Pod> {

    companion object {
        val KIND = Pod::class.java;
    }

    override val kind = KIND

    private val allResources: MutableSet<Pod> = mutableSetOf()
        get() {
            if (field.isEmpty()) {
                val pods = getAllPods()
                field.addAll(pods)
            }
            return field
        }

    override fun getAllResources(): Collection<Pod> {
        return allResources.toList()
    }

    override fun hasResource(resource: HasMetadata): Boolean {
        return allResources.contains(resource)
    }

    override fun invalidate() {
        allResources.clear()
    }

    override fun add(resource: HasMetadata): Boolean {
        if (resource !is Pod) {
            return false
        }
        return allResources.add(resource)
    }

    override fun remove(resource: HasMetadata): Boolean {
        if (resource !is Pod) {
            return false
        }
        return allResources.removeIf { resource.metadata.name == it.metadata.name }
    }

    private fun getAllPods(): List<Pod> {
        return client.inNamespace(namespace.metadata.name).pods().list().items
    }

}
