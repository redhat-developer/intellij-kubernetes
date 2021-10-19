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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor.Companion.KEY_RESOURCE_EDITOR
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResourceEditorFactoryTest {

    private val deployment = """
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: postgres
        spec:
          selector:
            matchLabels:
              app: postgres
          template:
            metadata:
              labels:
                app: postgres
            spec:
              containers:
                - name: postgres
                  image: postgres
                  ports:
                    - containerPort: 5432
                  env:
                    - name: POSTGRES_DB
                      value: mydatabase
                    - name: POSTGRES_USER
                      valueFrom:
                        configMapKeyRef:
                          name: postgres-config
                          key: my.username
                    - name: POSTGRES_PASSWORD
                      valueFrom:
                        secretKeyRef:
                          name: postgres-secret
                          key: secret.password 
    """.trimIndent()

    private val resource = createResource<HasMetadata?>(deployment)!!
    private val virtualFile: VirtualFile = mock {
        on { isInLocalFileSystem } doReturn true
    }

    private val fileEditor: FileEditor = mock<FileEditor>().apply {
        doReturn(null)
            .whenever(this).getUserData(KEY_RESOURCE_EDITOR)
        doReturn(virtualFile)
            .whenever(this).file
    }
    private val project: Project = mock()
    private val fileEditorManager: FileEditorManager = mock<FileEditorManager>().apply {
        doReturn(arrayOf(fileEditor))
            .whenever(this).openFile(any(), any())
        doReturn(emptyArray<FileEditor>())
            .whenever(this).allEditors
        doReturn(emptyArray<FileEditor>())
            .whenever(this).openFile(any(), any(), any())
    }
    private val getFileEditorManager: (project: Project) -> FileEditorManager = { fileEditorManager }
    private val resourceFile: ResourceFile = mock<ResourceFile>().apply {
        doReturn(this@ResourceEditorFactoryTest.virtualFile)
            .whenever(this).write(any())
    }
    private val createResourceFile: (resource: HasMetadata) -> ResourceFile =
        mock<(resource: HasMetadata) -> ResourceFile>().apply {
            doReturn(resourceFile)
                .whenever(this).invoke(any())
        }
    private val isValidType: (file: VirtualFile?) -> Boolean = mock<(file: VirtualFile?) -> Boolean>().apply {
        doReturn(true)
            .whenever(this).invoke(any())
    }
    private val isTemporary: (file: VirtualFile?) -> Boolean = mock<(file: VirtualFile?) -> Boolean>().apply {
        doReturn(true)
            .whenever(this).invoke(any())
    }
    private val document: Document = mock<Document>().apply {
        doReturn(deployment)
            .whenever(this).text
    }
    private val getDocument: (editor: FileEditor) -> Document? = { document }
    private val createEditorResource: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? =
        { editor, clients -> mock() }
    private val createClients: (config: ClientConfig) -> Clients<out KubernetesClient>? =
        mock<(config: ClientConfig) -> Clients<out KubernetesClient>?>().apply {
            doReturn(mock<Clients<out KubernetesClient>>())
                .whenever(this).invoke(any())
        }
    private val reportTelemetry: (HasMetadata, TelemetryMessageBuilder.ActionMessage) -> Unit = mock()
    private val createResourceEditor: (HasMetadata, FileEditor, Project, Clients<out KubernetesClient>) -> ResourceEditor =
        { resource, editor, project, clients -> mock() }
    private val resourceEditor: ResourceEditor = spy(ResourceEditor(resource, fileEditor, mock(), mock()))

    private val editorFactory =
        ResourceEditorFactory(getFileEditorManager, createResourceFile, isValidType, isTemporary, getDocument, createEditorResource, createClients, reportTelemetry, createResourceEditor)

    @Test
    fun `#open should NOT open editor if temporary resource file could not be created`() {
        // given
        doReturn(null)
            .whenever(createResourceFile).invoke(any())
        // when
        val editor = editorFactory.open(mock(), mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#open should return resource editor that is in userData of opened FileEditor`() {
        // given
        doReturn(true)
            .whenever(isTemporary).invoke(any())
        doReturn(arrayOf(fileEditor))
            .whenever(fileEditorManager).allEditors
        doReturn(resourceEditor)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR)
        // when
        val editor = editorFactory.open(resource, mock())
        // then
        assertThat(editor).isEqualTo(resourceEditor)
    }

    @Test
    fun `#open should create new editor if existing editor edits a non-temporary file`() {
        // given
        doReturn(false)
            .whenever(isTemporary).invoke(any())
        doReturn(arrayOf(fileEditor))
            .whenever(fileEditorManager).allEditors
        doReturn(resourceEditor)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR)
        // when open an editor for a temporary file
        val editor = editorFactory.open(resource, mock())
        // then new editor created
        assertThat(editor).isNotEqualTo(resourceEditor)
    }

    @Test
    fun `#open should return resource editor that is in userData of file in opened FileEditor`() {
        // given
        doReturn(true)
            .whenever(isTemporary).invoke(any())
        doReturn(arrayOf(fileEditor))
            .whenever(fileEditorManager).allEditors
        doReturn(resourceEditor)
            .whenever(virtualFile).getUserData(KEY_RESOURCE_EDITOR)
        // when
        val editor = editorFactory.open(resource, mock())
        // then
        assertThat(editor).isEqualTo(resourceEditor)
    }

    @Test
    fun `#getOrCreate should return null if editor is null`() {
        // given
        // when
        val editor = editorFactory.getOrCreate(null, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should return null if project is null`() {
        // given
        // when
        val editor = editorFactory.getOrCreate(mock(), null)
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should return instance existing in FileEditor user data, not create new ResourceEditor`() {
        // given
        doReturn(resourceEditor)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR)
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isEqualTo(resourceEditor)
    }

    @Test
    fun `#getOrCreate should return null if file is NOT valid file type`() {
        // given
        doReturn(null)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR) // force create
        doReturn(false)
            .whenever(isValidType).invoke(any())
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should return null if file is NOT YAML or JSON`() {
        // given
        doReturn(null)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR) // force create
        doReturn(true)
            .whenever(isValidType).invoke(any()) // force file content check
        doReturn("this is not YAML")
            .whenever(document).text
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should NOT create resource editor if clients cannot be created`() {
        // given
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNotNull()
    }

    @Test
    fun `#getOrCreate should create resource editor if no instance exists, local file contains kubernetes YAML`() {
        // given
        doReturn(null)
            .whenever(createClients).invoke(any())
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should store ResourceEditor that it created in FileEditor user data`() {
        // given
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        verify(fileEditor).putUserData(KEY_RESOURCE_EDITOR, editor)
    }

    @Test
    fun `#getOrCreate should store ResourceEditor that it created in VirtualFile user data`() {
        // given
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        verify(virtualFile).putUserData(KEY_RESOURCE_EDITOR, editor)
    }

}