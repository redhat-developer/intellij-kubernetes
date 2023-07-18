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
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorFactory
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelectedFileEditor
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.KubernetesClientExceptionUtils
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService

class DiffAction : AnAction() {

    companion object {
        const val ID = "com.redhat.devtools.intellij.kubernetes.editor.actions.DiffAction"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fileEditor = getSelectedFileEditor(project) ?: return
        val telemetry = TelemetryService.instance.action(TelemetryService.NAME_PREFIX_EDITOR + "diff")
        com.redhat.devtools.intellij.kubernetes.actions.run(
            "Showing diff...", true,
            Progressive {
                val editor = ResourceEditorFactory.instance.getExistingOrCreate(fileEditor, project)
                    ?: return@Progressive
                editor.diff()
                    .thenApply {
                        TelemetryService.sendTelemetry(editor.getResources(), telemetry)
                    }
                    .exceptionally { completionException ->
                        val e = completionException.cause as? Exception
                            ?: completionException as? Exception
                            ?: return@exceptionally
                        logger<ResourceEditor>().warn("Could not open diff", e)
                        telemetry.error(e).send()
                        notify(e)
                    }
            })
    }

    private fun notify(e: Exception) {
        val message = trimWithEllipsis(e.message, 100)
        val causeMessage = KubernetesClientExceptionUtils.statusMessage(e.cause)
        Notification()
            .error(
                "Could not open diff",
                if (causeMessage == null) {
                    message ?: ""
                } else if (message == null) {
                    causeMessage
                } else {
                    "$message: $causeMessage"
                }
            )
    }
}
