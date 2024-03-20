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

import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext

class NonNamespacedCustomResourceOperator(
    override val kind: ResourceKind<GenericKubernetesResource>,
    private val context: CustomResourceDefinitionContext,
    client: KubernetesClient
) : NonNamespacedResourceOperator<GenericKubernetesResource, KubernetesClient>(client) {

    override fun loadAllResources(): List<GenericKubernetesResource> {
        return getOperation()
            ?.list()
            ?.items
            ?: emptyList()
    }

    override fun watchAll(watcher: Watcher<in GenericKubernetesResource>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<GenericKubernetesResource> ?: return null
        return getOperation()
            ?.watch(typedWatcher)
    }

    override fun watch(resource: HasMetadata, watcher: Watcher<in GenericKubernetesResource>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<GenericKubernetesResource> ?: return null
        return getOperation()
            ?.withName(resource.metadata.name)
            ?.watch(typedWatcher)
    }

    override fun delete(resources: List<HasMetadata>, force: Boolean): Boolean {
        @Suppress("UNCHECKED_CAST")
        val toDelete = resources as? List<GenericKubernetesResource> ?: return false
        return toDelete.stream()
            .map { delete(it.metadata.name, force) }
            .reduce(false ) { thisDelete, thatDelete -> thisDelete || thatDelete }
    }

    private fun delete(name: String, force: Boolean): Boolean {
        val operation = getOperation()
            ?.withName(name) ?: return false
      	operation
          .immediate(force)
          ?.delete()
        return true
    }

    override fun replace(resource: HasMetadata): HasMetadata? {
        val toReplace = resource as? GenericKubernetesResource ?: return null

        return runWithoutServerSetProperties(toReplace) {
            getOperation()
                ?.resource(resource)
                /**
                 * See: https://github.com/fabric8io/kubernetes-client/blob/main/doc/FAQ.md#alternatives-to-createOrReplace-and-replace
                 */
                ?.patch()
        }
    }

    override fun create(resource: HasMetadata): HasMetadata? {
        return replace(resource)
    }

    override fun get(resource: HasMetadata): HasMetadata? {
        return getOperation()?.withName(resource.metadata.name)?.get()
    }

    override fun getOperation(): NonNamespacedOperation<GenericKubernetesResource>? {
        return client.genericKubernetesResources(context)
    }
}
