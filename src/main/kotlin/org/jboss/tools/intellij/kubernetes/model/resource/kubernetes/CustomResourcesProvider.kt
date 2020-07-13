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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import java.util.stream.Collectors

class CustomResourcesProvider(
        private val definition: CustomResourceDefinition,
        namespace: String?,
        client: KubernetesClient)
    : NamespacedResourcesProvider<GenericCustomResource, KubernetesClient>(namespace, client) {

    override val kind = ResourceKind.new(definition)

    override fun loadAllResources(namespace: String): List<GenericCustomResource> {
        val context = CustomResourceDefinitionContext.fromCrd(definition)
        val list = client.customResource(context).list(namespace)
        return createItems(list["items"] as? List<Map<String, String>> ?: emptyList())
    }

    override fun getWatchable(): () -> Watchable<Watch, Watcher<GenericCustomResource>>? {
        return { null }
    }

    private fun createItems(items: List<Map<String, String>>): List<GenericCustomResource> {
        return items.stream()
                .map { createItem(it) }
                .collect(Collectors.toList())
    }

    private fun createItem(item: Map<String, String>): GenericCustomResource {
        val resource = GenericCustomResource()
        resource.apiVersion = item["apiVersion"]
        resource.kind = item["kind"]
        resource.metadata = createObjectMetadata(item["metadata"] as? Map<String, Any> ?: emptyMap())
        resource.spec = GenericCustomResourceSpec(item["spec"] as? Map<String, Any> ?: emptyMap())
        return resource
    }

    private fun createObjectMetadata(metadata: Map<String, Any>): ObjectMeta {
        return ObjectMetaBuilder()
                .withCreationTimestamp(metadata["creationTimestamp"] as? String?)
                .withGeneration(metadata["generation"] as? Long?)
                .withName(metadata["name"] as? String?)
                .withNamespace(metadata["namespace"] as? String?)
                .withResourceVersion(metadata["resourceVersion"] as? String?)
                .withSelfLink(metadata["selfLink"] as? String?)
                .withUid(metadata["uid"] as? String?)
                .build()
    }
}
