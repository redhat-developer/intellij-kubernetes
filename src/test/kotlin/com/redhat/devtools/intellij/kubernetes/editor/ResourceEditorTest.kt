/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ModifiedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import org.junit.Test

class ResourceEditorTest {

    private val allNamespaces = arrayOf(ClientMocks.NAMESPACE1, ClientMocks.NAMESPACE2, ClientMocks.NAMESPACE3)
    private val currentNamespace = ClientMocks.NAMESPACE2
    private val localCopy = POD2
    private val virtualFile: VirtualFile = mock()
    private val fileEditor: FileEditor = mock<FileEditor>().apply {
        doReturn(virtualFile)
            .whenever(this).file
    }
    private val resourceFile: ResourceFile = mock()
    private val resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile? =
        mock<(file: VirtualFile?) -> ResourceFile?>().apply {
            doReturn(resourceFile)
                .whenever(this).invoke(any())
        }
    private val resourceFileForResource: (resource: HasMetadata?) -> ResourceFile? =
        mock<(file: HasMetadata?) -> ResourceFile?>().apply {
            doReturn(resourceFile)
                .whenever(this).invoke(any())
        }

    private val project: Project = mock()
    private val clients: Clients<KubernetesClient> =
        Clients(client(currentNamespace.metadata.name, allNamespaces))
    private val createResource: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? =
        mock<(editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata?>().apply  {
            doReturn(localCopy)
                .whenever(this).invoke(any(), any())
        }
    private val clusterResource: ClusterResource = mock()
    private val pushNotification: PushNotification = mock()
    private val modifiedNotification: ModifiedNotification = mock()
    private val deletedNotification: DeletedNotification = mock()
    private val errorNotification: ErrorNotification = mock()
    private val editor = spy(
        TestableResourceEditor(
            localCopy,
            fileEditor,
            project,
            clients,
            createResource,
            { resource, clients -> clusterResource },
            resourceFileForVirtual,
            resourceFileForResource,
            pushNotification,
            modifiedNotification,
            deletedNotification,
            errorNotification
        )
    )

    @Test
    fun `#update should show error notification if creating resource throws ResourceException`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification).show("resource error", "client error")
    }

    @Test
    fun `#update should rename file if resource in editor has different name`() {
        // given editor resource is POD3
        doReturn(POD3)
            .whenever(createResource).invoke(any(), any())
        // editor resource is POD2
        val editorFile: ResourceFile = mockResourceFile(POD2.metadata.name)
        doReturn(editorFile)
            .whenever(resourceFileForVirtual).invoke(any())
        val resourceFile: ResourceFile = mockResourceFile(POD3.metadata.name)
        doReturn(resourceFile)
            .whenever(resourceFileForResource).invoke(any())
        // when
        editor.update()
        // then rename to POD3
        verify(editorFile).rename(POD3)
    }

    private fun mockResourceFile(basePath: String): ResourceFile {
        return mock {
            on { getBasePath() } doReturn basePath
        }
    }

    private class TestableResourceEditor(
        localCopy: HasMetadata?,
        editor: FileEditor,
        project: Project,
        clients: Clients<out KubernetesClient>,
        resourceFactory: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata?,
        createClusterResource: (HasMetadata, Clients<out KubernetesClient>) -> ClusterResource,
        resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile?,
        resourceFileForResource: (resource: HasMetadata) -> ResourceFile?,
        pushNotification: PushNotification,
        modifiedNotification: ModifiedNotification,
        deletedNotification: DeletedNotification,
        errorNotification: ErrorNotification
    ) : ResourceEditor(
        localCopy,
        editor,
        project,
        clients,
        resourceFactory,
        createClusterResource,
        resourceFileForVirtual,
        resourceFileForResource,
        pushNotification,
        modifiedNotification,
        deletedNotification,
        errorNotification
    )

}