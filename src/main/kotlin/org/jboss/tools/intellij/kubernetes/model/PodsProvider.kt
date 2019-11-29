package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

class PodsProvider(private val client: NamespacedKubernetesClient, private val namespace: Namespace): ResourceKindProvider {

    companion object {
        @JvmField val KIND = Pod::class.java;
    }

    override val kind = KIND

    override val resources: MutableList<Pod> = mutableListOf<Pod>()
        get() {
            if (field.isEmpty()) {
                val pods = client.pods().inNamespace(namespace.metadata.name).list().items
                field.addAll(pods)
            }
            return field
        }
}
