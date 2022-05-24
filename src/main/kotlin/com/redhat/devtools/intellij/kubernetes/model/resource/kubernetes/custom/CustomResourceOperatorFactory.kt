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
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.model.Scope

object CustomResourceOperatorFactory {

    fun create(jsonYaml: String, definition: CustomResourceDefinition?, client: KubernetesClient): IResourceOperator<GenericKubernetesResource>? {
        if (definition == null) {
            return null
        }
        val resource = createResource<GenericKubernetesResource>(jsonYaml)
        return create(resource, definition, client)
    }

    fun create(resource: HasMetadata, definition: CustomResourceDefinition?, client: KubernetesClient): IResourceOperator<GenericKubernetesResource>? {
        if (definition == null) {
            return null
        }
        val context = CustomResourceDefinitionContextFactory.create(definition)
        return create(resource, context, client)
    }

    fun create(resource: HasMetadata, context: CustomResourceDefinitionContext, client: KubernetesClient): IResourceOperator<GenericKubernetesResource>? {
        val kind = ResourceKind.create(context) ?: return null
        return when (context.scope) {
            Scope.CLUSTER.value() ->
                NonNamespacedCustomResourceOperator(kind, context, client)
            Scope.NAMESPACED.value() -> {
                val namespace = resource.metadata.namespace ?: client.namespace
                NamespacedCustomResourceOperator(kind, context, namespace, client)
            }
            else ->
                throw IllegalArgumentException(
                    "Could not determine scope in spec for custom resource definition ${context.kind}")
        }
    }

}