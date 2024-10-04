/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
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
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionViewerFactory
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_RESOURCE_KIND
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import javax.swing.tree.TreePath

class DescribeResourceAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val descriptor = selected?.get(0)?.getDescriptor() ?: return
        val project = descriptor.project ?: return
        val toDescribe: HasMetadata = descriptor.element as? HasMetadata? ?: return
        run("Describe ${toMessage(toDescribe, 30)}...", true,
            Progressive {
                val telemetry = TelemetryService.instance.action("describe resource")
                    .property(PROP_RESOURCE_KIND, toDescribe.kind)
                try {
                    DescriptionViewerFactory.instance.openEditor(toDescribe, project)
                    telemetry.success().send()
                } catch (e: RuntimeException) {
                    logger<DescribeResourceAction>().warn("Error opening editor ${toDescribe.metadata.name}", e)
                    Notification().error(
                        "Error opening editor ${toDescribe.metadata.name}",
                        "Could not open editor for ${toDescribe.kind} '${toDescribe.metadata.name}'."
                    )
                    telemetry.error(e).send()
                }
            }
        )
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.size == 1
                && isVisible(selected.firstOrNull())
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        return element is Pod
    }
}