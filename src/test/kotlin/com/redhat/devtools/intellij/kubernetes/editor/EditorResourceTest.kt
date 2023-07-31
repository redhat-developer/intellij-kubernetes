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
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.times

class EditorResourceTest {

    private val context: IActiveContext<*,*> = mock()
    private val model: IResourceModel = mock {
        on { getCurrentContext() } doReturn context
    }
    private val onClusterResourceChanged: IResourceModelListener = mock()
    private val clusterResource: ClusterResource = mock()
    private val createClusterResource: (resource: HasMetadata, context: IActiveContext<out HasMetadata, out KubernetesClient>?) -> ClusterResource? =
        mock {
            on { invoke(any(), any()) } doReturn clusterResource
        }

    @Test
    fun `#constructor should create clusterResource`() {
        // given
        // when
        createEditorResource(POD2)
        // then
        verify(createClusterResource).invoke(POD2, context)
    }

    @Test
    fun `#constructor should add given listener to clusterResource that it creates`() {
        // given
        // when
        createEditorResource(POD2)
        // then
        verify(createClusterResource).invoke(POD2, context)
        verify(clusterResource).addListener(onClusterResourceChanged)
    }

    @Test
    fun `#constructor should watch clusterResource that it creates`() {
        // given
        // when
        createEditorResource(POD2)
        // then
        verify(createClusterResource).invoke(POD2, context)
        verify(clusterResource).watch()
    }

    @Test
    fun `#setResource should set resource if given resource is same but modified resource`() {
        // given
        val original = POD2
        val modified = PodBuilder(POD2)
            .editMetadata()
                .withLabels<String, String>(Collections.singletonMap("jedi","luke skywalker"))
            .endMetadata()
            .build()
        val editorResource = createEditorResource(original)
        // when
        editorResource.setResource(modified)
        // then
        assertThat(editorResource.getResource()).isEqualTo(modified)
    }

    @Test
    fun `#setResource should NOT set resource if given resource not the same resource`() {
        // given
        val original = POD2
        val notSame = POD3
        val editorResource = createEditorResource(original)
        // when
        editorResource.setResource(notSame)
        // then
        assertThat(editorResource.getResource()).isNotEqualTo(notSame)
    }

    @Test
    fun `#getResourceOnCluster should pull resource from cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(POD3)
            .whenever(clusterResource).pull()
        // when
        editorResource.getResourceOnCluster()
        // then
        verify(clusterResource).pull(any())
    }

    @Test
    fun `#push should push resource to cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        // when
        editorResource.push()
        // then
        verify(clusterResource).push(editorResource.getResource())
    }

    @Test
    fun `#push should store resource version of resource returned by cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(POD3)
            .whenever(clusterResource).push(any())
        assertThat(editorResource.getResourceVersion()).isEqualTo(POD2.metadata.resourceVersion)
        // when
        editorResource.push()
        // then
        assertThat(editorResource.getResourceVersion()).isEqualTo(POD3.metadata.resourceVersion)
    }

    @Test
    fun `#push should store local resource that was pushed`() {
        // given
        val toPush = PodBuilder(POD2).build()
        val editorResource = createEditorResource(toPush)
        // when
        editorResource.push()
        // then
        // stored local resource that was pushed (not resource return from cluster)
        assertThat(editorResource.getLastPushedPulled()).isEqualTo(toPush)
    }

    @Test
    fun `#push should store local resource that was pushed even if pushing fails`() {
        // given
        val toPush = PodBuilder(POD2).build()
        val editorResource = createEditorResource(toPush)
        doThrow(ResourceException::class)
            .whenever(clusterResource).push(any())
        // when
        editorResource.push()
        // then
        // stored local resource that was pushed (not resource return from cluster)
        assertThat(editorResource.getLastPushedPulled()).isEqualTo(toPush)
    }

    @Test
    fun `#push should set to pushed state if successful`() {
        // given
        val toPush = PodBuilder(POD2).build()
        val editorResource = createEditorResource(toPush)
        assertThat(editorResource.getState()).isNotInstanceOf(Pushed::class.java)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(true) // after a push: resource exists on cluster
            .whenever(clusterResource).exists()
        doReturn(false) // after a push: resource is not deleted on cluster
            .whenever(clusterResource).isDeleted()
        doReturn(false) // after a push: local resource is not outdated when compared with cluster resource
            .whenever(clusterResource).isOutdatedVersion(any())
        // when
        editorResource.push()
        // then
        assertThat(editorResource.getState()).isInstanceOf(Pushed::class.java)
    }

    @Test
    fun `#push should set to error state if pushing to the cluster fails`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        assertThat(editorResource.getState()).isNotInstanceOf(Error::class.java)
        doThrow(ResourceException("interference with the force"))
            .whenever(clusterResource).push(any())
        // when
        editorResource.push()
        // then
        assertThat(editorResource.getState()).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#pull should pull resource from cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        // when
        editorResource.pull()
        // then
        verify(clusterResource).pull(any())
    }

    @Test
    fun `#pull should store resource version of resource returned by cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(POD3)
            .whenever(clusterResource).pull(any())
        assertThat(editorResource.getResourceVersion()).isEqualTo(POD2.metadata.resourceVersion)
        // when
        editorResource.pull()
        // then
        assertThat(editorResource.getResourceVersion()).isEqualTo(POD3.metadata.resourceVersion)
    }

    @Test
    fun `#pull should store local resource that existed when pulling`() {
        // given
        val toPull = PodBuilder(POD2).build()
        val pulled = POD3
        doReturn(pulled)
            .whenever(clusterResource).pull(any())
        val editorResource = createEditorResource(toPull)
        editorResource.setLastPushedPulled(toPull)
        // when
        editorResource.pull()
        // then
        // stored resource that was pulled (not local resource)
        assertThat(editorResource.getLastPushedPulled()).isEqualTo(pulled)
    }

    @Test
    fun `#pull should set to pulled state if successful`() {
        // given
        val toPull = PodBuilder(POD2).build()
        val pulled = PodBuilder(POD2) // needs to be same resource but modified
            .editMetadata()
                .withLabels<String, String>(Collections.singletonMap("jedi", "yoda"))
            .endMetadata()
            .build()
        doReturn(pulled)
            .whenever(clusterResource).pull(any())
        val editorResource = createEditorResource(toPull)
        assertThat(editorResource.getState()).isNotInstanceOf(Pushed::class.java)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(true) // after a pull: resource exists on cluster
            .whenever(clusterResource).exists()
        doReturn(false) // after a pull: resource is not deleted on cluster
            .whenever(clusterResource).isDeleted()
        doReturn(false) // after a pull: local resource is not outdated when compared with cluster resource
            .whenever(clusterResource).isOutdatedVersion(any())
        // when
        editorResource.pull()
        // then
        assertThat(editorResource.getState()).isInstanceOf(Pulled::class.java)
    }

    @Test
    fun `#pull should set to error state if pulling from the cluster fails`() {
        // given
        val editorResource = createEditorResource(POD2)
        editorResource.setLastPushedPulled(POD2) // not modified
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        assertThat(editorResource.getState()).isNotInstanceOf(Error::class.java)
        doThrow(ResourceException("interference with the force"))
            .whenever(clusterResource).pull(any())
        // when
        editorResource.pull()
        // then
        assertThat(editorResource.getState()).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should return error if previous state was error and it is not modified`() {
        // given
        val editorResource = createEditorResource(POD2)
        val error = Error("oh my!")
        editorResource.setState(error)
        editorResource.setLastPushedPulled(POD2) // modified = (current resource != lastPushedPulled)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isEqualTo(error)
    }

    @Test
    fun `#getState should return modified state if previous state was error but editor is modified`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        val editorResource = createEditorResource(POD2)
        val modifiedPod2 = PodBuilder(POD2)
            .editMetadata()
                .withLabels<String, String>(mapOf("jedi" to "yoda"))
            .endMetadata()
            .build()
        val error = Error("oh my!")
        editorResource.setState(error)
        editorResource.setResource(modifiedPod2) // new resource != existing resource, causes state to be reset and then recreated
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Modified::class.java)
    }

    @Test
    fun `#getState should return error if cluster resource is null`() {
        // given
        val editorResource = createEditorResource(POD2)
        editorResource.clusterResource = null
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should return error if resource has neither name nor generateName`() {
        // given
        val resource = PodBuilder()
            .withNewMetadata()
                .withName(null)
                .withGenerateName(null)
            .endMetadata()
            .build()
        val editorResource = createEditorResource(resource)
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should NOT return error if resource has name`() {
        // given
        val resource = PodBuilder()
            .withNewMetadata()
            .withName("yoda")
            .withGenerateName(null)
            .endMetadata()
            .build()
        val editorResource = createEditorResource(resource)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isNotInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should NOT return error if resource has generateName`() {
        // given
        val resource = PodBuilder()
            .withNewMetadata()
            .withName(null)
            .withGenerateName("jedi")
            .endMetadata()
            .build()
        val editorResource = createEditorResource(resource)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isNotInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should return DeletedOnCluster if resource is deleted on cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(true)
            .whenever(clusterResource).isDeleted()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(DeletedOnCluster::class.java)
    }

    @Test
    fun `#getState should return Modified if resource doesnt exist on cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(false)
            .whenever(clusterResource).exists()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Modified::class.java)
        assertThat((state as Modified).exists).isFalse()
    }

    @Test
    fun `#getState should return Modified if resource is modified when compared to last pulled pushed resource`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(true) // don't create modified state because it doesnt exist on cluster
            .whenever(clusterResource).exists()
        val editorResource = createEditorResource(POD2)
        val modified = PodBuilder(POD2)
            .editMetadata()
                .withLabels<String, String>(Collections.singletonMap("jedi", "skywalker"))
            .endMetadata()
            .build()
        editorResource.setResource(modified)
        editorResource.setLastPushedPulled(POD2)
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Modified::class.java)
    }

    @Test
    fun `#getState should return Outdated if resource is outdated (when compared to the cluster resource)`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(true)
            .whenever(clusterResource).isOutdatedVersion(any())
        doReturn(true) // don't return modified state because it doesnt exist
            .whenever(clusterResource).exists()
        editorResource.setLastPushedPulled(POD2) // don't return modified because last pulled pushed is different
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Outdated::class.java)
    }

    @Test
    fun `#getState should return Error if resource is outdated but in error`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(true)
            .whenever(clusterResource).isAuthorized()
        doReturn(true)
            .whenever(clusterResource).isOutdatedVersion(any())
        doReturn(true) // don't return modified state because it doesnt exist
            .whenever(clusterResource).exists()
        editorResource.setLastPushedPulled(POD2) // don't return modified because last pulled pushed is different
        editorResource.setState(mock<Error>())
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should return Error if resource is NOT supported on cluster`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(false)
            .whenever(clusterResource).isSupported()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#getState should return Error if cluster is not authorized`() {
        // given
        val editorResource = createEditorResource(POD2)
        doReturn(true)
            .whenever(clusterResource).isSupported()
        doReturn(false)
            .whenever(clusterResource).isAuthorized()
        // when
        val state = editorResource.getState()
        // then
        assertThat(state).isInstanceOf(Error::class.java)
    }

    @Test
    fun `#dispose should close clusterResource that was created`() {
        // given
        val editorResource = createEditorResource(POD2)
        // when
        editorResource.dispose()
        // then
        verify(createClusterResource).invoke(POD2, context)
        verify(clusterResource).close()
    }

    @Test
    fun `#dispose should NOT close clusterResource if it was closed already`() {
        // given
        val editorResource = createEditorResource(POD2)
        editorResource.dispose()
        // when
        editorResource.dispose()
        // then
        verify(createClusterResource).invoke(POD2, context)
        verify(clusterResource, times(1)).close()
    }

    private fun createEditorResource(resource: HasMetadata): TestableEditorResource {
        return TestableEditorResource(resource, model, onClusterResourceChanged, createClusterResource)
    }

    private class TestableEditorResource(
        resource: HasMetadata,
        model: IResourceModel,
        listener: IResourceModelListener,
        createClusterResource: (HasMetadata, IActiveContext<out HasMetadata, out KubernetesClient>?) -> ClusterResource?
    ) : EditorResource(
        resource,
        model,
        listener,
        createClusterResource
    ) {
        public override var clusterResource: ClusterResource? = super.clusterResource

        public override fun setState(state: EditorResourceState?) {
            super.setState(state)
        }

        public override fun setLastPushedPulled(resource: HasMetadata?) {
            super.setLastPushedPulled(resource)
        }

        public override fun getLastPushedPulled(): HasMetadata? {
            return super.getLastPushedPulled()
        }

        public override fun getResourceVersion(): String? {
            return super.getResourceVersion()
        }

    }

}