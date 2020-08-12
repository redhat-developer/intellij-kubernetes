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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import java.util.stream.Collectors

object GenericResourceFactory {

	const val ITEMS = "items"
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

	fun createResources(resourcesList: Map<String, Any?>): List<GenericResource> {
		val items = resourcesList[ITEMS] as? List<Map<String, Any?>> ?: return emptyList()
		return createResources(items)
	}

	private fun createResources(items: List<Map<String, Any?>>): List<GenericResource> {
		return items.stream()
				.map { createResource(it) }
				.collect(Collectors.toList())
	}

	private fun createResource(item: Map<String, Any?>): GenericResource {
		return GenericResource(
				item[API_VERSION] as? String,
				item[KIND] as? String,
				createObjectMetadata(item[METADATA] as? Map<String, Any?>),
				GenericResourceSpec(item[SPEC] as? Map<String, Any?>))
	}

	private fun createObjectMetadata(metadata: Map<String, Any?>?): ObjectMeta {
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

	fun createResource(node: JsonNode): GenericResource {
		return GenericResource(
				node.get(API_VERSION).asText(),
				node.get(KIND).asText(),
				createObjectMetadata(node.get(METADATA)),
				createSpec(node.get(SPEC)))
	}

	private fun createObjectMetadata(metadata: JsonNode): ObjectMeta {
		return ObjectMetaBuilder()
				.withCreationTimestamp(metadata.get(CREATION_TIMESTAMP)?.asText())
				.withGeneration(metadata.get(GENERATION)?.asLong())
				.withName(metadata.get(NAME)?.asText())
				.withNamespace(metadata.get(NAMESPACE)?.asText())
				.withResourceVersion(metadata.get(RESOURCE_VERSION)?.asText())
				.withSelfLink(metadata.get(SELF_LINK)?.asText())
				.withUid(metadata.get(UID)?.asText())
				.build()
	}

	private fun createSpec(node: JsonNode?): GenericResourceSpec {
		val specs: Map<String, Any> = ObjectMapper().convertValue(node, object : TypeReference<Map<String, Any>>() {})
		return GenericResourceSpec(specs)
	}

}