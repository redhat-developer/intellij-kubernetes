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

import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinition
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinitionVersion
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata.HasMetadataResource
import com.redhat.devtools.intellij.kubernetes.model.util.getApiVersion
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.model.Scope
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CustomResourceOperatorFactoryTest {

    companion object {
        private val client = client("currentNamespace", arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3))
        private val group = null
        private const val version = "v1"
        private val neo =
            resource<HasMetadataResource>("neo", "zion", "uid", getApiVersion(group, version), "1")
        private val kind = neo.kind!!

        private val CRD_NOT_MATCHING = customResourceDefinition(
            "crd1", "ns1", "uid1", "apiVersion1",
            listOf(
                customResourceDefinitionVersion("v42")
            ),
            group,
            kind,
            Scope.CLUSTER.value()
        )
        private val CRD_CLUSTER_SCOPE = customResourceDefinition(
            "crd1", "ns1", "uid1", "apiVersion1",
            listOf(
                customResourceDefinitionVersion(version)
            ),
            group,
            kind,
            Scope.CLUSTER.value()
        )

        private val CRD_NAMESPACE_SCOPED = customResourceDefinition(
            "crd1", "ns1", "uid1", "apiVersion1",
            listOf(
                customResourceDefinitionVersion(version)
            ),
            group,
            kind,
            Scope.NAMESPACED.value()
        )

        private val CRD_UNKNOWN_SCOPE = customResourceDefinition(
            "crd1", "ns1", "uid1", "apiVersion1",
            listOf(
                customResourceDefinitionVersion(version)
            ),
            group,
            kind,
            "UNKNOWN_SCOPE"
        )

    }

    @Test
    fun `#create(resource) should return NonNamespacedCustomResourceOperator if resource is matching definition that is cluster scoped`() {
        // given
        // when
        val operator = CustomResourceOperatorFactory.create(neo, CRD_CLUSTER_SCOPE, client)
        // then
        assertThat(operator).isInstanceOf(NonNamespacedCustomResourceOperator::class.java)
    }

    @Test
    fun `#create(resource) should return NamespacedCustomResourceOperator if resource is matching definition that is namespace scoped`() {
        // given
        // when
        val operator = CustomResourceOperatorFactory.create(neo, CRD_NAMESPACE_SCOPED, client)
        // then
        assertThat(operator).isInstanceOf(NamespacedCustomResourceOperator::class.java)
    }

    @Test
    fun `#create(resource) should set namespace of resource to NamespacedCustomResourceOperator`() {
        // given
        // when
        val operator = CustomResourceOperatorFactory.create(neo, CRD_NAMESPACE_SCOPED, client)
        // then
        assertThat((operator as? NamespacedCustomResourceOperator)?.namespace).isEqualTo(neo.metadata.namespace)
    }

    @Test
    fun `#create(resource) should set current namespace in client to NamespacedCustomResourceOperator if resource has no namespace`() {
        // given
        val morpheus = resource<HasMetadataResource>("neo", null, "uid", getApiVersion(group, version), "1")
        // when
        val operator = CustomResourceOperatorFactory.create(morpheus, CRD_NAMESPACE_SCOPED, client)
        // then
        assertThat((operator as? NamespacedCustomResourceOperator)?.namespace).isEqualTo(client.namespace)
    }

    @Test(expected=IllegalArgumentException::class)
    fun `#create(resource) should throw IllegalArgumentException if definition has unknown scope`() {
        // given
        // when
        CustomResourceOperatorFactory.create(neo, CRD_UNKNOWN_SCOPE, client)
        // then
        // exception expected
    }

    @Test(expected = KubernetesClientException::class)
    fun `#create(yaml) should throw if yaml is invalid`() {
        // given
        // when
        CustomResourceOperatorFactory.create("{", CRD_NAMESPACE_SCOPED, client)
        // then
    }

    @Test
    fun `#create(yaml) should return NonNamespacedCustomResourceOperator for cluster scoped definition`() {
        // given
        val json = """
			{
				"apiVersion": "${getApiVersion(group, version)}",
				"kind": "$kind",
				"metadata": {
					"name": "neo"
				},
				"spec": {
					"sunglasses": "black"
				}
			}
            """.trimIndent()
        // when
        val operator = CustomResourceOperatorFactory.create(json, CRD_CLUSTER_SCOPE, client)
        // then
        assertThat(operator).isInstanceOf(NonNamespacedCustomResourceOperator::class.java)
    }

}