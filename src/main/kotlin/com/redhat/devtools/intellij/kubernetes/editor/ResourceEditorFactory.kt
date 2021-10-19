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
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient

open class ResourceEditorFactory(
    /* for mocking purposes */
    private val getFileEditorManager: (project: Project) -> FileEditorManager = FileEditorManager::getInstance,
    /* for mocking purposes */
    private val createResourceFile: (resource: HasMetadata) -> ResourceFile? = { resource: HasMetadata ->
        ResourceFile.create(resource)
    },
    /* for mocking purposes */
    private val isValidType: (file: VirtualFile?) -> Boolean = ResourceFile.Factory::isValidType,
    /* for mocking purposes */
    private val isTemporary: (file: VirtualFile?) -> Boolean = ResourceFile.Factory::isTemporary,
    /* for mocking purposes */
    private val getDocument: (editor: FileEditor) -> Document? = { editor ->
        com.redhat.devtools.intellij.kubernetes.editor.util.getDocument(editor)
    },
    /* for mocking purposes */
    private val createEditorResource: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? =
        { editor, clients -> EditorResourceFactory.create(editor, clients) },
    /* for mocking purposes */
    private val createClients: (config: ClientConfig) -> Clients<out KubernetesClient>? =
        { config -> com.redhat.devtools.intellij.kubernetes.model.createClients(config) },
    /* for mocking purposes */
    private val reportTelemetry: (HasMetadata, TelemetryMessageBuilder.ActionMessage) -> Unit = TelemetryService::reportResource,
    /* for mocking purposes */
    private val createResourceEditor: (HasMetadata, FileEditor, Project, Clients<out KubernetesClient>) -> ResourceEditor =
        { resource, editor, project, clients -> ResourceEditor(resource, editor, project, clients) }
) {

    /**
     * Opens a new editor or focuses an existing editor for the given [HasMetadata] and [Project].
     *
     * @param resource to edit
     * @param project that this editor belongs to
     * @return the new [ResourceEditor] that was opened
     */
    fun open(resource: HasMetadata, project: Project): ResourceEditor? {
        val file = getFile(resource, project) ?: return null
        val editor = getFileEditorManager.invoke(project)
            .openFile(file, true, true)
            .firstOrNull()
            ?: return null
        return getOrCreate(editor, project)
    }

    private fun getFile(resource: HasMetadata, project: Project): VirtualFile? {
        val resourceEditor = get(resource, project)
        return if (resourceEditor != null) {
            resourceEditor.editor.file
        } else {
            createResourceFile.invoke(resource)?.write(resource)
        }
    }

    private fun get(resource: HasMetadata, project: Project): ResourceEditor? {
        return getFileEditorManager.invoke(project).allEditors
            .mapNotNull { editor -> get(editor) }
            .firstOrNull { resourceEditor ->
                // get editor for a temporary file thus only editors for temporary files are candidates
                isTemporary.invoke(resourceEditor.editor.file)
                        && resource.isSameResource(resourceEditor.editorResource)
            }
    }

    /**
     * Returns the existing or creates a new [ResourceEditor] for the given [FileEditor] and [Project].
     * Returns `null` if the given [FileEditor] is `null`, not a [ResourceEditor] or the given [Project] is `null`.
     *
     * @return the existing or a new [ResourceEditor].
     */
    fun getOrCreate(editor: FileEditor?, project: Project?): ResourceEditor? {
        if (editor == null
            || project == null) {
            return null
        }

        val resourceEditor = get(editor)
        if (resourceEditor != null) {
            return resourceEditor
        }
        return create(editor, project)
    }

    private fun hasKubernetesResource(editor: FileEditor): Boolean {
        val document = getDocument.invoke(editor) ?: return false
        return isKubernetesResource(document.text)
    }

    /**
     * Returns a [ResourceEditor] for the given [FileEditor] if it exists. Returns `null` otherwise.
     * The editor exists if it is contained in the user data for the given editor or its file.
     *
     * @param editor for which an existing [ResourceEditor] is returned.
     * @return [ResourceEditor] that exists.
     *
     * @see [FileEditor.getUserData]
     * @see [VirtualFile.getUserData]
     */
    fun get(editor: FileEditor?): ResourceEditor? {
        if (editor == null) {
            return null
        }

        return editor.getUserData(ResourceEditor.KEY_RESOURCE_EDITOR)
            ?: get(editor.file)
    }

    /**
     * Returns a [ResourceEditor] for the given [VirtualFile] if it exists. Returns `null` otherwise.
     * The editor exists if it is contained in the user data for the given file.
     *
     * @param file for which an existing [VirtualFile] is returned.
     * @return [ResourceEditor] that exists.
     *
     * @see [VirtualFile.getUserData]
     */
    fun get(file: VirtualFile?): ResourceEditor? {
        return file?.getUserData(ResourceEditor.KEY_RESOURCE_EDITOR)
    }

    private fun create(editor: FileEditor, project: Project): ResourceEditor? {
        if (!isValidType.invoke(editor.file)
            || !hasKubernetesResource(editor)) {
            return null
        }
        val telemetry = TelemetryService.instance.action(TelemetryService.NAME_PREFIX_EDITOR + "open")
        try {
            val clients = createClients.invoke(ClientConfig {}) ?: return null
            val resource = createEditorResource.invoke(editor, clients) ?: return null
            reportTelemetry.invoke(resource, telemetry)
            return create(resource, editor, project, clients)
        } catch (e: ResourceException) {
            ErrorNotification(editor, project).show(e.message ?: "", e.cause?.message)
            telemetry.error(e).send()
            return null
        }
    }

    private fun create(
        resource: HasMetadata,
        editor: FileEditor,
        project: Project,
        clients: Clients<out KubernetesClient>
    ): ResourceEditor {
        val resourceEditor = createResourceEditor.invoke(resource, editor, project, clients)
        resourceEditor.createToolbar()
        editor.putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, resourceEditor)
        editor.file?.putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, resourceEditor)
        return resourceEditor
    }
}