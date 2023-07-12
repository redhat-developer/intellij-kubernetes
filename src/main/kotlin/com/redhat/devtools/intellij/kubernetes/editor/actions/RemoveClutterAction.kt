/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.Progressive
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorFactory
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelectedFileEditor
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService

class RemoveClutterAction: AnAction() {

    companion object {
        const val ID = "com.redhat.devtools.intellij.kubernetes.editor.actions.RemoveClutterAction"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fileEditor = getSelectedFileEditor(project) ?: return
        val telemetry = TelemetryService.instance.action(TelemetryService.NAME_PREFIX_EDITOR + "remove-clutter")
        com.redhat.devtools.intellij.kubernetes.actions.run("Removing metadata clutter...", true,
            Progressive {
                try {
                    val editor = ResourceEditorFactory.instance.getExistingOrCreate(fileEditor, project) ?: return@Progressive
                    editor.removeClutter()
                    TelemetryService.sendTelemetry(editor.getResources(), telemetry)
                } catch (e: Exception) {
                    logger<RemoveClutterAction>().warn("Could not remove clutter resource to cluster: ${e.message}", e)
                    Notification().error("Error removing metadata clutter", "Could not remove metadata clutter: ${e.message}")
                    telemetry.error(e).send()
                }
            })
    }

}