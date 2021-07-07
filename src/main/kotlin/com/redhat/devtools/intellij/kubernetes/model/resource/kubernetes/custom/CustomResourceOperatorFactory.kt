/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient

object CustomResourceOperatorFactory {

    fun create(jsonYaml: String, definitions: Collection<CustomResourceDefinition>, client: KubernetesClient): IResourceOperator<GenericCustomResource>? {
        return try {
            val resource = createResource<GenericCustomResource>(jsonYaml)
            create(resource, definitions, client)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun create(resource: HasMetadata, definitions: Collection<CustomResourceDefinition>, client: KubernetesClient): IResourceOperator<GenericCustomResource>? {
        val definition = CustomResourceDefinitionMapping.getDefinitionFor(resource, definitions) ?: return null
        return when (definition.spec.scope) {
            CustomResourceScope.CLUSTER -> NonNamespacedCustomResourceOperator(definition, client)
            CustomResourceScope.NAMESPACED -> NamespacedCustomResourceOperator(definition, resource.metadata.namespace, client)
            else -> throw IllegalArgumentException(
                "Could not determine scope in spec for custom resource definition ${definition.spec.names.kind}")
        }
    }
}