/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
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
import com.redhat.devtools.intellij.kubernetes.logs.LogTab
import com.redhat.devtools.intellij.kubernetes.logs.LogsToolWindow
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_RESOURCE_KIND
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.getKinds
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.TreePath

class FollowLogsAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val project = event?.project ?: return
        val model = getResourceModel() ?: return
        val toFollow = selected?.map { it.getDescriptor()?.element as HasMetadata } ?: return
        run("Following logs of ${toMessage(toFollow, 30)}...", true,
            Progressive {
                val telemetry = TelemetryService.instance.action("follow logs")
                    .property(PROP_RESOURCE_KIND, getKinds(toFollow))
                try {
                    createLogTabs(toFollow, model, project)
                    telemetry.success().send()
                } catch (e: MultiResourceException) {
                    val failed = e.causes.flatMap { it.resources }
                    Notification().error("Could not follow log(s)", toMessage(failed, 30))
                    logger<ResourceWatchController>().warn("Could not follow logs", e)
                    telemetry.error(e).send()
                }
            })
    }

    private fun createLogTabs(resources: List<HasMetadata>, model: IResourceModel, project: Project) {
        resources.forEach { resource ->
            val tab = LogTab(resource, model, project)
            if (LogsToolWindow.add(tab, project)) {
                tab.watchLog()
            }
        }
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.any { isVisible(it) }
            ?: false
    }

    override fun isVisible(selected: Any?): Boolean {
        val resource = selected?.getElement<HasMetadata>()
        return resource != null
                    && true == getResourceModel()?.canFollowLogs(resource)
    }
}