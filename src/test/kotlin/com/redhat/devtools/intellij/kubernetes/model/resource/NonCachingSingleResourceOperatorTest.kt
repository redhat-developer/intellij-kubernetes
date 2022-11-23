/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.clusterScopedApiResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namespacedApiResource
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import io.fabric8.kubernetes.api.model.APIResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString

class NonCachingSingleResourceOperatorTest {

    private val clientNamespace = "theForce"

    private val namespacedCustomResource = GenericKubernetesResource().apply {
        this.apiVersion = "rebels/alderaan"
        this.kind = "GrandMaster"
        this.metadata = ObjectMetaBuilder()
            .withName("yoda")
            .withNamespace("jedis")
            .build()
    }

    private val clusterCustomResource = GenericKubernetesResource().apply {
        this.apiVersion = "rebels/tatooine"
        this.kind = "Jedi"
        this.metadata = ObjectMetaBuilder()
            .withName("luke")
            .build()
    }

    private val legacyResource = PodBuilder()
        .withApiVersion("v1")
        .withKind("GrandJedi")
        .withNewMetadata()
            .withName("obwian")
            .withNamespace("jedis")
        .endMetadata()
        .build()

    /* client.genericKubernetesResources() */
    private val genericResourceOperation =
        createMixedOperation<GenericKubernetesResource, GenericKubernetesResourceList>(namespacedCustomResource)

    private val client = createClient(
        clientNamespace,
        genericResourceOperation
    )

    private val clientAdapter: KubeClientAdapter = KubeClientAdapter(client)

    @Before
    fun before() {
        clearInvocations(genericResourceOperation)
    }

    @Test
    fun `#get should call client#genericKubernetesResource(context) if resource has a name`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(client).genericKubernetesResources(argThat {
                kind == namespacedCustomResource.kind
                        && isNamespaceScoped
                        && group == ApiVersionUtil.trimGroupOrNull(namespacedCustomResource.apiVersion)
                        && version == ApiVersionUtil.trimVersion(namespacedCustomResource.apiVersion)
        })
    }

    @Test
    fun `#get should return NULL and NOT call client#genericKubernetesResource(context) if resource has NO name`() {
        // given
        val unnamed = PodBuilder(legacyResource)
            .build()
        unnamed.metadata.name = null
        val apiResource = namespacedApiResource(unnamed)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        val result = operator.get(unnamed)
        // then
        verify(client, never()).genericKubernetesResources(any())
        assertThat(result).isNull()
    }

    @Test
    fun `#get should call client#genericKubernetesResources()#inNamespace(resourceNamespace) if custom resource is namespaced and has namespace`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(genericResourceOperation).inNamespace(namespacedCustomResource.metadata.namespace)
    }

    @Test
    fun `#get should call client#genericKubernetesResources()#inNamespace(clientNamespace) if custom resource is namespaced but has NO namespace`() {
        // given
        val noNamespace = ObjectMetaBuilder(namespacedCustomResource.metadata)
            .withNamespace(null)
            .build()
        namespacedCustomResource.metadata = noNamespace
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(genericResourceOperation).inNamespace(client.namespace)
    }

    @Test
    fun `#get should NOT call client#genericKubernetesResources()#inNamespace() if resource is cluster scoped`() {
        // given
        val apiResource = clusterScopedApiResource(clusterCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.get(clusterCustomResource)
        // then
        verify(genericResourceOperation, never()).inNamespace(any())
    }

    @Test(expected = KubernetesClientException::class)
    fun `#get should throw if custom resource is unknown api`() {
        // given
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(null))
        // when
        operator.get(namespacedCustomResource)
        // then
    }

    @Test
    fun `#replace should call client#genericKubernetesResource(context) if resource has a name`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(namespacedCustomResource)
        // then
        verify(client).genericKubernetesResources(argThat {
            matchesContext(namespacedCustomResource, true, this) })
    }

    @Test
    fun `#replace should call #createOrReplace() if resource has a name`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(namespacedCustomResource)
        // then
        verify(genericResourceOperation.inNamespace(namespacedCustomResource.metadata.namespace))
            .createOrReplace(argThat {
                isSameResource(namespacedCustomResource)
            })
    }

    @Test
    fun `#replace should call #create() if resource has NO name but has generateName`() {
        // given
        val generatedName = PodBuilder(legacyResource).build()
        generatedName.metadata.name = null
        generatedName.metadata.generateName = "storm trooper clone"
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(generatedName)
        // then
        verify(genericResourceOperation.inNamespace(generatedName.metadata.namespace))
            .create(argThat { isSameResource(generatedName) })
    }

    @Test(expected = ResourceException::class)
    fun `#replace should throw if resource has NO name NOR generateName`() {
        // given
        val generatedName = PodBuilder(legacyResource).build()
        generatedName.metadata.name = null
        generatedName.metadata.generateName = null
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(generatedName)
        // then
    }

    @Test
    fun `#watch should call client#genericKubernetesResource(context) if resource has a name`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.watch(namespacedCustomResource, mock())
        // then
        verify(client).genericKubernetesResources(argThat {
            matchesContext(namespacedCustomResource,true, this)
        })
    }

    @Test
    fun `#watch should return NULL if resource has NO name`() {
        // given
        val unnamed = PodBuilder(legacyResource).build()
        unnamed.metadata.name = null
        val apiResource = namespacedApiResource(unnamed)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        val result = operator.watch(unnamed, mock())
        // then
        assertThat(result).isNull()
    }

    private fun matchesContext(
        resource: HasMetadata,
        isNamespaceScoped: Boolean,
        context: ResourceDefinitionContext
    ): Boolean {
        return context.kind == resource.kind
                && context.isNamespaceScoped == isNamespaceScoped
                && context.group == ApiVersionUtil.trimGroup(resource.apiVersion)
                && context.version == ApiVersionUtil.trimVersion(resource.apiVersion)
    }

    private fun createClient(
        namespace: String,
        genericKubernetesResourceOp: MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>
    ): NamespacedKubernetesClient {
        val client: NamespacedKubernetesClient = mock()
        doReturn(genericKubernetesResourceOp)
            .whenever(client).genericKubernetesResources(any())
        doReturn(namespace)
            .whenever(client).namespace
        return client
    }

    private fun <T, L> createMixedOperation(resource: T): MixedOperation<T, L, Resource<T>> {
        val withNameInNamespaceOp: Resource<T> = createWithNameOp(createFromServerOp(resource))
        val inNamespaceOp: MixedOperation<T, L, Resource<T>> = mock {
            on { withName(any()) } doReturn withNameInNamespaceOp
        }
        val withNameOp: Resource<T> = createWithNameOp(createFromServerOp(resource))
        return mock {
            on { inNamespace(any()) } doReturn inNamespaceOp
            on { withName(any()) } doReturn withNameOp
        }
    }

    private fun <T> createWithNameOp(fromServerOp: Resource<T>): Resource<T> {
        return mock {
            on { fromServer() } doReturn fromServerOp
        }
    }

    private fun <T> createFromServerOp(resource: T): Resource<T> {
        return mock {
            on { get() } doReturn resource
        }
    }

    private fun createAPIResources(apiResource: APIResource?): APIResources {
        val apiResources = spy(APIResources(mock()))
        doReturn(apiResource)
            .whenever(apiResources).get(anyString(), anyOrNull(), anyString())
        return apiResources
    }
}