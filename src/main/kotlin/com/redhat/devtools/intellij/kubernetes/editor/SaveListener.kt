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
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException

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
        val projectEditor = getEditor(file) ?: return true
        if (!projectEditor.editor.isValid) {
            return false
        }
        try {
            val resource: HasMetadata = ResourceEditor.createResource(document.text) ?: return false
            val contextName = ResourceEditor.getContextName(projectEditor.editor, projectEditor.project) ?: return true
            if (confirmSaveToCluster(resource, contextName)) {
                saveToCluster(resource, contextName, projectEditor.editor, projectEditor.project)
            }
            return true
        } catch (e: KubernetesClientException) {
            val errorMessage = "Could not save ${file.name}: ${e.cause?.message}"
            Notification().error("Save to Cluster failed", errorMessage)
            logger<SaveListener>().warn(errorMessage, e.cause)
            return true
        }
    }

    private fun confirmSaveToCluster(resource: HasMetadata, contextName: String?): Boolean {
        val answer = Messages.showYesNoDialog(
            "Save ${toMessage(resource, 30)} "
                    + "${if (resource.metadata.namespace != null) "in namespace ${resource.metadata.namespace}" else "" } "
                    + "${if (contextName != null) "to $contextName" else ""}?",
            "Save Resource?",
            Messages.getQuestionIcon()
        )
        return answer == Messages.OK
    }

    private fun saveToCluster(resource: HasMetadata, contextName: String?, editor: FileEditor?, project: Project) {
        com.redhat.devtools.intellij.kubernetes.actions.run("Saving to cluster...", true,
            Progressive {
                try {
                    if (editor != null) {
                        val updatedResource = ResourceEditor.saveToCluster(resource, editor, project)
                        if (updatedResource != null) {
                            reloadEditor(updatedResource, editor, project)
                        }
                    }
                } catch (e: KubernetesClientException) {
                    val message = """${resource.metadata.name}
                            ${ if (resource.metadata.namespace != null) {"in namespace ${resource.metadata.namespace} "} else {""}} 
                            could not be saved to cluster $contextName:
                            ${extractDetails(e)}"""
                    logger<SaveListener>().warn(message, e)
                    Notification().error("Save to cluster failed", message)
                }
            })
    }

    private fun reloadEditor(updatedResource: HasMetadata?, editor: FileEditor?, project: Project) {
        if (updatedResource == null
            || editor == null) {
            return
        }
        ResourceEditor.reloadEditor(updatedResource, editor, project)
    }


    private fun getEditor(file: VirtualFile): ProjectAndEditor? {
        return ProjectManager.getInstance().openProjects
            .filter { project -> project.isInitialized && !project.isDisposed }
            .flatMap { project ->
                FileEditorManager.getInstance(project).getEditors(file).toList()
                    .mapNotNull { editor -> ProjectAndEditor(project, editor) }
            }
            .firstOrNull()
    }

    private fun extractDetails(e: KubernetesClientException): String? {
        if (e.message == null) {
            return null
        }
        val detailsIdentifier = "Message: "
        val detailsStart = e.message!!.indexOf(detailsIdentifier)
        if (detailsStart < 0) {
            return e.message
        }
        return e.message!!.substring(detailsStart + detailsIdentifier.length)
    }

    private class ProjectAndEditor(val project: Project, val editor: FileEditor)

}