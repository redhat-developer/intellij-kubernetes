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
    : ResourceKindProvider<HasMetadata> {

    companion object {
        val KIND = Pod::class.java;
    }

    override val kind = KIND

    override val allResources: MutableList<Pod> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                val pods = getAllPods()
                field.addAll(pods)
            }
            return field
        }

    override fun hasResource(resource: HasMetadata): Boolean {
        return allResources.contains(resource)
    }

    override fun clear(resource: HasMetadata) {
        if (resource !is Pod) {
            return
        }
        allResources.remove(resource)
        allResources.add(getPod(resource.metadata.name))
    }

    override fun clear() {
        allResources.clear()
    }

    private fun getAllPods() = client.pods().inNamespace(namespace.metadata.name).list().items

    private fun getPod(name: String) = client.pods().inNamespace(namespace.metadata.name).withName(name).get()

}
