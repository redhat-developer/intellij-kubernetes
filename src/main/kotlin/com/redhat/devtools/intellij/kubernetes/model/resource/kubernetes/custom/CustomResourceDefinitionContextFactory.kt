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

import com.redhat.devtools.intellij.kubernetes.model.util.getHighestPriorityVersion
import io.fabric8.kubernetes.api.model.APIResource
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.model.Scope

object CustomResourceDefinitionContextFactory {

    /**
     * Returns a [CustomResourceDefinitionContext] for the given [CustomResourceDefinition].
     * The version with the highest priority among the available ones is used.
     *
     * @param definition [CustomResourceDefinition] to create the context for
     *
     * @return the [CustomResourceDefinitionContext] for the given [CustomResourceDefinition]
     *
     * @see [getHighestPriorityVersion]
     */
    fun create(definition: CustomResourceDefinition): CustomResourceDefinitionContext {
        return CustomResourceDefinitionContext.Builder()
            .withGroup(definition.spec.group)
            .withVersion(getHighestPriorityVersion(definition.spec)) // use version with highest priority
            .withScope(definition.spec.scope)
            .withName(definition.metadata.name)
            .withPlural(definition.spec.names.plural)
            .withKind(definition.spec.names.kind)
            .build()
    }

    /**
     * Returns a [CustomResourceDefinitionContext] for the given kind, group, version and list of available [APIResource].
     * [APIResource
     * The swagger schema is used to populate name, plural, kind, etc. in the context.
     *
     * @param kind to create the context for
     * @param client the kubernetes client to retrieve the swagger schema
     *
     * @return the [CustomResourceDefinitionContext] for the given kind and client
     *
     * @see [getHighestPriorityVersion]
     */
    fun create(
        kind: String,
        group: String,
        version: String,
        apiResource: APIResource?
    ): CustomResourceDefinitionContext? {
        if (apiResource == null) {
            return null
        }
        val scope = getScope(apiResource)
        val plural = getPlural(apiResource) ?: return null
        val name = getName(plural, group)
        return createContext(kind, group, version, name, plural, scope)
    }

    private fun getScope(apiResource: APIResource): String {
        return if (apiResource.namespaced) {
            Scope.NAMESPACED.value()
        } else {
            Scope.CLUSTER.value()
        }
    }

    private fun getPlural(apiResource: APIResource): String? {
        return apiResource.name
    }

    private fun getName(plural: String, group: String): String {
        return "$plural.$group"
    }

    private fun createContext(
        kind: String,
        group: String,
        version: String,
        name: String,
        plural: String,
        scope: String
    ): CustomResourceDefinitionContext {
        return CustomResourceDefinitionContext.Builder()
            .withKind(kind)
            .withGroup(group)
            .withVersion(version)
            .withName(name)
            .withPlural(plural)
            .withScope(scope)
            .build()
    }

}
