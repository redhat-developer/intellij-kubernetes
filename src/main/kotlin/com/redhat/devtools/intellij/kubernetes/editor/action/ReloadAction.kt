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
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.util.getFileEditor
import com.redhat.devtools.intellij.kubernetes.model.Notification

class ReloadAction: AnAction() {

    companion object {
        const val ID = "com.redhat.devtools.intellij.kubernetes.editor.action.ReloadAction"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.dataContext.getData(CommonDataKeys.PROJECT) ?: return
        val editor = getFileEditor(project)
        com.redhat.devtools.intellij.kubernetes.actions.run("Reloading...", true,
            Progressive {
                try {
                    ResourceEditor.get(editor, project)?.replaceContent()
                } catch (e: Exception) {
                    ResourceEditor.get(editor, project)
                    Notification().error("Error Loading", "Could not load resource from cluster: ${e.message}")
                }
            })
    }
}