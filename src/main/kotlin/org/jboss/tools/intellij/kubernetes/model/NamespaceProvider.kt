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

class NamespaceProvider(
    client: NamespacedKubernetesClient,
    val namespace: Namespace,
    private val kindProviders: Map<Class<out HasMetadata>, IResourceKindProvider<out HasMetadata>> =
        mapOf(Pair(PodsProvider.KIND, PodsProvider(client, namespace)))) {

    fun <T: HasMetadata> getResources(kind: Class<T>): Collection<T> {
        val allResources = kindProviders[kind]?.getAllResources()
        return allResources as? Collection<T> ?: emptyList()
    }

    fun <T: HasMetadata> clear(kind: Class<T>) {
        kindProviders[kind]?.clear()
    }

    fun clear() {
        kindProviders.forEach{ it.value.clear() }
    }

    fun add(resource: HasMetadata): Boolean {
        val provider = kindProviders[resource::class.java]
        var added = false
        if (provider != null) {
            added = provider.add(resource);
        }
        return added
    }

    fun remove(resource: HasMetadata): Boolean {
        var removed = false
        val provider = kindProviders[resource::class.java]
        if (provider != null) {
            removed = provider.remove(resource);
        }
        return removed
    }
}
