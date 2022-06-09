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
import com.intellij.openapi.ui.Messages
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.hasDeletionTimestamp
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_RESOURCE_KIND
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.getKinds
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.TreePath

class DeleteResourceAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val model = getResourceModel() ?: return
        val toDelete = selected?.map { it.getDescriptor()?.element as HasMetadata} ?: return
        if (!userConfirmed(toDelete)) {
            return
        }
        run("Deleting ${toMessage(toDelete, 30)}...", true,
            Progressive {
                val telemetry = TelemetryService.instance.action("delete resource")
                    .property(PROP_RESOURCE_KIND, getKinds(toDelete))
                try {
                    model.delete(toDelete)
                    Notification().info("Resources Deleted", toMessage(toDelete, 30))
                    telemetry.success().send()
                } catch (e: MultiResourceException) {
                    val resources = e.causes.flatMap { it.resources }
                    Notification().error("Could not delete resource(s)", toMessage(resources, 30))
                    logger<ResourceWatchController>().warn("Could not delete resources.", e)
                    telemetry.error(e).send()
                }
            })
    }

    private fun userConfirmed(resources: List<HasMetadata>): Boolean {
        val answer = Messages.showYesNoDialog(
            "Delete ${toMessage(resources, 30)}?",
            "Delete resources?",
            Messages.getQuestionIcon())
        return answer == Messages.OK
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.any { isVisible(it) }
            ?: false
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        return element != null
                    && !hasDeletionTimestamp(element)
    }
}