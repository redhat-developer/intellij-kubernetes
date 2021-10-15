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
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.Clients
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

    private val virtualFile: VirtualFile = mock()
    private val resourceEditor: ResourceEditor = mock()
    private val fileEditor: FileEditor = mock<FileEditor>().apply {
        doReturn(null)
            .whenever(this).getUserData(ResourceEditorFactory.KEY_RESOURCE_EDITOR)
        doReturn(virtualFile)
            .whenever(this).file
    }
    private val fileEditorManager: FileEditorManager = mock<FileEditorManagerImpl>().apply {
        doReturn(arrayOf(fileEditor))
            .whenever(this).openFile(any(), any())
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
    private val document: Document = mock<Document>().apply {
        doReturn(deployment)
            .whenever(this).text
    }
    private val getDocument: (editor: FileEditor) -> Document? = { document }
    private val createEditorResource: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? = { editor, clients -> mock() }
    private val createClients: (config: ClientConfig) -> Clients<out KubernetesClient>? =
        mock<(config: ClientConfig) -> Clients<out KubernetesClient>?>().apply {
            doReturn(mock<Clients<out KubernetesClient>>())
                .whenever(this).invoke(any())
        }
    private val reportTelemetry: (HasMetadata, TelemetryMessageBuilder.ActionMessage) -> Unit = mock()
    private val createResourceEditor: (HasMetadata, FileEditor, Project, Clients<out KubernetesClient>) -> ResourceEditor = { resource, editor, project, clients -> mock() }

    private val editorFactory =
        ResourceEditorFactory(getFileEditorManager, createResourceFile, isValidType, getDocument, createEditorResource, createClients, reportTelemetry, createResourceEditor)

    @Test
    fun `#openEditor should NOT open editor if resource file could not be created`() {
        // given
        doReturn(null)
            .whenever(createResourceFile).invoke(any())
        // when
        val editor = editorFactory.openEditor(mock(), mock())
        // then
        assertThat(editor).isNull()
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
            .whenever(fileEditor).getUserData(ResourceEditorFactory.KEY_RESOURCE_EDITOR)
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isEqualTo(resourceEditor)
    }

    @Test
    fun `#getOrCreate should return null if file is NOT valid file type`() {
        // given
        doReturn(null)
            .whenever(fileEditor).getUserData(ResourceEditorFactory.KEY_RESOURCE_EDITOR) // force create
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
            .whenever(fileEditor).getUserData(ResourceEditorFactory.KEY_RESOURCE_EDITOR) // force create
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
        verify(fileEditor).putUserData(ResourceEditorFactory.KEY_RESOURCE_EDITOR, editor)
    }

    @Test
    fun `#getOrCreate should store ResourceEditor that it created in VirtualFile user data`() {
        // given
        // when
        val editor = editorFactory.getOrCreate(fileEditor, mock())
        // then
        verify(virtualFile).putUserData(ResourceEditorFactory.KEY_RESOURCE_EDITOR, editor)
    }

}