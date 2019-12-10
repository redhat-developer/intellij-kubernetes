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
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient

class Cluster(val client: DefaultKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())) {

    private val namespaceProviders: MutableMap<String, NamespaceProvider> = mutableMapOf()
        get() {
            if (field.isEmpty()) {
                val namespaceProviders = loadAllNameSpaces()
                    .map { Pair(it.metadata.name, NamespaceProvider(client, it)) }
                field.putAll(namespaceProviders)
            }
            return field
        }

    fun getAllNamespaces(): List<Namespace> {
        return namespaceProviders.entries.map { it.value.namespace }
    }

    fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders[name]
    }

    fun getNamespaceProvider(namespace: Namespace): NamespaceProvider? {
        return getNamespaceProvider(namespace.metadata.name)
    }

    fun getNamespaceProvider(resource: HasMetadata): NamespaceProvider? {
        return getNamespaceProvider(resource.metadata.namespace)
    }

    fun add(namespace: Namespace): Boolean {
        val provider = NamespaceProvider(client, namespace)
        return namespaceProviders.putIfAbsent(namespace.metadata.name, provider) == null
    }

    fun remove(namespace: Namespace): Boolean {
        return namespaceProviders.remove(namespace.metadata.name) != null
    }

    private fun loadAllNameSpaces(): Sequence<Namespace> {
        return  client.namespaces().list().items.asSequence()
    }

    private fun getNamespace(name: String?): Namespace? {
        return namespaceProviders[name]
            ?.namespace
    }

}
