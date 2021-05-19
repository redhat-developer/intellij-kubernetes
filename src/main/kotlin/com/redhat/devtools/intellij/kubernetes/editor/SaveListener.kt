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
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listener that gets called when an editor is saved.
 * It saves the resource in the editor to the (kubeconfig-) context the editor is bound to.
 */
class SaveListener : FileDocumentSynchronizationVetoer() {

    override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
        if (!isSaveExplicit) {
            return true
        }
        return saveToCluster(document)
    }

    private fun saveToCluster(document: Document): Boolean {
        val file = ResourceEditor.getResourceFile(document) ?: return true
        val projectAndEditor = getProjectAndEditor(file) ?: return true
        if (!projectAndEditor.editor.isValid) {
            return false
        }
        val resource: HasMetadata = ResourceEditor.createResource(document.text) ?: return false
        saveToCluster(resource, projectAndEditor.editor, projectAndEditor.project)
        return true
    }

    private fun saveToCluster(resource: HasMetadata, editor: FileEditor, project: Project) {
        com.redhat.devtools.intellij.kubernetes.actions.run("Saving to cluster...", true,
            Progressive {
                try {
                    ResourceEditor.saveToCluster(resource, editor, project)
                } catch (e: ResourceException) {
                    logger<SaveListener>().warn(e)
                    Notification().error(
                        "Could not save ${resource.kind} ${resource.metadata.name} to cluster",
                    trimWithEllipsis(e.message, 300) ?: "")
                }
            })
    }

    private fun getProjectAndEditor(file: VirtualFile): ProjectAndEditor? {
        return ProjectManager.getInstance().openProjects
            .filter { project -> project.isInitialized && !project.isDisposed }
            .flatMap { project ->
                FileEditorManager.getInstance(project).getEditors(file).toList()
                    .mapNotNull { editor -> ProjectAndEditor(project, editor) }
            }
            .firstOrNull()
    }

    private class ProjectAndEditor(val project: Project, val editor: FileEditor)

}