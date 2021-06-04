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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.API_VERSION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.CREATION_TIMESTAMP
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.GENERATION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.ITEMS
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.KIND
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.LABELS
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.METADATA
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.NAME
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.RESOURCE_VERSION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.SELF_LINK
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.UID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry
import org.junit.Test

class HasMetadataResourceFactoryTest {

	@Test
	fun `#createResources(map) should return empty list if resourceList has items of wrong type`() {
		// given
		val resourcesList = mapOf(Pair(ITEMS, Integer.valueOf(1)))
		// when
		val items = HasMetadataResourceFactory.createResources(resourcesList)
		// then
		assertThat(items).isEmpty()
	}

	@Test
	fun `#createResources(map) should return empty list if resourceList has null items`() {
		// given
		val resourcesList = mapOf(Pair(ITEMS, null))
		// when
		val items = HasMetadataResourceFactory.createResources(resourcesList)
		// then
		assertThat(items).isEmpty()
	}

	@Test
	fun `#createResources(map) should return GenericCustomResource with empty Metadata if metadata is null`() {
		// given
		val resourcesList = mapOf(Pair(ITEMS, listOf(createHasMetadataResourceMap())))
		// when
		val items = HasMetadataResourceFactory.createResources(resourcesList)
		// then
		assertThat(items).hasSize(1)
		assertThat(items[0].metadata).isNotNull()
	}

	@Test
	fun `#createResources(map) should return GenericCustomResource`() {
		// given
		// resource
		val resourceVersion = "version1"
		val kind = "kind1"

		// metadata
		val creationTimestamp = "creation1"
		// deserizalization creates an Int
		val generation: Int = 42
		val name = "name1"
		val namespace = "namespace1"
		val metadataResourceVersion = "metadataResourceVersion1"
		val selfLink = "selflink1"
		val labels = mapOf(Pair("jedi", "yoda"))
		val uid = "uid"

		// resource list
		val resourcesList = mapOf(Pair(ITEMS, listOf(createHasMetadataResourceMap(
				resourceVersion,
				kind,
				createMetadataMap(
						creationTimestamp,
						generation,
						name,
						namespace,
						metadataResourceVersion,
						selfLink,
						uid,
						labels)
		))))
		// when
		val items = HasMetadataResourceFactory.createResources(resourcesList)

		// then
		assertThat(items).hasSize(1)
		val resource = items[0]
		assertThat(resource.apiVersion).isEqualTo(resourceVersion)
		assertThat(resource.kind).isEqualTo(kind)

		val metadata = resource.metadata
		assertThat(metadata.creationTimestamp).isEqualTo(creationTimestamp)
		assertThat(metadata.generation).isEqualTo(generation.toLong())
		assertThat(metadata.name).isEqualTo(name)
		assertThat(metadata.namespace).isEqualTo(namespace)
		assertThat(metadata.resourceVersion).isEqualTo(metadataResourceVersion)
		assertThat(metadata.selfLink).isEqualTo(selfLink)
		assertThat(metadata.uid).isEqualTo(uid)
		assertThat(metadata.labels).containsExactly(*labels.entries.toTypedArray())
	}

	@Test
	fun `createResources(jsonNode) should create GenericCustomResource`() {
		// given
		val json = """
			{
				"apiVersion": "openshift.pub/v1",
				"kind": "Car",
				"metadata": {
					"creationTimestamp": "2020-08-07T18:15:35Z",
					"generation": 1,
					"name": "alfaromeo",
					"resourceVersion": "472968",
					"selfLink": "/apis/openshift.pub/v1/cars/alfaromeo",
					"uid": "1229d4a6-b8aa-43a0-a5dc-b5ce6c59bf2e",
					"labels": {
						"cuore": "sportivo"
					}
				}
			}
			""".trimIndent()
		val node: JsonNode = ObjectMapper().readTree(json)
		// when
		val resource = 	HasMetadataResourceFactory.createResource(node)
		assertThat(resource.apiVersion).isEqualTo("openshift.pub/v1")
		assertThat(resource.kind).isEqualTo("Car")

		val metadata = resource.metadata
		assertThat(metadata.creationTimestamp).isEqualTo("2020-08-07T18:15:35Z")
		assertThat(metadata.generation).isEqualTo(1)
		assertThat(metadata.name).isEqualTo("alfaromeo")
		assertThat(metadata.namespace).isEqualTo(null)
		assertThat(metadata.resourceVersion).isEqualTo("472968")
		assertThat(metadata.selfLink).isEqualTo("/apis/openshift.pub/v1/cars/alfaromeo")
		assertThat(metadata.uid).isEqualTo("1229d4a6-b8aa-43a0-a5dc-b5ce6c59bf2e")
		assertThat(metadata.labels).containsExactly(MapEntry.entry("cuore", "sportivo"))
	}

	private fun createMetadataMap(
			creationTimestamp: String? = null,
			generation: Int? = null,
			name: String? = null,
			namespace: String? = null,
			resourceVersion: String? = null,
			selfLink: String? = null,
			uid: String? = null,
			labels: Map<String, Any>
	): Map<String, Any?> {
		return mapOf(
				Pair(CREATION_TIMESTAMP, creationTimestamp),
				Pair(GENERATION, generation),
				Pair(NAME, name),
				Pair(NAMESPACE, namespace),
				Pair(RESOURCE_VERSION, resourceVersion),
				Pair(SELF_LINK, selfLink),
				Pair(UID, uid),
				Pair(LABELS, labels)
		)
	}

	private fun createHasMetadataResourceMap(
			version: String? = null,
			kind: String? = null,
			metadata: Map<String, Any?>? = null
	): Map<String, Any?> {
		return mapOf(
				Pair(API_VERSION, version),
				Pair(KIND, kind),
				Pair(METADATA, metadata))
	}
}