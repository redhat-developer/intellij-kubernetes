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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.hasKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import io.fabric8.kubernetes.api.model.HasMetadata


open class ResourceEditorFactory protected constructor(
    /* for mocking purposes */
    private val getFileEditorManager: (project: Project) -> FileEditorManager = FileEditorManager::getInstance,
    /* for mocking purposes */
    private val createResourceFile: (resource: HasMetadata) -> ResourceFile? = { resource ->
        ResourceFile.create(resource)
    },
    /* for mocking purposes */
    private val isValidType: (file: VirtualFile?) -> Boolean = ResourceFile.Factory::isValidType,
    /* for mocking purposes */
    private val isTemporary: (file: VirtualFile?) -> Boolean = ResourceFile.Factory::isTemporary,
    /* for mocking purposes */
    private val hasKubernetesResource: (FileEditor, Project) -> Boolean = { editor, project ->
        hasKubernetesResource(editor.file, project)
    },
    /* for mocking purposes */
    private val createResourceEditor: (FileEditor, Project) -> ResourceEditor =
        { editor, project -> ResourceEditor(editor, IResourceModel.getInstance(), project) },
    /* for mocking purposes */
    private val getProjectManager: () -> ProjectManager = {  ProjectManager.getInstance() },
) {

    companion object {
        val instance = ResourceEditorFactory()
        private val KEY_RESOURCE = Key<HasMetadata>(HasMetadata::class.java.name)
    }

    /**
     * Opens a new editor or focuses an existing editor for the given [HasMetadata] and [Project].
     *
     * @param resource to edit
     * @param project that this editor belongs to
     * @return the new [ResourceEditor] that was opened
     */
    fun openEditor(resource: HasMetadata, project: Project) {
        runAsync {
            val file = getFile(resource, project) ?: return@runAsync
            file.putUserData(KEY_RESOURCE, resource)
            runInUI {
                // invokes editor selection listeners before call returns
                getFileEditorManager.invoke(project)
                    .openFile(file, true, true)
                    .firstOrNull()
                    ?: return@runInUI
            }
        }
    }

    private fun getFile(resource: HasMetadata, project: Project): VirtualFile? {
        val resourceEditor = getExisting(resource, project)
        return if (resourceEditor != null) {
            resourceEditor.editor.file
        } else {
            createResourceFile.invoke(resource)?.write(resource)
        }
    }

    private fun getExisting(resource: HasMetadata, project: Project): ResourceEditor? {
        return getFileEditorManager.invoke(project).allEditors
            .mapNotNull { editor ->
                getExisting(editor) }
            .firstOrNull { resourceEditor ->
                // get editor for a temporary file thus only editors for temporary files are candidates
                isTemporary.invoke(resourceEditor.editor.file)
                        && resourceEditor.isEditing(resource)
            }
    }

    /**
     * Returns the existing or creates a new [ResourceEditor] for the given [FileEditor] and [Project].
     * Returns `null` if the given [FileEditor] is `null`, not a [ResourceEditor] or the given [Project] is `null`.
     *
     * @return the existing or a new [ResourceEditor].
     */
    fun getExistingOrCreate(editor: FileEditor?, project: Project?): ResourceEditor? {
        if (editor == null
            || project == null) {
            return null
        }

        return getExisting(editor) ?: create(editor, project)
    }

    private fun create(editor: FileEditor, project: Project): ResourceEditor? {
        if (!isValidType.invoke(editor.file)
            || !hasKubernetesResource.invoke(editor, project)
        ) {
            return null
        }
        val telemetry = getTelemetryMessageBuilder().action(TelemetryService.NAME_PREFIX_EDITOR + "open")
      return try {
            runAsync { TelemetryService.sendTelemetry(getKubernetesResourceInfo(editor.file, project), telemetry) }
            val resourceEditor = createResourceEditor.invoke(editor, project)
            resourceEditor.createToolbar()
            getProjectManager.invoke().addProjectManagerListener(project, onProjectClosed(resourceEditor))
            editor.putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, resourceEditor)
            editor.file?.putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, resourceEditor)
            resourceEditor
        } catch (e: ResourceException) {
            ErrorNotification(editor, project).show(e.message ?: "", e.cause?.message)
            runAsync { telemetry.error(e).send() }
            null
        }
    }

    private fun onProjectClosed(resourceEditor: ResourceEditor): ProjectManagerListener {
        return object : ProjectManagerListener {
            override fun projectClosing(project: Project) {
                resourceEditor.close()
            }
        }
    }

    /** for testing purposes */
    protected open fun runAsync(runnable: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable)
    }

    /** for testing purposes */
    protected open fun runInUI(runnable: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runnable.invoke()
        } else {
            ApplicationManager.getApplication().invokeLater(runnable)
        }
    }

    /* for testing purposes */
    protected open fun getTelemetryMessageBuilder(): TelemetryMessageBuilder {
      return TelemetryService.instance;
    }
}
