package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import java.util.stream.Collectors

class Cluster(val client: NamespacedKubernetesClient) {

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
        val namespace = getNamespaceProvider(name)
        if (namespace == null) {
            return emptyList<Pod>()
        } else {
            return namespace.getChildren(PodsProvider.KIND);
        }
    }

    private fun getNamespaceProvider(name: String): NamespaceProvider? {
        return namespaceProviders.find { nsResource -> name == nsResource.namespace.metadata.name }
    }

}
