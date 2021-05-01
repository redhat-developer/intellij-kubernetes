package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.util.toResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient

object CustomResourceOperatorFactory {

    fun create(jsonYaml: String, definitions: Collection<CustomResourceDefinition>, client: KubernetesClient): IResourceOperator<GenericCustomResource>? {
        val resource = createResource(jsonYaml) ?: return null
        return create(resource, definitions, client)
    }

    fun create(resource: HasMetadata, definitions: Collection<CustomResourceDefinition>, client: KubernetesClient): IResourceOperator<GenericCustomResource>? {
        val definition = getCustomResourceDefinition(resource.kind, definitions) ?: return null
        return when (definition.spec.scope) {
            "Cluster" -> NonNamespacedCustomResourceOperator(definition, client)
            "Namespaced" -> NamespacedCustomResourceOperator(definition, resource.metadata.namespace, client)
            else -> throw IllegalArgumentException(
                "Could not determine scope in spec for custom resource definition ${definition.spec.names.kind}")
        }
    }

    private fun createResource(jsonYaml: String): HasMetadata? {
        return try {
            toResource<GenericCustomResource>(jsonYaml)
        } catch(e: RuntimeException) {
            null
        }
    }

    private fun getCustomResourceDefinition(kind: String, definitions: Collection<CustomResourceDefinition>): CustomResourceDefinition? {
        return definitions
            .firstOrNull { definition -> kind == definition.spec.names.kind }
    }

}