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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.API_VERSION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.CREATION_TIMESTAMP
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.GENERATION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.ITEMS
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.KIND
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.METADATA
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.NAME
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.RESOURCE_VERSION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.SELF_LINK
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.SPEC
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.UID
import org.junit.Test


private const val SCOPE = "scope"

class GenericCustomResourceFactoryTest {

	@Test
	fun `#createResources(map) should return empty list if resourceList has items of wrong type`() {
		// given
		val resourcesList = mapOf(Pair(ITEMS, Integer.valueOf(1)))
		// when
		val items = GenericCustomResourceFactory.createResources(resourcesList)
		// then
		assertThat(items).isEmpty()
	}

	@Test
	fun `#createResources(map) should return empty list if resourceList has null items`() {
		// given
		val resourcesList = mapOf(Pair(ITEMS, null))
		// when
		val items = GenericCustomResourceFactory.createResources(resourcesList)
		// then
		assertThat(items).isEmpty()
	}

	@Test
	fun `#createResources(map) should return create GenericCustomResource with empty Metadata if metadata is null`() {
		// given
		val resourcesList = mapOf(Pair(ITEMS, listOf(createGenericCustomResourceMap())))
		// when
		val items = GenericCustomResourceFactory.createResources(resourcesList)
		// then
		assertThat(items).hasSize(1)
		assertThat(items[0].metadata).isNotNull()
	}

	@Test
	fun `#createResources(map) should return create GenericCustomResource`() {
		// given
		// resource
		val resourceVersion = "version1"
		val kind = "kind1"

		// scope
		val scope = "version1"

		// metadata
		val creationTimestamp = "creation1"
		val generation: Long = 42
		val name = "name1"
		val namespace = "namespace1"
		val metadataResourceVersion = "metadataResourceVersion1"
		val selfLink = "selflink1"
		val uid = "uid"

		// resource list
		val resourcesList = mapOf(Pair(ITEMS, listOf(createGenericCustomResourceMap(
				resourceVersion,
				kind,
				createMetadataMap(
						creationTimestamp,
						generation,
						name,
						namespace,
						metadataResourceVersion,
						selfLink,
						uid),
				createSpecMap(scope)
		))))
		// when
		val items = GenericCustomResourceFactory.createResources(resourcesList)

		// then
		assertThat(items).hasSize(1)
		val resource = items[0]
		assertThat(resource.apiVersion).isEqualTo(resourceVersion)
		assertThat(resource.kind).isEqualTo(kind)

		val metadata = resource.metadata
		assertThat(metadata.creationTimestamp).isEqualTo(creationTimestamp)
		assertThat(metadata.generation).isEqualTo(generation)
		assertThat(metadata.name).isEqualTo(name)
		assertThat(metadata.namespace).isEqualTo(namespace)
		assertThat(metadata.resourceVersion).isEqualTo(metadataResourceVersion)
		assertThat(metadata.selfLink).isEqualTo(selfLink)
		assertThat(metadata.uid).isEqualTo(uid)

		val spec = resource.spec
		assertThat(spec?.values?.get(SCOPE)).isEqualTo(scope)
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
					"uid": "1229d4a6-b8aa-43a0-a5dc-b5ce6c59bf2e"
				},
				"spec": {
					"date_of_manufacturing": "2016-07-01T00:00:00Z",
					"engine": "CQ123456"
				}
			}
			""".trimIndent()
		val node: JsonNode = ObjectMapper().readTree(json)
		// when
		val resource = 	GenericCustomResourceFactory.createResource(node)
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

		val spec = resource.spec
		assertThat(spec?.values?.get("date_of_manufacturing")).isEqualTo("2016-07-01T00:00:00Z")
		assertThat(spec?.values?.get("engine")).isEqualTo("CQ123456")
	}

	private fun createSpecMap(scope: String): Map<String, Any> {
		return mapOf(Pair(SCOPE, scope))
	}

	private fun createMetadataMap(
			creationTimestamp: String? = null,
			generation: Long? = null,
			name: String? = null,
			namespace: String? = null,
			resourceVersion: String? = null,
			selfLink: String? = null,
			uid: String? = null
	): Map<String, Any?> {
		return mapOf(
				Pair(CREATION_TIMESTAMP, creationTimestamp),
				Pair(GENERATION, generation),
				Pair(NAME, name),
				Pair(NAMESPACE, namespace),
				Pair(RESOURCE_VERSION, resourceVersion),
				Pair(SELF_LINK, selfLink),
				Pair(UID, uid))
	}

	private fun createGenericCustomResourceMap(
			version: String? = null,
			kind: String? = null,
			metadata: Map<String, Any?>? = null,
			spec: Map<String, Any?>? = null
	): Map<String, Any?> {
		return mapOf(
				Pair(API_VERSION, version),
				Pair(KIND, kind),
				Pair(METADATA, metadata),
				Pair(SPEC, spec))
	}
}