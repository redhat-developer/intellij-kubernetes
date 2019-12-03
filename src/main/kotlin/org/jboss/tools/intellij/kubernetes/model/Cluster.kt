package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import java.util.stream.Collectors

class Cluster(val client: DefaultKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())) {

    private var namespaceProviders: MutableList<NamespaceProvider> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field.addAll(client.namespaces().list().items.stream()
                    .map { namespace: Namespace -> NamespaceProvider(client, namespace) }
                    .collect(Collectors.toList<NamespaceProvider>()))
            }
            return field
        }

    fun getNamespaces(): List<Namespace> {
        return namespaceProviders.map { nsResource -> nsResource.namespace }
    }

    fun getPods(name: String): List<Pod> {
        val provider = getNamespaceProvider(name)
        return provider?.getChildren(PodsProvider.KIND) ?: emptyList()
    }

    private fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders.find { nsResource -> name == nsResource.namespace.metadata.name }
    }

}
