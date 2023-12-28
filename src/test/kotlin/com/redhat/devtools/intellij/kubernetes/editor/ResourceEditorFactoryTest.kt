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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor.Companion.KEY_RESOURCE_EDITOR
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder.ActionMessage
import io.fabric8.kubernetes.api.model.HasMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
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

    private val resource = createResource<HasMetadata>(deployment)
    private val virtualFile: VirtualFile = mock {
        on { isInLocalFileSystem } doReturn true
    }

    private val fileEditor: FileEditor = mock<FileEditor>().apply {
        doReturn(null)
            .whenever(this).getUserData(KEY_RESOURCE_EDITOR)
        doReturn(virtualFile)
            .whenever(this).file
    }
    private val fileEditorManager: FileEditorManager = mock<FileEditorManager>().apply {
        doReturn(arrayOf(fileEditor))
            .whenever(this).openFile(any(), any())
        doReturn(emptyArray<FileEditor>())
            .whenever(this).allEditors
        doReturn(emptyArray<FileEditor>())
            .whenever(this).openFile(any(), any(), any())
    }
    private val getFileEditorManager: (project: Project) -> FileEditorManager = mock<(project: Project) -> FileEditorManager>().apply {
        doReturn(fileEditorManager)
            .whenever(this).invoke(any())
    }
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
    private val hasKubernetesResource: (editor: FileEditor, project: Project) -> Boolean = mock<(editor: FileEditor, project: Project) -> Boolean>().apply {
        doReturn(true)
            .whenever(this).invoke(any(), any())
    }
    private val createResourceEditor: (FileEditor, Project) -> ResourceEditor =
        { editor, project -> mock() }
    private val reportTelemetry: (FileEditor, Project, TelemetryMessageBuilder.ActionMessage) -> Unit = mock()
    private val projectManager: ProjectManager = mock()
    private val getProjectManager: () -> ProjectManager = { projectManager }
    private val resourceEditor: ResourceEditor = mock {
        on { editor } doReturn fileEditor
    }

    private val actionMessage: ActionMessage = mock {
      on { property(any(), any()) } doReturn mock
    }

    private val telemetryMessageBuilder: TelemetryMessageBuilder = mockTelemetryMessageBuilder()

  private val editorFactory =
        TestableResourceEditorFactory(
            getFileEditorManager,
            createResourceFile,
            isValidType,
            isTemporary,
            hasKubernetesResource,
            createResourceEditor,
            getProjectManager
        )

    @Test
    fun `#openEditor should NOT open editor if temporary resource file could not be created`() {
        // given
        doReturn(null)
            .whenever(createResourceFile).invoke(any())
        // when
        editorFactory.openEditor(mock(), mock())
        // then
        verify(fileEditorManager, never()).openFile(any(), any(), any())
    }

    @Test
    fun `#openEditor should create new editor if existing editor edits a non-temporary file`() {
        // given
        doReturn(false)
            .whenever(isTemporary).invoke(any())
        doReturn(arrayOf(fileEditor))
            .whenever(fileEditorManager).allEditors
        doReturn(resourceEditor)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR)
        // when open an editor for a temporary file
        val editor = editorFactory.openEditor(resource, mock())
        // then new editor created
        assertThat(editor).isNotEqualTo(resourceEditor)
    }

    @Test
    fun `#openEditor should open editor for file of ResourceEditor that is in userData of opened FileEditor`() {
        // given
        doReturn(true)
            .whenever(isTemporary).invoke(any())
        doReturn(arrayOf(fileEditor))
            .whenever(fileEditorManager).allEditors
        doReturn(resourceEditor)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR)
        // when
        editorFactory.openEditor(resource, mock())
        // then
        verify(fileEditorManager).openFile(eq(resourceEditor.editor.file!!), any(), any())
    }

    @Test
    fun `#openEditor should open editor for file that has ResourceEditor in userData`() {
        // given
        doReturn(true)
            .whenever(isTemporary).invoke(any())
        doReturn(arrayOf(fileEditor))
            .whenever(fileEditorManager).allEditors
        doReturn(resourceEditor)
            .whenever(virtualFile).getUserData(KEY_RESOURCE_EDITOR)
        // when
        editorFactory.openEditor(resource, mock())
        // then
        verify(fileEditorManager).openFile(eq(resourceEditor.editor.file!!), any(), any())
    }

    @Test
    fun `#getOrCreate should return null if editor is null`() {
        // given
        // when
        val editor = editorFactory.getExistingOrCreate(null, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should return null if project is null`() {
        // given
        // when
        val editor = editorFactory.getExistingOrCreate(mock(), null)
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should return instance existing in FileEditor user data, not create new ResourceEditor`() {
        // given
        doReturn(resourceEditor)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR)
        // when
        val editor = editorFactory.getExistingOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isEqualTo(resourceEditor)
    }

    @Test
    fun `#getOrCreate should NOT create editor if file is NOT valid file type`() {
        // given
        doReturn(null)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR) // force create
        doReturn(false)
            .whenever(isValidType).invoke(any())
        doReturn(true)
            .whenever(hasKubernetesResource).invoke(any(), any())
        // when
        val editor = editorFactory.getExistingOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should NOT create resource editor if local file does NOT contain kubernetes resource`() {
        // given
        doReturn(null)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR) // force create
        doReturn(true)
            .whenever(isValidType).invoke(any())
        doReturn(false)
            .whenever(hasKubernetesResource).invoke(any(), any())
        // when
        val editor = editorFactory.getExistingOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNull()
    }

    @Test
    fun `#getOrCreate should create resource editor if local file contains kubernetes resource`() {
        // given
        doReturn(null)
            .whenever(fileEditor).getUserData(KEY_RESOURCE_EDITOR) // force create
        doReturn(true)
            .whenever(isValidType).invoke(any())
        doReturn(true)
            .whenever(hasKubernetesResource).invoke(any(), any())
        // when
        val editor = editorFactory.getExistingOrCreate(fileEditor, mock())
        // then
        assertThat(editor).isNotNull()
    }

    @Test
    fun `#getOrCreate should store ResourceEditor that it created in FileEditor user data`() {
        // given
        // when
        val editor = editorFactory.getExistingOrCreate(fileEditor, mock())
        // then
        verify(fileEditor).putUserData(KEY_RESOURCE_EDITOR, editor)
    }

    @Test
    fun `#getOrCreate should store ResourceEditor that it created in VirtualFile user data`() {
        // given
        // when
        val editor = editorFactory.getExistingOrCreate(fileEditor, mock())
        // then
        verify(virtualFile).putUserData(KEY_RESOURCE_EDITOR, editor)
    }

    private fun mockTelemetryMessageBuilder(): TelemetryMessageBuilder {
      val actionMessage: ActionMessage = mock {
        on { property(any(), any()) } doReturn mock
      }

      return mock {
        on { action(any()) } doReturn actionMessage
      }

    }

  private open inner class TestableResourceEditorFactory(
        getFileEditorManager: (project: Project) -> FileEditorManager,
        createResourceFile: (resource: HasMetadata) -> ResourceFile?,
        isValidType: (file: VirtualFile?) -> Boolean,
        isTemporary: (file: VirtualFile?) -> Boolean,
        hasKubernetesResource: (FileEditor, Project) -> Boolean,
        createResourceEditor: (FileEditor, Project) -> ResourceEditor,
        getProjectManager: () -> ProjectManager
    ) : ResourceEditorFactory(
        getFileEditorManager,
        createResourceFile,
        isValidType,
        isTemporary,
        hasKubernetesResource,
        createResourceEditor,
        getProjectManager
    ) {

        override fun runAsync(runnable: () -> Unit) {
            // dont execute in application thread pool
            runnable.invoke()
        }

        override fun runInUI(runnable: () -> Unit) {
            // dont execute in UI thread
            runnable.invoke()
        }

        override fun getTelemetryMessageBuilder(): TelemetryMessageBuilder {
          return telemetryMessageBuilder
        }
    }
}
