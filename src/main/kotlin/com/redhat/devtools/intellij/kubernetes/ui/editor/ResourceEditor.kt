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

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.CommonConstants
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.sameRevision
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.toResource
import com.redhat.devtools.intellij.kubernetes.ui.FileUserData
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.log4j.lf5.util.ResourceUtils
import org.slf4j.LoggerFactory
import java.io.IOException

object ResourceEditor {

    private val KEY_RESOURCE = Key<HasMetadata>("RESOURCE")
    private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)

    @Throws(IOException::class)
    fun open(project: Project, resource: HasMetadata) {
        val file = ResourceEditorFile.get(resource)
        FileUserData(file)
            .put(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true);
        val editor = openEditor(file, project)
        putUserDataResource(resource, editor)
    }

    private fun openEditor(virtualFile: VirtualFile?, project: Project): FileEditor? {
        if (virtualFile == null) {
            return null
        }
        val editors = FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
        return editors.getOrNull(0)
    }

    fun getUserDataResource(editor: FileEditor): HasMetadata? {
        return editor?.getUserData(KEY_RESOURCE)
    }

    fun putUserDataResource(resource: HasMetadata?, editor: FileEditor?) {
        editor?.putUserData(KEY_RESOURCE, resource)
    }

    class ManagerListener(private val project: Project) : FileEditorManagerListener {

        private val model = ServiceManager.getService(IResourceModel::class.java)

        override fun selectionChanged(event: FileEditorManagerEvent) {
            if (event.newEditor == null
                || !ResourceEditorFile.matches(event.newFile)
            ) {
                return
            }
            val editor = event.newEditor!!
            val resource = getResource(editor) ?: return
            val latestRevision = model.resource(resource) ?: return
            if (!resource.sameRevision(latestRevision)) {
                ReloadNotification.show(editor, resource, project)
            }
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            WriteAction.compute<Unit, Exception> { file.delete(this) }
        }

        private fun getResource(editor: FileEditor): HasMetadata? {
            var resource = getUserDataResource(editor)
            if (resource == null
                && editor.file != null
            ) {
                val document = FileDocumentManager.getInstance().getDocument(editor.file!!)
                if (document?.text != null) {
                    resource = Serialization.unmarshal(document.text, HasMetadata::class.java)
                    putUserDataResource(resource, editor)
                }
            }
            return resource
        }
    }

    class SaveListener : FileDocumentSynchronizationVetoer() {

        override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
            if (!isSaveExplicit) {
                return true
            }
            val file = FileDocumentManager.getInstance().getFile(document) ?: return true
            val fileData = FileUserData(file)
            if (!isModified(document, fileData)) {
                return true
            }
            try {
                val resource: HasMetadata? = toResource(document.text)
                if (resource == null) {
                    Notification().error(
                        "Invalid content",
                        "Could not parse ${file.presentableUrl}. Only valid Json or Yaml supported."
                    )
                    return true
                }
                val resourceModel = ServiceManager.getService(IResourceModel::class.java) ?: return true
                val cluster = resourceModel.getCurrentContext()?.masterUrl?.toString()
                if (confirmSave(resource, cluster)) {
                    save(resource, resourceModel, fileData, document.modificationStamp, cluster)
                }
                return true
            } catch (e: KubernetesClientException) {
                logger<ResourceUtils>().debug(
                    "Could not parse ${file.presentableUrl}. Only valid Json or Yaml supported.",
                    e.cause
                )
                return true
            }
        }

        private fun save(
            resource: HasMetadata,
            resourceModel: IResourceModel,
            fileData: FileUserData,
            modificationStamp: Long,
            cluster: String?
        ) {
            com.redhat.devtools.intellij.kubernetes.actions.run("Saving to cluster...", true,
                Progressive {
                    try {
                        //Executors.newCachedThreadPool().submit { resourceModel.createOrReplace(resource) }
                        resourceModel.replace(resource)
                        fileData.put(CommonConstants.LAST_MODIFICATION_STAMP, modificationStamp)
                    } catch (e: MultiResourceException) {
                        val message = "Could not save ${resource.metadata.name} to cluster at $cluster"
                        logger<SaveListener>().error(message, e)
                        Notification().error("Save Error", message)
                    }
                })
        }

        private fun isModified(document: Document, fileData: FileUserData): Boolean {
            val lastModificationStamp = fileData.get(CommonConstants.LAST_MODIFICATION_STAMP) ?: return true
            return lastModificationStamp == null
                    || lastModificationStamp != document.modificationStamp
        }

        private fun confirmSave(resource: HasMetadata, cluster: String?): Boolean {
            val answer = Messages.showYesNoDialog(
                "Save ${toMessage(resource, 30)} ${if (cluster != null) "to $cluster" else ""}?",
                "Save resource?",
                Messages.getQuestionIcon()
            )
            return answer == Messages.OK
        }

    }
}

