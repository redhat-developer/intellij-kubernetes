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
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.client.OpenShiftClient

class Cluster(val client: DefaultKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())) {

    private val namespaceProviders: MutableList<NamespaceProvider> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field.addAll(
                    loadAllNameSpaces()
                        .map { namespace: HasMetadata -> NamespaceProvider(client, namespace) })
            }
            return field
        }

    fun getAllNamespaces(): List<HasMetadata> {
        return namespaceProviders.map { provider -> provider.namespace }
    }

    fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders.find { provider -> name == provider.namespace.metadata.name }
    }

    fun clearNamespaceProvider(resource: HasMetadata) {
        namespaceProviders.find { it.hasResource(resource) }
            ?.clear(resource)
    }

    private fun loadAllNameSpaces(): Sequence<HasMetadata> {
        var namespaces: List<HasMetadata> = emptyList()
        try {
            namespaces = loadKubernetesNamespaces()
        } catch(e: KubernetesClientException) {
            namespaces = loadOpenShiftProjects()
        }
        return namespaces.asSequence()
    }

    private fun loadKubernetesNamespaces() =
        client.namespaces().list().items

    private fun loadOpenShiftProjects() =
        client.adapt(OpenShiftClient::class.java).projects().list().items

}
