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

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.ui.Messages
import com.redhat.devtools.intellij.common.CommonConstants
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.toResource
import com.redhat.devtools.intellij.kubernetes.ui.FileUserData
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException

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
                logger<SaveListener>().debug(
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
            return lastModificationStamp != document.modificationStamp
        }

        private fun confirmSave(resource: HasMetadata, cluster: String?): Boolean {
            val answer = Messages.showYesNoDialog(
                "Save ${toMessage(resource, 30)} " +
                        "to namespace ${resource.metadata.name} " +
                        "${if (cluster != null) "on $cluster" else ""}?",
                "Save resource?",
                Messages.getQuestionIcon()
            )
            return answer == Messages.OK
        }

    }