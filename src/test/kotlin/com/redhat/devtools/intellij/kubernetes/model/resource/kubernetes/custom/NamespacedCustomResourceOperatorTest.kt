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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceMap
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namespacedCustomResourceOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.ITEMS
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionNamesBuilder
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpecBuilder
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentCaptor


class NamespacedCustomResourceOperatorTest {

    private val currentNamespace = NAMESPACE2.metadata.name
    private val spec = CustomResourceDefinitionSpecBuilder()
        .withGroup("rebels")
        .withScope("endor")
        .withNames(CustomResourceDefinitionNamesBuilder()
            .withShortNames("rebel")
            .withKind("Rebel")
            .build()
        )
        .withVersions(CustomResourceDefinitionVersionBuilder()
            .withName("v1")
            .build()
        )
        .build()
    private val definition = CustomResourceDefinitionBuilder()
        .withSpec(spec)
        .withMetadata(ObjectMetaBuilder()
            .withName("Rebels")
            .build())
        .build()
    private val kind = ResourceKind.create(spec)!!
    private val customResource = customResource("Ezra", "Endor", definition)
    private val customResourceMap = customResourceMap(customResource)
    private val resources = mapOf<String, Any>(
        ITEMS to listOf(customResourceMap)
    )
    private val op = namespacedCustomResourceOperation(customResourceMap, resources, mock())
    private val client = client(currentNamespace, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)).apply {
        doReturn(op)
            .whenever(this).customResource(any())
    }
    private val operator = spy(NamespacedCustomResourceOperator(kind, definition, currentNamespace, client))

    @Test
    fun `#replace() is removing uid`() {
        // given
        val uid = customResource.metadata.uid
        // when
        operator.replace(customResource)
        // then
        verify(customResource.metadata)
            .uid = null
        verify(customResource.metadata)
            .uid = uid
    }

    @Test
    fun `#replace() is removing and restoring resourceVersion`() {
        // given
        val resourceVersion = customResource.metadata.resourceVersion
        // when
        operator.replace(customResource)
        // then
        verify(customResource.metadata)
            .resourceVersion = null
        verify(customResource.metadata)
            .resourceVersion = resourceVersion
    }

    @Test
    fun `#replace() is using resource namespace and not operator namespace if present`() {
        // given
        val namespaceUsed = ArgumentCaptor.forClass(String::class.java)
        assertThat(operator.namespace).isNotEqualTo(customResource.metadata.namespace)
        // when
        operator.replace(customResource)
        // then
        verify(op).createOrReplace(namespaceUsed.capture(), any<String>())
        assertThat(namespaceUsed.value).isEqualTo(customResource.metadata.namespace)
    }

    @Test
    fun `#replace() is using operator namespace if resource has no namespace`() {
        // given
        val noNamespace = customResource("Ezra", null, definition)
        assertThat(noNamespace.metadata.namespace).isNull()
        assertThat(operator.namespace).isNotNull()
        val namespaceUsed = ArgumentCaptor.forClass(String::class.java)
        // when
        operator.replace(noNamespace)
        // then
        verify(op).createOrReplace(namespaceUsed.capture(), any<String>())
        assertThat(namespaceUsed.value).isEqualTo(operator.namespace)
    }

    @Test
    fun `#create() is using resource namespace and not operator namespace if present`() {
        // given
        val namespaceUsed = ArgumentCaptor.forClass(String::class.java)
        assertThat(operator.namespace).isNotEqualTo(customResource.metadata.namespace)
        // when
        operator.create(customResource)
        // then
        verify(op).createOrReplace(namespaceUsed.capture(), any<String>())
        assertThat(namespaceUsed.value).isEqualTo(customResource.metadata.namespace)
    }

    @Test
    fun `#create() is using operator namespace if resource has no namespace`() {
        // given
        val noNamespace = customResource("Yoda", null, definition)
        assertThat(noNamespace.metadata.namespace).isNull()
        assertThat(operator.namespace).isNotNull()
        val namespaceUsed = ArgumentCaptor.forClass(String::class.java)
        // when
        operator.create(noNamespace)
        // then
        verify(op).createOrReplace(namespaceUsed.capture(), any<String>())
        assertThat(namespaceUsed.value).isEqualTo(operator.namespace)
    }

    @Test
    fun `#delete() is using resource namespace and not operator namespace if present`() {
        // given
        val namespaceUsed = ArgumentCaptor.forClass(String::class.java)
        assertThat(operator.namespace).isNotEqualTo(customResource.metadata.namespace)
        // when
        operator.delete(listOf(customResource))
        // then
        verify(op).delete(namespaceUsed.capture(), any<String>())
        assertThat(namespaceUsed.value).isEqualTo(customResource.metadata.namespace)
    }

    @Test
    fun `#delete() is using operator namespace if resource has no namespace`() {
        // given
        val noNamespace = customResource("Luke Skywalker", null, definition)
        assertThat(noNamespace.metadata.namespace).isNull()
        assertThat(operator.namespace).isNotNull()
        val namespaceUsed = ArgumentCaptor.forClass(String::class.java)
        // when
        operator.delete(listOf(noNamespace))
        // then
        verify(op).delete(namespaceUsed.capture(), any<String>())
        assertThat(namespaceUsed.value).isEqualTo(operator.namespace)
    }
}
