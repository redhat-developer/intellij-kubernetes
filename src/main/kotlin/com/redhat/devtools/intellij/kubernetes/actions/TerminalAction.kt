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
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.console.ConsolesToolWindow
import com.redhat.devtools.intellij.kubernetes.console.TerminalTab
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_RESOURCE_KIND
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.getKinds
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import javax.swing.tree.TreePath

class TerminalAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val project = event?.project ?: return
        val model = getResourceModel() ?: return
        val pods = selected?.map { it.getDescriptor()?.element as Pod } ?: return
        run("Opening Terminal to ${toMessage(pods, 30)}...", true,
            Progressive {
                val telemetry = TelemetryService.instance.action("open terminal")
                    .property(PROP_RESOURCE_KIND, getKinds(pods))
                try {
                    createTerminalTabs(pods, model, project)
                    telemetry.success().send()
                } catch (e: MultiResourceException) {
                    notify(e.causes.flatMap { it.resources }, e, telemetry)
                } catch (e: ResourceException) {
                    // WebSocketHandshakeException
                    notify(e.resources, e, telemetry)
                }
            })
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.any { isVisible(it) }
            ?: false
    }

    override fun isVisible(selected: Any?): Boolean {
        val resource = selected?.getElement<HasMetadata>()
        return resource != null
                    && true == getResourceModel()?.canWatchExec(resource)
    }

    private fun notify(
        resources: List<HasMetadata>,
        e: Exception,
        telemetry: TelemetryMessageBuilder.ActionMessage
    ) {
        Notification().error("Could not open terminal(s) to", toMessage(resources, 30))
        logger<TerminalAction>().warn("Could open terminal for ${toMessage(resources, -1)}", e)
        telemetry.error(e).send()
    }

    private fun createTerminalTabs(pods: List<Pod>, model: IResourceModel, project: Project) {
        pods.forEach { pod ->
            val tab = TerminalTab(pod, model, project)
            ConsolesToolWindow.add(tab, project)
        }
    }
}
