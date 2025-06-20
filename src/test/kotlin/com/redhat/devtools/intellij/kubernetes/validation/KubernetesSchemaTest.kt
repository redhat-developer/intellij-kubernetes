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
package com.redhat.devtools.intellij.kubernetes.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class KubernetesSchemaTest {

    @Before
    fun setUp() {
        // Clear the cache before each test to ensure test isolation
        KubernetesSchema.clearCache()
    }

    @Test
    fun `#get should return schema for Pod with v1 apiVersion`() {
        // given
        val kind = "Pod"
        val apiVersion = "v1"

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNotNull()
            .contains("Pod is a collection of containers that can run on a host")
            .containsPattern("\"enum\":\\s*\\[\\s*\"v1\"\\s*\\]") // "enum": [ v1 ]
    }

    @Test
    fun `#get should return schema for Pod with generic apiVersion when specific not found`() {
        // given
        val kind = "Pod"
        val apiVersion = "v2" // This version doesn't exist, should fallback to generic

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNotNull()
            .contains("Pod is a collection of containers that can run on a host")
            .doesNotContainPattern("\"enum\":\\s*\\[\\s*\"v1\"\\s*\\]") // "enum": [ v1 ]
    }

    @Test
    fun `#get should return schema for Deployment with apps-v1 apiVersion`() {
        // given
        val kind = "Deployment"
        val apiVersion = "apps/v1"

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNotNull()
            .contains("Deployment enables declarative updates for Pods and ReplicaSets")
            .containsPattern("\"enum\":\\s*\\[\\s*\"apps/v1\"\\s*\\]") // "enum": [ apps/v1 ]
    }

    @Test
    fun `#get should return schema for Service with v1 schema`() {
        // given
        val kind = "Service"
        val apiVersion = "v1"

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNotNull()
            .contains("Service is a named abstraction of software service")
            .containsPattern("\"enum\":\\s*\\[\\s*\"v1\"\\s*\\]") // "enum": [ v1 ]
    }

    @Test
    fun `#get should return null for non-existent resource kind`() {
        // given
        val kind = "NonExistentResource"
        val apiVersion = "v1"

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNull()
    }

    @Test
    fun `#get should cache schemas for subsequent calls`() {
        // given
        val kind = "Pod"
        val apiVersion = "v1"

        // when
        val schema1 = KubernetesSchema.get(kind, apiVersion)
        val schema2 = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema1).isNotNull()
        assertThat(schema2).isNotNull()
        assertThat(schema1).isSameAs(schema2) // Should be the same object reference (cached)
    }

    @Test
    fun `#get should handle case insensitive kind names`() {
        // given
        val kind = "POD" // uppercase
        val apiVersion = "v1"

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNotNull()
            .contains("Pod is a collection of containers that can run on a host")
            .containsPattern("\"enum\":\\s*\\[\\s*\"v1\"\\s*\\]") // "enum": [ v1 ]
    }

    @Test
    fun `#get should handle mixed case kind names`() {
        // given
        val kind = "dEpLoYmEnT" // mixed case
        val apiVersion = "apps/v1"

        // when
        val schema = KubernetesSchema.get(kind, apiVersion)

        // then
        assertThat(schema).isNotNull()
            .contains("Deployment enables declarative updates for Pods and ReplicaSets")
            .containsPattern("\"enum\":\\s*\\[\\s*\"apps/v1\"\\s*\\]") // "enum": [ apps/v1 ]
    }

    @Test
    fun `#getPossibleFileNames should create correct patterns for core v1 resources`() {
        // given
        val kind = "Pod"
        val apiVersion = "v1"

        // when
        val fileNames = KubernetesSchema.getPossibleFileNames(kind, apiVersion)

        // then
        assertThat(fileNames).containsExactly(
            "pod-v1.json",
            "pod.json"
        )
    }

    @Test
    fun `#getPossibleFileNames should create correct patterns for grouped resources`() {
        // given
        val kind = "Deployment"
        val apiVersion = "apps/v1"

        // when
        val fileNames = KubernetesSchema.getPossibleFileNames(kind, apiVersion)

        // then
        assertThat(fileNames).containsExactly(
            "deployment-apps-v1.json",
            "deployment-v1.json",
            "deployment.json"
        )
    }

    @Test
    fun `#getPossibleFileNames should create correct patterns for custom resources`() {
        // given
        val kind = "MyCustomResource"
        val apiVersion = "example.com/v1beta1"

        // when
        val fileNames = KubernetesSchema.getPossibleFileNames(kind, apiVersion)

        // then
        assertThat(fileNames).containsExactly(
            "mycustomresource-example.com-v1beta1.json",
            "mycustomresource-v1beta1.json",
            "mycustomresource.json"
        )
    }

    @Test
    fun `#getPossibleFileNames should handle apiVersion without version`() {
        // given
        val kind = "SomeResource"
        val apiVersion = "example.com"

        // when
        val fileNames = KubernetesSchema.getPossibleFileNames(kind, apiVersion)

        // then
        assertThat(fileNames).containsExactly(
            "someresource-example.com.json",
            "someresource.json"
        )
    }

    @Test
    fun `#cache should store different schemas for different apiVersions of same kind`() {
        // given
        val kind = "Pod"
        val apiVersion1 = "v1"
        val apiVersion2 = "v2"

        // when
        val schema1 = KubernetesSchema.get(kind, apiVersion1)
        val schema2 = KubernetesSchema.get(kind, apiVersion2)

        // then
        assertThat(schema1).isNotNull()
        assertThat(schema2).isNotNull()
        // They should be different because they have different cache keys
        // even though they might resolve to the same file
        assertThat(schema1).isNotSameAs(schema2)
    }

    @Test
    fun `#cache should store different schemas for different kinds with same apiVersion`() {
        // given
        val kind1 = "Pod"
        val kind2 = "Service"
        val apiVersion = "v1"

        // when
        val schema1 = KubernetesSchema.get(kind1, apiVersion)
        val schema2 = KubernetesSchema.get(kind2, apiVersion)

        // then
        assertThat(schema1).isNotNull()
        assertThat(schema2).isNotNull()
        assertThat(schema1).isNotSameAs(schema2)
        assertThat(schema1).contains("Pod")
        assertThat(schema2).contains("Service")
    }

    @Test
    fun `#get should handle null and empty parameters gracefully`() {
        // Test various edge cases
        assertThat(KubernetesSchema.get("", "v1")).isNull()
        assertThat(KubernetesSchema.get("Pod", "")).isNull()
        assertThat(KubernetesSchema.get("", "")).isNull()
    }

    @Test
    fun `#get should work with real Kubernetes resources`() {
        // Test with some common Kubernetes resource types to ensure they load properly
        val commonResources = listOf(
            "Pod" to "v1",
            "Service" to "v1",
            "Deployment" to "apps/v1",
            "ConfigMap" to "v1",
            "Secret" to "v1",
            "Namespace" to "v1"
        )

        commonResources.forEach { (kind, apiVersion) ->
            val schema = KubernetesSchema.get(kind, apiVersion)
            assertThat(schema)
                .withFailMessage("Schema should be found for $kind with apiVersion $apiVersion")
                .isNotNull()
            assertThat(schema)
                .withFailMessage("Schema should contain kind $kind")
                .contains(kind)
        }
    }


} 