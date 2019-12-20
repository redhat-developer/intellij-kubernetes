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

class NamespaceProvider(private val client: NamespacedKubernetesClient, val namespace: Namespace) {

    private val kindProviders: MutableMap<Class<out HasMetadata>, IResourceKindProvider<out HasMetadata>> = mutableMapOf(
        Pair(PodsProvider.KIND, PodsProvider(client, namespace))
    )

    fun getAllResources(): Collection<HasMetadata>? {
        return kindProviders.values.flatMap { it.allResources }
    }

    fun <T: HasMetadata> getResources(kind: Class<T>): Collection<T> {
        val provider = kindProviders[kind]
        var allResources: Collection<T> = emptyList()
        if (provider?.allResources is Collection<*>) {
            allResources = provider.allResources as Collection<T>
        }
        return allResources
    }

    fun clear(kind: Class<HasMetadata>) {
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
