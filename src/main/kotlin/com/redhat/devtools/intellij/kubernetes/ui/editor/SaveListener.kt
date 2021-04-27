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
package com.redhat.devtools.intellij.kubernetes.ui.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.toResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException

class SaveListener : FileDocumentSynchronizationVetoer() {

    override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
        if (!isSaveExplicit) {
            return true
        }
        val file = ResourceEditor.getResourceFile(document) ?: return true
        try {
            val resource: HasMetadata? = toResource(document.text)
            if (resource == null) {
                Notification().error(
                    "Invalid content",
                    "Could not parse ${file.presentableUrl}. Only valid Json or Yaml supported."
                )
                return true
            }
            val projectEditor = getEditor(file) ?: return true
            val contextName = ResourceEditor.getContextName(projectEditor.second, projectEditor.first) ?: return true
            if (confirmSave(resource, contextName)) {
                save(resource, contextName, projectEditor.second, projectEditor.first)
            }
            return true
        } catch (e: KubernetesClientException) {
            logger<SaveListener>().debug(
                "Could not parse ${file.presentableUrl}. Only valid Json or Yaml supported.",
                e.cause
            )
            return true
        }
    }

    private fun confirmSave(resource: HasMetadata, contextName: String?): Boolean {
        val answer = Messages.showYesNoDialog(
            "Save ${toMessage(resource, 30)} " +
                    "in namespace ${resource.metadata.name} " +
                    "${if (contextName != null) "to $contextName" else ""}?",
            "Save resource?",
            Messages.getQuestionIcon()
        )
        return answer == Messages.OK
    }

    private fun save(resource: HasMetadata, contextName: String?, editor: FileEditor?, project: Project) {
        com.redhat.devtools.intellij.kubernetes.actions.run("Saving to cluster...", true,
            Progressive {
                try {
                    val updatedResource = ResourceEditor.replaceResource(resource, editor, project)
                    if (updatedResource != null) {
                        val file = editor?.file
                        if (file != null) {
                            ApplicationManager.getApplication().invokeAndWait {
                                ResourceEditor.replaceFile(updatedResource, file, project)
                            }
                        }
                    }
                } catch (e: KubernetesClientException) {
                    val message = "Could not save ${resource.metadata.name} to cluster at $contextName"
                    logger<SaveListener>().warn(message, e)
                    Notification().error("Could not Save", message)
                }
            })
    }


    private fun getEditor(file: VirtualFile): Pair<Project, FileEditor>? {
        return ProjectManager.getInstance().openProjects
            .filter { project -> project.isInitialized && !project.isDisposed }
            .flatMap { project ->
                FileEditorManager.getInstance(project).getEditors(file).toList()
                    .mapNotNull { Pair<Project, FileEditor>(project, it) }
            }
            .firstOrNull()
    }

}