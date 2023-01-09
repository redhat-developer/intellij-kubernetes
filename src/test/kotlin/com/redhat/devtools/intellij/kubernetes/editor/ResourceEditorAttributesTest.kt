/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class ResourceEditorAttributesTest {

    private val context: IActiveContext<*,*> = mock()
    private val model: IResourceModel = mock {
        on { getCurrentContext() } doReturn context
    }
    private val clusterResource: ClusterResource = mock()
    private val createClusterResource: (resource: HasMetadata, context: IActiveContext<out HasMetadata, out KubernetesClient>?) -> ClusterResource? =
        mock {
            on { invoke(any(), any()) } doReturn clusterResource
        }
    private val attributes = EditorResourceAttributes(model, createClusterResource)

    @After
    fun after() {
        attributes.disposeAll()
    }

    @Test
    fun `#update should create clusterResource for given resource`() {
        // given
        val resource: HasMetadata = ClientMocks.POD2
        // when
        attributes.update(listOf(resource)) // create attribute for resource
        // then
        verify(createClusterResource).invoke(resource, context)
    }

    @Test
    fun `#update should watch clusterResource for given resource`() {
        // given
        val resource: HasMetadata = ClientMocks.POD2
        // when
        attributes.update(listOf(resource)) // create attribute for resource
        // then
        verify(clusterResource).watch()
    }

    @Test
    fun `#update should close existing clusterResource if new resource is given in #update`() {
        // given
        val existing: HasMetadata = ClientMocks.POD2
        val new: HasMetadata = ClientMocks.POD3
        attributes.update(listOf(existing)) // create attribute for resource
        // when
        attributes.update(listOf(new)) // create new attribute, close existing cluster resource
        // then
        verify(clusterResource).close()
    }

    @Test
    fun `#update should watch new clusterResource if #update contains additional resource`() {
        // given
        val initial: HasMetadata = ClientMocks.POD2
        val initialClusterResource: ClusterResource = mock()
        doReturn(initialClusterResource)
            .whenever(createClusterResource).invoke(eq(initial), any())
        attributes.update(listOf(initial)) // create attribute for resource
        verify(initialClusterResource).watch()

        val additional: HasMetadata = ClientMocks.POD3
        val additionalClusterResource: ClusterResource = mock()
        doReturn(additionalClusterResource)
            .whenever(createClusterResource).invoke(eq(additional), any())
        // when
        attributes.update(listOf(initial, ClientMocks.POD3))
        // then
        verify(additionalClusterResource).watch()
    }

    @Test
    fun `#update should NOT close existing clusterResource if existing resource is given again in #update`() {
        // given
        val existing: HasMetadata = ClientMocks.POD2
        attributes.update(listOf(existing)) // create attribute for resource
        // when
        attributes.update(listOf(existing)) // create new attribute, close existing cluster resource
        // then
        verify(clusterResource, never()).close()
    }

    @Test
    fun `#getClusterResource(HasMetadata) should return existing cluster resource for given resource`() {
        // given
        val resource: HasMetadata = ClientMocks.POD2
        attributes.update(listOf(resource)) // create attribute for resource
        // when
        val clusterResource = attributes.getClusterResource(resource)
        // then
        assertThat(clusterResource).isNotNull
    }

    @Test
    fun `#getClusterResource(HasMetadata) should return null if given resource wasn't given in #update()`() {
        // given
        val resource: HasMetadata = ClientMocks.POD2
        attributes.update(listOf(resource)) // create attribute for resource
        // when
        val clusterResource = attributes.getClusterResource(ClientMocks.POD3)
        // then
        assertThat(clusterResource).isNull()
    }

    @Test
    fun `#getResourceVersion(HasMetadata) should return resourceVersion of given resource`() {
        // given
        val resource: HasMetadata = ClientMocks.POD2
        attributes.update(listOf(resource)) // create attribute for resource
        // when
        val resourceVersion = attributes.getResourceVersion(resource)
        // then
        assertThat(resourceVersion).isEqualTo(resource.metadata.resourceVersion)
    }

    @Test
    fun `#getLastPulledPushed(HasMetadata) should return resource that was given in #update`() {
        // given
        val resource: HasMetadata = ClientMocks.POD2
        attributes.update(listOf(resource)) // create attribute for resource
        // when
        val resourceVersion = attributes.getLastPulledPushed(resource)
        // then
        assertThat(resourceVersion).isEqualTo(resource)
    }

    @Test
    fun `#getLastPulledPushed(HasMetadata) should return resource that was set`() {
        // given
        val initial: Pod = ClientMocks.POD2
        attributes.update(listOf(initial)) // create attribute for resource
        val new: HasMetadata = PodBuilder(initial)
            // same kind, apiversion, name, namespace
            .editMetadata()
                .withLabels<String, String>(mapOf("jedi" to "luke skywalker"))
                .withResourceVersion("42")
            .endMetadata()
            .build()
        attributes.setLastPushedPulled(new)
        // when
        val lastPulledPushed = attributes.getLastPulledPushed(initial)
        // then
        assertThat(lastPulledPushed).isEqualTo(new)
    }

}