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
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.clusterScopedApiResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namespacedApiResource
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.APIResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.base.PatchContext
import io.fabric8.kubernetes.client.dsl.base.PatchType
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatcher
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

    private val namespacedCoreResource = PodBuilder()
        .withApiVersion("v1")
        .withKind("GrandJedi")
        .withNewMetadata()
        .withName("obwian")
        .withNamespace("jedis")
        .endMetadata()
        .build()

    private val clusterscopedCoreResource = PodBuilder()
        .withApiVersion("v1")
        .withKind("GrandJedi")
        .withNewMetadata()
        // no namespace
        .withName("Mace Windu")
        .endMetadata()
        .build()

    /** .withName(name) */
    private val withNameOp: Resource<GenericKubernetesResource> = mock()
    /** .resource(resource) */
    private val resourceOp: Resource<GenericKubernetesResource> = mock()
    /** .inNamespace(namespace) */
    private val inNamespaceOp =
        mock<MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>>() {
            on { withName(any()) } doReturn withNameOp
            on { resource(any()) } doReturn resourceOp
        }

    /** client.genericKubernetesResources() */
    private val genericKubernetesResourcesOp =
        mock<MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>> {
            on { inNamespace(any()) } doReturn inNamespaceOp
            on { resource(any()) } doReturn resourceOp
        }

    private val client = createClient(
        clientNamespace,
        genericKubernetesResourcesOp
    )

    private val clientAdapter: KubeClientAdapter = KubeClientAdapter(client)

    @Before
    fun before() {
        clearInvocations(genericKubernetesResourcesOp)
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
    fun `#get should return NULL and NOT call client#genericKubernetesResource(context) if resource has NO name NOR generateName`() {
        // given
        val unnamed = PodBuilder(namespacedCoreResource)
            .build()
        unnamed.metadata.name = null
        unnamed.metadata.generateName = null
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
        verify(genericKubernetesResourcesOp).inNamespace(namespacedCustomResource.metadata.namespace)
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
        verify(genericKubernetesResourcesOp).inNamespace(client.namespace)
    }

    @Test
    fun `#get should call client#genericKubernetesResources()#inNamespace(clientNamespace)#withName() if custom has name`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.get(namespacedCustomResource)
        // then
        verify(genericKubernetesResourcesOp.inNamespace(namespacedCoreResource.metadata.name))
            .withName(namespacedCustomResource.metadata.name)
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
    fun `#create should call #patch(SERVER_SIDE_APPLY) if resource has a name and NO managed fields`() {
        // given
        val metadata = ObjectMetaBuilder().build().apply {
            managedFields = null
        }
        val hasName = PodBuilder(namespacedCoreResource)
            .withMetadata(metadata)
            .build()
        hasName.metadata.name = "yoda"
        hasName.metadata.generateName = null
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.create(hasName)
        // then
        verify(resourceOp)
            .patch(argThat(ArgumentMatcher<PatchContext> { context ->
                context.patchType == PatchType.SERVER_SIDE_APPLY
            }))
    }

    @Test
    fun `#create should call #create if resource has no name`() {
        // given
        val hasNoName = PodBuilder(namespacedCoreResource)
            .withNewMetadata()
                .withManagedFields(ManagedFieldsEntry())
            .endMetadata()
            .build()
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.create(hasNoName)
        // then
        verify(resourceOp)
            .create()
    }

    @Test
    fun `#create should remove resourceVersion and uid before calling #create`() {
        // given
        val metadata = mock<ObjectMeta>()
        val genericResource = mock<GenericKubernetesResource> {
            on { getMetadata() } doReturn metadata
        }
        val hasNoName = PodBuilder(namespacedCoreResource)
            .withMetadata(metadata)
            .build()
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
            { resource -> genericResource }
        // when
        operator.create(hasNoName)
        // then
        verify(resourceOp).create() // make sure #create was called as this only applies when #create is called
        verify(metadata, times(2)).setResourceVersion(null) //
        verify(metadata, times(2)).setUid(null)
    }

    @Test
    fun `#create should call #create if resource has a name but managed fields`() {
        // given
        val hasNameAndManagedFields = PodBuilder(namespacedCoreResource)
            .withNewMetadata()
                .withManagedFields(ManagedFieldsEntry())
                .withName("obiwan")
            .endMetadata()
            .build()
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.create(hasNameAndManagedFields)
        // then
        verify(resourceOp)
            .create()
    }

    @Test
    fun `#replace should call #inNamespace for namespaced resource`() {
        // given
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(namespacedCoreResource)
        // then
        verify(genericKubernetesResourcesOp)
            .inNamespace(namespacedCoreResource.metadata.namespace)
    }

    @Test
    fun `#replace should NOT call #inNamespace for clusterScoped resource`() {
        // given
        val apiResource = clusterScopedApiResource(clusterscopedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(clusterscopedCoreResource)
        // then
        verify(genericKubernetesResourcesOp, never())
            .inNamespace(any())
    }

    @Test
    fun `#replace should call #patch(STRATEGIC_MERGE) if resource has a name and managed fields`() {
        // given
        val metadata = ObjectMetaBuilder()
            .withManagedFields(ManagedFieldsEntry())
            .build().apply {
                name = "yoda"
                generateName = null
            }
        val hasName = PodBuilder(namespacedCoreResource)
            .withMetadata(metadata)
            .build()
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(hasName)
        // then
        verify(resourceOp)
            .patch(argThat(ArgumentMatcher<PatchContext> { context ->
                context.patchType == PatchType.STRATEGIC_MERGE
            }))
    }

    @Test
    fun `#replace should call #patch(SERVER_SIDE_APPLY) if resource has a name and NO managed fields`() {
        // given
        val metadata = ObjectMetaBuilder()
            .build().apply {
                name = "yoda"
                generateName = null
                managedFields = null
            }
        val hasName = PodBuilder(namespacedCoreResource)
            .withMetadata(metadata)
            .build()
        val apiResource = namespacedApiResource(namespacedCoreResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(hasName)
        // then
        verify(resourceOp)
            .patch(argThat(ArgumentMatcher<PatchContext> { context ->
                context.patchType == PatchType.SERVER_SIDE_APPLY
            }))
    }

    @Test
    fun `#replace should call #create() if resource has NO name but has generateName`() {
        // given
        val hasGeneratedName = PodBuilder(namespacedCoreResource).build().apply {
            metadata.name = null
            metadata.generateName = "storm trooper clone"
        }
        val operator = NonCachingSingleResourceOperator(
            clientAdapter,
            createAPIResources(namespacedApiResource(hasGeneratedName))
        )
        // when
        operator.replace(hasGeneratedName)
        // then
        verify(resourceOp)
            .create()
    }

    @Test(expected = ResourceException::class)
    fun `#replace should throw if resource has NO name NOR generateName`() {
        // given
        val generatedName = PodBuilder(namespacedCoreResource).build().apply {
            metadata.name = null
            metadata.generateName = null
        }
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        operator.replace(generatedName)
        // then
    }

    @Test
    fun `#replace should return a clone, NOT the resource that it was given`() {
        // given
        val resource = PodBuilder(namespacedCoreResource).build()
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        // when
        val returned = operator.replace(resource)
        // then
        assertThat(returned).isNotSameAs(resource)
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
    fun `#watch should call client#genericKubernetesResource(context)#withName(name) if resource has a name`() {
        // given
        val apiResource = namespacedApiResource(namespacedCustomResource)
        val operator = NonCachingSingleResourceOperator(clientAdapter, createAPIResources(apiResource))
        val inNamespaceOperation = mock<NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>> {
            val withNameOperation = mock<Resource<GenericKubernetesResource>>()
            on { withName(any()) } doReturn withNameOperation
        }
        val genericKubernetesResourceOperation =
            mock<MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>> {
                on { inNamespace(any()) } doReturn inNamespaceOperation
            }
        doReturn(genericKubernetesResourceOperation)
            .whenever(client).genericKubernetesResources(any())
        // when
        operator.watch(namespacedCustomResource, mock())
        // then
        verify(inNamespaceOperation)
            .withName(namespacedCustomResource.metadata.name)
    }

    @Test
    fun `#watch should return NULL if resource has NO name`() {
        // given
        val unnamed = PodBuilder(namespacedCoreResource).build().apply {
            metadata.name = null
        }

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

    private fun createAPIResources(apiResource: APIResource?): APIResources {
        val apiResources = spy(APIResources(mock()))
        doReturn(apiResource)
            .whenever(apiResources).get(anyString(), anyOrNull(), anyString())
        return apiResources
    }
}