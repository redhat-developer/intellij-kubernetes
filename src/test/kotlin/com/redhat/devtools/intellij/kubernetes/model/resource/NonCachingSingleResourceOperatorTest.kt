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
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.clusterScopedApiResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namespacedApiResource
import com.redhat.devtools.intellij.kubernetes.model.resource.APIResources.APIResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.VisitFromServerGetWatchDeleteRecreateWaitApplicable
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import io.fabric8.kubernetes.model.Scope
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

    /* custom resource in wrong class, annotations in Pod: group=null, version=v1 */
    private val customResourceInLegacyClass = PodBuilder()
        .withApiVersion("rebels/alderaan")
        .withKind("JediMaster")
        .withMetadata(
            ObjectMetaBuilder()
                .withName("obiwan")
                .withNamespace("jedis")
                .build()
        )
        .build()

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
    private val customResourceOperation =
        createMixedOperation<GenericKubernetesResource, GenericKubernetesResourceList>(namespacedCustomResource)

    /* client.resources() */
    private val legacyResourceOperation = createLegacyResourceOperation(legacyResource)

    private val client = createClient(
        clientNamespace,
        customResourceOperation,
        legacyResourceOperation
    )

    @Test
    fun `#get should call client#genericKubernetesResource(context) if resource is GenericKubernetesResource`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(client).genericKubernetesResources(argThat<CustomResourceDefinitionContext> {
                kind == namespacedCustomResource.kind
                        && isNamespaceScoped
                        && scope == Scope.NAMESPACED.value()
                        && group == ApiVersionUtil.trimGroupOrNull(namespacedCustomResource.apiVersion)
                        && version == ApiVersionUtil.trimVersion(namespacedCustomResource.apiVersion)
        })
    }

    @Test
    fun `#get should call client#genericKubernetesResources()#inNamespace(resourceNamespace) if custom resource is namespaced and has namespace`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(customResourceOperation).inNamespace(namespacedCustomResource.metadata.namespace)
    }

    @Test
    fun `#get should call client#genericKubernetesResources()#inNamespace(clientNamespace) if custom resource is namespaced but has NO namespace`() {
        // given
        val noNamespace = ObjectMetaBuilder(namespacedCustomResource.metadata)
            .withNamespace(null)
            .build()
        namespacedCustomResource.metadata = noNamespace
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(customResourceOperation).inNamespace(client.namespace)
    }

    @Test
    fun `#get should NOT call client#genericKubernetesResources()#inNamespace() if custom resource is cluster scoped`() {
        // given
        val apiResource = clusterScopedApiResource(clusterCustomResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(clusterCustomResource)
        // then
        verify(customResourceOperation, never()).inNamespace(any())
    }

    @Test
    fun `#get should call custom resource operation if resource is custom resource deserialized to wrong class`() {
        // given
        val apiResource = namespacedApiResource(customResourceInLegacyClass)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(customResourceInLegacyClass)
        // then
        verify(client).genericKubernetesResources(any())
    }

    @Test(expected = KubernetesClientException::class)
    fun `#get should throw if custom resource is unknown api`() {
        // given
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(null))
        // when
        operator.get(customResourceInLegacyClass)
        // then
    }

    @Test
    fun `#get should call legacy resource operation if resource is legacy resource`() {
        // given
        val apiResource = namespacedApiResource(legacyResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(legacyResource)
        // then
        verify(client).resource(legacyResource)
    }

    @Test
    fun `#get should call client#resource()#inNamespace(resourceNamespace) if legacy resource is namespaced`() {
        // given
        val apiResource = namespacedApiResource(legacyResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(legacyResource)
        // then
        verify(legacyResourceOperation).inNamespace(legacyResource.metadata.namespace)
    }

    @Test
    fun `#get should call client#genericKubernetesResources()#inNamespace(clientNamespace) if legacy resource is namespaced but has NO namespace`() {
        // given
        val noNamespace = ObjectMetaBuilder(legacyResource.metadata)
            .withNamespace(null)
            .build()
        legacyResource.metadata = noNamespace
        val apiResource = namespacedApiResource(legacyResource)
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(apiResource))
        // when
        operator.get(legacyResource)
        // then
        verify(legacyResourceOperation).inNamespace(client.namespace)
    }

    @Test(expected = KubernetesClientException::class)
    fun `#get should throw if legacy resource is unknown api`() {
        // given
        val operator = NonCachingSingleResourceOperator(client, createAPIResources(null))
        // when
        operator.get(legacyResource)
        // then
    }

    private fun createClient(namespace: String,
        customResourceOperation: MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>,
        legacyResourceOperation: NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable<HasMetadata>
    ): KubernetesClient {
        val client: KubernetesClient = mock()
        doReturn(customResourceOperation)
            .whenever(client).genericKubernetesResources(any())
        doReturn(legacyResourceOperation)
            .whenever(client).resource(any<HasMetadata>())
        doReturn(namespace)
            .whenever(client).namespace
        return client
    }

    private fun createLegacyResourceOperation(resource: HasMetadata): NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable<HasMetadata> {
        val fromServerOp: VisitFromServerGetWatchDeleteRecreateWaitApplicable<HasMetadata> = mock {
            on { get() } doReturn resource
        }
        return mock {
            on { fromServer() } doReturn fromServerOp
        }
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