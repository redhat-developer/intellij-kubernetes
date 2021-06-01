/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.stream.Collectors

abstract class AbstractResourceFactory<T : HasMetadata> {

    companion object {
        @JvmStatic val ITEMS = "items"
        @JvmStatic val API_VERSION = "apiVersion"
        @JvmStatic val KIND = "kind"
        @JvmStatic val METADATA = "metadata"
        @JvmStatic val CREATION_TIMESTAMP = "creationTimestamp"
        @JvmStatic val GENERATION = "generation"
        @JvmStatic val NAME = "name"
        @JvmStatic val NAMESPACE = "namespace"
        @JvmStatic val RESOURCE_VERSION = "resourceVersion"
        @JvmStatic val SELF_LINK = "selfLink"
        @JvmStatic val UID = "uid"
        @JvmStatic val LABELS = "labels"
    }

    fun createResources(resourcesList: Map<String, Any?>): List<T> {
        @Suppress("UNCHECKED_CAST")
        val items = resourcesList[ITEMS] as? List<Map<String, Any?>> ?: return emptyList()
        return createResources(items)
    }

    private fun createResources(items: List<Map<String, Any?>>): List<T> {
        return items.stream()
            .map { createResource(it) }
            .collect(Collectors.toList())
    }

    abstract fun createResource(item: Map<String, Any?>): T

    abstract fun createResource(node: JsonNode): T

    protected fun createObjectMetadata(metadata: Map<String, Any?>?): ObjectMeta {
        if (metadata == null) {
            return ObjectMetaBuilder().build()
        }
        @Suppress("UNCHECKED_CAST")
        return ObjectMetaBuilder()
            .withCreationTimestamp(metadata[CREATION_TIMESTAMP] as? String?)
            // jackson is deserializing 'generation' to Int
            .withGeneration((metadata[GENERATION] as? Int?)?.toLong())
            .withName(metadata[NAME] as? String?)
            .withNamespace(metadata[NAMESPACE] as? String?)
            .withResourceVersion(metadata[RESOURCE_VERSION] as? String?)
            .withSelfLink(metadata[SELF_LINK] as? String?)
            .withUid(metadata[UID] as? String?)
            .withLabels(metadata[LABELS] as? Map<String, String>?)
            .build()
    }

    protected fun createObjectMetadata(metadata: JsonNode): ObjectMeta {
        return ObjectMetaBuilder()
            .withCreationTimestamp(metadata.get(CREATION_TIMESTAMP)?.asText())
            .withGeneration(metadata.get(GENERATION)?.asLong())
            .withName(metadata.get(NAME)?.asText())
            .withNamespace(metadata.get(NAMESPACE)?.asText())
            .withResourceVersion(metadata.get(RESOURCE_VERSION)?.asText())
            .withSelfLink(metadata.get(SELF_LINK)?.asText())
            .withUid(metadata.get(UID)?.asText())
            .withLabels(Serialization.jsonMapper().convertValue(
                    metadata.get(LABELS), object : TypeReference<Map<String, Any>>() {})
            )
            .build()
    }
}