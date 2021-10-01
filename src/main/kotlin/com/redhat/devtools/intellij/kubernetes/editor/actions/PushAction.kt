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
package com.redhat.devtools.intellij.kubernetes.editor.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.Progressive
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.util.getFileEditor
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.NAME_PREFIX_EDITOR
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.reportResource

class PushAction: AnAction() {

    companion object {
        const val ID = "com.redhat.devtools.intellij.kubernetes.editor.actions.PushAction"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.dataContext.getData(CommonDataKeys.PROJECT) ?: return
        val editor = getFileEditor(project)
        val telemetry = TelemetryService.instance.action(NAME_PREFIX_EDITOR + "push")
        com.redhat.devtools.intellij.kubernetes.actions.run("Pushing...", true,
            Progressive {
                try {
                    val editor = ResourceEditor.get(editor, project) ?: return@Progressive
                    editor.push()
                    reportResource(editor.editorResource, telemetry)
                } catch (e: Exception) {
                    Notification().error("Error Pushing", "Could not push resource to cluster: ${e.message}")
                    telemetry.error(e).send()
                }
            })
    }
}