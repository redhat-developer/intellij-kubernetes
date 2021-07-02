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
package com.redhat.devtools.intellij.kubernetes.editor.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.ui.MessageDialogBuilder
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.util.getFileEditor
import com.redhat.devtools.intellij.kubernetes.model.Notification
import java.util.function.Supplier

class PushAction: AnAction() {

    companion object {
        const val ID = "com.redhat.devtools.intellij.kubernetes.editor.action.PushAction"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.dataContext.getData(CommonDataKeys.PROJECT) ?: return
        val editor = getFileEditor(project)
        com.redhat.devtools.intellij.kubernetes.actions.run("Pushing...", true,
            Progressive {
                try {
                    val resourceEditor = ResourceEditor.get(editor, project)
                    if (resourceEditor != null
                        && resourceEditor.existsOnCluster()
                        && UIHelper.executeInUI(Supplier {
                            MessageDialogBuilder.yesNo(
                                "Overwrite Cluster",
                                "Overwrite resource on cluster?"
                            ).isYes
                        })
                    ) {
                        ResourceEditor.get(editor, project)?.push()
                    }
                } catch (e: Exception) {
                    ResourceEditor.get(editor, project)
                    Notification().error("Error Pushing", "Could not push resource to cluster: ${e.message}")
                }
            })
    }
}