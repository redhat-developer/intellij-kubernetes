package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

class NamespaceProvider(private val client: NamespacedKubernetesClient, val namespace: Namespace) {

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
