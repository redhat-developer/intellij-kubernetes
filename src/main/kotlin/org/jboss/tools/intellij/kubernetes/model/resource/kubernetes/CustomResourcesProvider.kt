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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.ListOptionsBuilder
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import java.io.IOException
import java.util.stream.Collectors


class CustomResourcesProvider(
        definition: CustomResourceDefinition,
        namespace: String?,
        client: KubernetesClient)
    : NamespacedResourcesProvider<GenericCustomResource, KubernetesClient>(namespace, client) {

    private companion object Constants {
        const val API_VERSION = "apiVersion"
        const val KIND = "kind"
        const val METADATA = "metadata"
        const val SPEC = "spec"
        const val CREATION_TIMESTAMP = "creationTimestamp"
        const val GENERATION = "generation"
        const val NAME = "name"
        const val NAMESPACE = "namespace"
        const val RESOURCE_VERSION = "resourceVersion"
        const val SELF_LINK = "selfLink"
        const val UID = "uid"
    }

    override val kind = ResourceKind.new(definition.spec)
    private val context: CustomResourceDefinitionContext = CustomResourceDefinitionContext.fromCrd(definition)

    override fun loadAllResources(namespace: String): List<GenericCustomResource> {
        val resourcesList = client.customResource(context).list(namespace)
        val items = resourcesList["items"] as? List<Map<String, String>> ?: return emptyList()
        return createGenericCustomResources(items)
    }

    override fun getWatchable(): () -> Watchable<Watch, Watcher<GenericCustomResource>>? {
        if (namespace == null) {
            return { null }
        }
        return { GenericCustomResourceWatchable(client.customResource(context)) }
    }

    private fun createGenericCustomResources(items: List<Map<String, String>>): List<GenericCustomResource> {
        return items.stream()
                .map { createGenericCustomResource(it) }
                .collect(Collectors.toList())
    }

    private fun createGenericCustomResource(item: Map<String, Any?>): GenericCustomResource {
        return GenericCustomResource(
                item[API_VERSION] as? String,
                item[KIND] as? String,
                createObjectMetadata(item[METADATA] as? Map<String, Any>),
                GenericCustomResourceSpec(item[SPEC] as? Map<String, Any>))
    }

    private fun createObjectMetadata(metadata: Map<String, Any>?): ObjectMeta {
        if (metadata == null) {
            return ObjectMetaBuilder().build()
        }
        return ObjectMetaBuilder()
                .withCreationTimestamp(metadata[CREATION_TIMESTAMP] as? String?)
                .withGeneration(metadata[GENERATION] as? Long?)
                .withName(metadata[NAME] as? String?)
                .withNamespace(metadata[NAMESPACE] as? String?)
                .withResourceVersion(metadata[RESOURCE_VERSION] as? String?)
                .withSelfLink(metadata[SELF_LINK] as? String?)
                .withUid(metadata[UID] as? String?)
                .build()
    }

    private inner class GenericCustomResourceWatchable(private val watchable: RawCustomResourceOperationsImpl)
        : Watchable<Watch, Watcher<GenericCustomResource>> {

        override fun watch(watcher: Watcher<GenericCustomResource>): Watch? {
            return watch(ListOptionsBuilder().build(), watcher)
        }

        override fun watch(resourceVersion: String?, watcher: Watcher<GenericCustomResource>): Watch? {
            return watch(ListOptionsBuilder().withResourceVersion(resourceVersion).build(), watcher)
        }

        override fun watch(options: ListOptions, watcher: Watcher<GenericCustomResource>): Watch? {
            if (namespace == null) {
                return null
            }
            return watchable.watch(namespace,null,null, options, DelegatingWatcher(watcher))
        }
    }

    /**
     * Watcher that delegates events to a given target watcher.
     * This watcher receives resources in an event as json strings.
     * It then deserializes those json string to GenericCustomResource(s)
     */
    private class DelegatingWatcher(private val target: Watcher<GenericCustomResource>): Watcher<String> {

        override fun eventReceived(action: Watcher.Action, resource: String) {
            val customResource = createGenericCustomResource(resource)
            target.eventReceived(action, customResource)
        }

        private fun createGenericCustomResource(json: String): GenericCustomResource {
            val mapper = ObjectMapper()
            val module = SimpleModule()
            module.addDeserializer(GenericCustomResource::class.java, GenericCustomResourceDeserializer())
            mapper.registerModule(module)

            return mapper.readValue(json, GenericCustomResource::class.java)
        }

        override fun onClose(exception: KubernetesClientException?) {
        }

        inner class GenericCustomResourceDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<GenericCustomResource?>(vc) {
            @Throws(IOException::class, JsonProcessingException::class)

            override fun deserialize(parser: JsonParser, ctx: DeserializationContext?): GenericCustomResource {
                val node: JsonNode = parser.codec.readTree(parser)
                return GenericCustomResource(
                        node.get(API_VERSION).asText(),
                        node.get(KIND).asText(),
                        createObjectMetadata(node.get(METADATA)),
                        createSpec(node.get(SPEC)))
            }

            private fun createObjectMetadata(metadata: JsonNode): ObjectMeta {
                return ObjectMetaBuilder()
                        .withCreationTimestamp(metadata.get(CREATION_TIMESTAMP).asText())
                        .withGeneration(metadata.get(GENERATION).asLong())
                        .withName(metadata.get(NAME).asText())
                        .withNamespace(metadata.get(NAMESPACE).asText())
                        .withResourceVersion(metadata.get(RESOURCE_VERSION).asText())
                        .withSelfLink(metadata.get(SELF_LINK).asText())
                        .withUid(metadata.get(UID).asText())
                        .build()
            }

            private fun createSpec(node: JsonNode?): GenericCustomResourceSpec {
                val specs: Map<String, Any> = ObjectMapper().convertValue(node, object: TypeReference<Map<String, Any>>(){})
                return GenericCustomResourceSpec(specs)
            }

        }
    }

}