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

import com.intellij.icons.AllIcons
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
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.JCheckBox
import javax.swing.tree.TreePath

class DeleteResourceAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val model = getResourceModel() ?: return
        val toDelete = selected?.map { it.getDescriptor()?.element as HasMetadata} ?: return
        val operation = userConfirms(toDelete)
        if (operation.isNo) {
            return
        }
        run("Deleting ${toMessage(toDelete, 30)}...", true,
            Progressive {
                val telemetry = TelemetryService.instance.action("delete resource")
                    .property(PROP_RESOURCE_KIND, getKinds(toDelete))
                try {
                    model.delete(toDelete, operation.isForce)
                    Notification().info("Resources Deleted", toMessage(toDelete, 30))
                    telemetry.success().send()
                } catch (e: MultiResourceException) {
                    val resources = e.causes.flatMap { it.resources }
                    Notification().error("Could not delete resource(s)", toMessage(resources, 30))
                    logger<DeleteResourceAction>().warn("Could not delete resources.", e)
                    telemetry.error(e).send()
                }
            })
    }

    private fun userConfirms(resources: List<HasMetadata>): DeleteOperation {
        val answer = Messages.showCheckboxMessageDialog(
            "Delete ${toMessage(resources, 30)}?",
            "Delete",
            arrayOf(Messages.getYesButton(), Messages.getNoButton()),
            "Force (immediate)",
            false,
            0,
            0,
            AllIcons.General.QuestionDialog,
            DeleteOperation.processDialogReturnValue)
        return DeleteOperation(answer)
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

  private class DeleteOperation(private val dialogReturn: Int) {

    companion object {
        const val FORCE_MASK = 0b1000000

        val processDialogReturnValue = { exitCode: Int, checkbox: JCheckBox ->
            if (exitCode == -1) {
                Messages.CANCEL
            } else {
                exitCode or (if (checkbox.isSelected) FORCE_MASK else 0)
            }
        }
    }

    val isForce: Boolean
        get() {
            return (dialogReturn and FORCE_MASK) == FORCE_MASK
        }

    val isNo: Boolean
        get() {
            return (dialogReturn and Messages.NO) == Messages.NO
        }
  }
}
