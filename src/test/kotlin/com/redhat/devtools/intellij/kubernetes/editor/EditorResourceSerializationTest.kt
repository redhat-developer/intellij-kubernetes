/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.json.JsonFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.utils.Serialization
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.tuple
import org.jetbrains.yaml.YAMLFileType
import org.junit.Test

class EditorResourceSerializationTest {

    @Test
    fun `#deserialize returns empty list if it is given null yaml`() {
        // given
        // when
        val deserialized = EditorResourceSerialization.deserialize(null, YAMLFileType.YML, "dagobah")
        // then
        assertThat(deserialized)
            .isEmpty()
    }

    @Test
    fun `#deserialize returns a list of resources if given multi-resource YAML`() {
        // given
        val yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              name: yoda
            ---
            apiVersion: v1
            kind: Service
            metadata:
              name: luke
        """.trimIndent()
        // when
        val deserialized = EditorResourceSerialization.deserialize(yaml, YAMLFileType.YML, "dagobah")
        // then
        assertThat(deserialized)
            .hasSize(2)
            .extracting("kind")
            .containsExactly("Pod", "Service")
    }

    @Test
    fun `#deserialize returns list of resources if given multiple JSON resources`() {
        // given
        val json = """
            [
                {"apiVersion": "v1", "kind": "Pod"},
                {"apiVersion": "v1", "kind": "Service"}
            ]
        """.trimIndent()
        val deserialized = EditorResourceSerialization.deserialize(json, JsonFileType.INSTANCE, null)
        assertThat(deserialized)
            .extracting(HasMetadata::getKind, HasMetadata::getApiVersion)
            .containsExactlyInAnyOrder(
                tuple("Pod", "v1"),
                tuple("Service", "v1"))
    }

    @Test
    fun `#deserialize returns a list with a single resource if given valid JSON with a single resource`() {
        // given
        val json = """
            {
                "apiVersion": "v1",
                "kind": "Pod",
                "metadata": {
                    "name": "obiwan"
                }
            }
        """.trimIndent()
        // when
        val deserialized = EditorResourceSerialization.deserialize(json, JsonFileType.INSTANCE, null)
        // then
        assertThat(deserialized)
            .singleElement()
            .extracting("kind")
            .isEqualTo("Pod")
    }

    @Test
    fun `#deserialize sets the current namespace to the resulting resource if it has no namespace`() {
        // given
        val yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              name: has-no-namespace
        """.trimIndent()
        // when
        val deserialized = EditorResourceSerialization.deserialize(yaml, YAMLFileType.YML, "namespace-that-should-be-set")
        // then
        assertThat(deserialized)
            .first()
            .extracting { it.metadata.namespace }
            .isEqualTo("namespace-that-should-be-set")
    }

    @Test
    fun `#deserialize sets the current namespace only to the resources that have no namespace`() {
        // given
        val yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              name: has-no-namespace
            ---
            apiVersion: v1
            kind: Pod
            metadata:
              name: has-a-namespace
              namespace: alderaan
        """.trimIndent()
        // when
        val deserialized = EditorResourceSerialization.deserialize(
            yaml, YAMLFileType.YML, "namespace-that-should-be-set"
        )
        // then
        assertThat(deserialized)
            .satisfiesExactly(
                { assertThat(it.metadata.namespace).isEqualTo("namespace-that-should-be-set") }, // namespace set
                { assertThat(it.metadata.namespace).isEqualTo("alderaan") } // no namespace set
            )
    }

    @Test
    fun `#deserialize does not change namespace in the resulting resource if it has a namespace`() {
        // given
        val yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              name: yoda
              namespace: has-a-namespace
        """.trimIndent()
        // when
        val deserialized = EditorResourceSerialization.deserialize(yaml, YAMLFileType.YML, "should-not-override-existing")
        // then
        assertThat(deserialized)
            .first()
            .extracting { it.metadata.namespace }
            .isEqualTo("has-a-namespace")
    }

    @Test
    fun `#deserialize does not change namespace in the resulting resource if it has a namespace and no given namespace`() {
        // given
        val yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              name: yoda
              namespace: has-a-namespace
        """.trimIndent()
        // when
        val deserialized = EditorResourceSerialization.deserialize(yaml, YAMLFileType.YML, null) // no namespace provided, keep whatever exists
        // then
        assertThat(deserialized)
            .first()
            .extracting { it.metadata.namespace }
            .isEqualTo("has-a-namespace")
    }

    @Test
    fun `#deserialize throws if given invalid yaml`() {
        // given
        val invalidYaml = """
            apiVersion: v1
            kind: Pod
            metadata: invalid
        """.trimIndent()
        assertThatThrownBy {
            // when
            EditorResourceSerialization.deserialize(invalidYaml, YAMLFileType.YML, "dagobah")
            // then
        }.isInstanceOf(ResourceException::class.java)
    }

    @Test
    fun `#serialize returns null if given null file type`() {
        // given
        val resource = resource<Pod>("darth vader")
        // when
        val serialized = EditorResourceSerialization.serialize(listOf(resource), null)
        // then
        assertThat(serialized)
            .isNull()
    }

    @Test
    fun `#serialize returns json array for given multiple resources and JSON file type`() {
        // given
        val resources = listOf(
            resource<Pod>("darth vader"),
            resource<Pod>("emperor")
        )
        val expected = Serialization.asJson(resources).trim()
        // when
        val serialized = EditorResourceSerialization.serialize(resources, JsonFileType.INSTANCE)
        // then
        assertThat(serialized).isEqualTo(expected)
    }

    @Test
    fun `#serialize returns correct YAML if given single resource and YAML file type`() {
        // given
        val resource = resource<Pod>("obiwan")
        val expected = Serialization.asYaml(resource).trim()
        // when
        val serialized = EditorResourceSerialization.serialize(listOf(resource), YAMLFileType.YML)
        //then
        assertThat(serialized)
            .isEqualTo(expected)
    }

    @Test
    fun `#serialize returns multiple YAML resources joined with newline if given 2 resources and YAML file type`() {
        // given
        val resources = listOf(
            resource<Pod>("leia"),
            resource<Pod>("luke")
        )
        val expected = resources
            .joinToString("\n") {
                Serialization.asYaml(it).trim()
            }
        // when
        val serialized = EditorResourceSerialization.serialize(resources, YAMLFileType.YML)
        // then
        assertThat(serialized)
            .isEqualTo(expected)
    }

    @Test
    fun `#serialize returns JSON if given JSON file type`() {
        // given
        val resource = resource<Pod>("obiwan")
        val expected = Serialization.asJson(resource).trim()
        // when
        val serialized = EditorResourceSerialization.serialize(listOf(resource), JsonFileType.INSTANCE)
        // then
        assertThat(serialized)
            .isEqualTo(expected)
    }

    @Test
    fun `#serialize returns '' if given unsupported file type`() {
        // given
        val resource = resource<Pod>("leia")
        // when
        val serialized = EditorResourceSerialization.serialize(listOf(resource), PlainTextFileType.INSTANCE)
        // then
        assertThat(serialized)
            .isEqualTo("")
    }
}