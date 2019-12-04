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

class NamespaceProvider(private val client: NamespacedKubernetesClient, val namespace: HasMetadata) {

    private val children: List<ResourceKindProvider> = mutableListOf(PodsProvider(client, namespace))

    fun getName(): String {
        return namespace.metadata.name
    }

    fun getPods(): List<Pod> {
        return getChildren(PodsProvider.KIND)
    }

    private fun <T> getChildren(kind: Class<T>): List<T> {
        val provider: ResourceKindProvider? = children.find { provider -> kind == provider.kind }
        return (provider?.resources as? List<T>) ?: emptyList()
    }
}
