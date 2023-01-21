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
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import com.redhat.devtools.intellij.kubernetes.tree.util.getResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import javax.swing.tree.TreePath


class CopyNameAction : StructureTreeAction(Any::class.java) {
    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val descriptor = selectedNode?.getDescriptor() ?: return
        val element = descriptor.getElement<HasMetadata>() ?: return
        val kind = getResourceKind(descriptor.element)
        run("Copying name of $selectedNode...", true) {
            val telemetry = TelemetryService.instance.action("start copy name of " + kind?.kind)
            try {
                val selection = StringSelection(element.metadata.name)
                val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(selection, null)
                TelemetryService.sendTelemetry(kind, telemetry)
            } catch (e: Exception) {
                logger<ResourceWatchController>().warn("Could not copy name of $descriptor resources.", e)
                telemetry.error(e).send()
            }
        }
    }
}