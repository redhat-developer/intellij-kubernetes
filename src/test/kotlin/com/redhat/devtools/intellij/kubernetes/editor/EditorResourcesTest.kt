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
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.atLeast
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.PodBuilder
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class EditorResourcesTest {

    private val context: IActiveContext<*,*> = mock()
    private val model: IResourceModel = mock {
        on { getCurrentContext() } doReturn context
    }
    private val editorResources: MutableList<EditorResource> = mutableListOf()
    private val createEditorResource: (resource: HasMetadata, resourceModel: IResourceModel, listener: IResourceModelListener?) -> EditorResource =
        mock<(resource: HasMetadata, resourceModel: IResourceModel, listener: IResourceModelListener?) -> EditorResource> {}.apply {
            doAnswer { invocationOnMock ->
                createEditorResourceMock(invocationOnMock.getArgument(0))
            }.whenever(this).invoke(any(), any(), anyOrNull())
        }
    private val resources = TestableEditorResources(model, createEditorResource)

    @After
    fun after() {
        resources.disposeAll()
    }

    @Test
    fun `#setResources should create EditorResource for new resource`() {
        // given
        assertThat(resources.getAllEditorResources()).isEmpty()
        // when
        resources.setResources(listOf(POD2, POD3))
        // then
        verify(createEditorResource, times(2)).invoke(any(), any(), anyOrNull())
        assertThat(resources.getAllResources()).hasSize(2)
    }

    @Test
    fun `#setResources should dispose existing EditorResource that is not present in new list`() {
        // given
        resources.setResources(listOf(POD1))
        // when
        resources.setResources(listOf(POD2, POD3))
        // then
        // dispose EditorResource for POD1
        verify(getEditorResourceMock(POD1), times(1)).dispose()
        verify(getEditorResourceMock(POD2), never()).dispose()
        verify(getEditorResourceMock(POD3), never()).dispose()
        assertThat(resources.getAllResources()).hasSize(2)
    }

    @Test
    fun `#setResources should only create EditorResource for new resource`() {
        // given
        resources.setResources(listOf(POD2, POD3))
        clearInvocations(createEditorResource)
        // when
        resources.setResources(listOf(POD1, POD3))
        // then
        // only create EditorResource for POD1
        verify(createEditorResource, times(1)).invoke(any(), any(), anyOrNull())
        assertThat(resources.getAllResources()).hasSize(2)
    }

    @Test
    fun `#setResources should update existing EditorResource with modified resource`() {
        // given
        val existing = PodBuilder(POD2)
            .build()
        val modified = PodBuilder(existing)
            .editMetadata()
                .withLabels<String, String>(Collections.singletonMap("jedi", "darth vader"))
            .endMetadata()
            .build()
        resources.setResources(listOf(existing, POD3))
        clearInvocations(createEditorResource)
        // when
        resources.setResources(listOf(modified, POD3))
        // then
        // set
        verify(getEditorResourceMock(existing), times(1)).setResource(modified)
        assertThat(resources.getAllResources()).hasSize(2)
    }

    @Test
    fun `#getAllResources should return all resources`() {
        // given
        val allResources = listOf(POD2, POD3)
        resources.setResources(allResources)
        // when
        resources.getAllResources()
        // then
        verify(getEditorResourceMock(POD2), atLeast(1)).getResource()
        verify(getEditorResourceMock(POD3), atLeast(1)).getResource()
    }

    @Test
    fun `#getAllResourcesOnCluster should return all resources from cluster`() {
        // given
        val allResources = listOf(POD2, POD3)
        resources.setResources(allResources)
        // when
        val retrievedResources = resources.getAllResourcesOnCluster()
        // then
        verify(getEditorResourceMock(POD2)).getResourceOnCluster()
        verify(getEditorResourceMock(POD3)).getResourceOnCluster()
    }

    @Test
    fun `#hasResource should return true if there is an EditorResource for given resource`() {
        // given
        val resource = POD2
        resources.setResources(listOf(resource))
        // when
        val hasResource = resources.hasResource(resource)
        // then
        assertThat(hasResource).isTrue()
    }

    @Test
    fun `#hasResource should return false if there's no EditorResource for given resource`() {
        // given
        resources.setResources(listOf(POD2))
        // when
        val hasResource = resources.hasResource(POD3)
        // then
        assertThat(hasResource).isFalse()
    }

    @Test
    fun `#push should push EditorResource with the given resource`() {
        // given
        resources.setResources(listOf(POD1, POD2, POD3))
        // when
        resources.push(POD2)
        // then
        verify(getEditorResourceMock(POD2), times(1)).push()
    }

    @Test
    fun `#pushAll should push all the EditorResources for the given resources`() {
        // given
        resources.setResources(listOf(POD1, POD2, POD3))
        // when
        resources.pushAll { editorResource: EditorResource ->
            editorResource.getResource() == POD2
                    || editorResource.getResource() == POD3
        }
        // then
        verify(getEditorResourceMock(POD1), never()).push()
        verify(getEditorResourceMock(POD2), times(1)).push()
        verify(getEditorResourceMock(POD3), times(1)).push()
    }

    @Test
    fun `#pull should pull the EditorResource for the given resource`() {
        // given
        resources.setResources(listOf(POD1, POD2, POD3))
        // when
        resources.pull(POD2)
        // then
        verify(getEditorResourceMock(POD1), never()).pull()
        verify(getEditorResourceMock(POD2), times(1)).pull()
        verify(getEditorResourceMock(POD3), never()).pull()
    }

    @Test
    fun `#watchAll should watch all the EditorResources`() {
        // given
        resources.setResources(listOf(POD1, POD2, POD3))
        // when
        resources.watchAll()
        // then
        verify(getEditorResourceMock(POD1)).watch()
        verify(getEditorResourceMock(POD2)).watch()
        verify(getEditorResourceMock(POD3)).watch()
    }

    @Test
    fun `#disposeAll should dispose all the EditorResources`() {
        // given
        resources.setResources(listOf(POD1, POD2, POD3))
        // when
        resources.disposeAll()
        // then
        verify(getEditorResourceMock(POD1)).dispose()
        verify(getEditorResourceMock(POD2)).dispose()
        verify(getEditorResourceMock(POD3)).dispose()
    }

    @Test
    fun `#stopWatchAll should stopWatch all the EditorResources`() {
        // given
        resources.setResources(listOf(POD2, POD3))
        // when
        resources.stopWatchAll()
        // then
        verify(getEditorResourceMock(POD2)).stopWatch()
        verify(getEditorResourceMock(POD3)).stopWatch()
    }

    private fun createEditorResourceMock(resource: HasMetadata): EditorResource {
        val editorResource: EditorResource = mock {
            on { getResource() } doReturn resource
        }
        // store editorResource mock in list
        editorResources.add(editorResource)
        return editorResource
    }

    private fun getEditorResourceMock(resource: HasMetadata): EditorResource {
        val editorResource = editorResources.find { editorResource ->
            editorResource.getResource() == resource // ATTENTION: counts as invocation on mock
        }
        assertThat(editorResource).isNotNull
        return editorResource!!
    }

    private class TestableEditorResources(
        model: IResourceModel,
        createEditorResource: (resource: HasMetadata, resourceModel: IResourceModel, resourceChangedListener: IResourceModelListener?) -> EditorResource
    ) : EditorResources(model, createEditorResource) {
        public override fun getAllEditorResources(): List<EditorResource> {
            return super.getAllEditorResources()
        }
    }
}