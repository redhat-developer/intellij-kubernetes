package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import kotlin.streams.asSequence

class Cluster(val client: DefaultKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())) {

    private var namespaceProviders: MutableList<NamespaceProvider> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field.addAll(
                    loadAllNameSpaces()
                        .map { namespace: Namespace -> NamespaceProvider(client, namespace) })
            }
            return field
        }

    private fun loadAllNameSpaces(): Sequence<Namespace> {
        return client.namespaces().list().items.stream().asSequence()
    }

    fun getAllNamespaces(): List<Namespace> {
        return namespaceProviders.map { provider -> provider.namespace }
    }

    fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders.find { nsResource -> name == nsResource.namespace.metadata.name }
    }
}
