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
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.NAME_PREFIX_CONTEXT
import javax.swing.tree.TreePath

class SetAsCurrentClusterAction: StructureTreeAction(IContext::class.java) {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val context: IContext = selectedNode?.getElement() ?: return
        val telemetry = TelemetryService.instance
            .action(NAME_PREFIX_CONTEXT + "switch")
        run("Setting ${context.context.name} as current cluster...", true,
            Progressive {
                try {
                    getResourceModel()?.setCurrentContext(context)
                    telemetry.success().send()
                } catch (e: Exception) {
                    logger<SetAsCurrentClusterAction>().warn(
                        "Could not set current context to ${context.context.name}.", e
                    )
                    telemetry.error(e).send()
                }
            })
    }

    override fun isVisible(selected: Any?): Boolean {
        return false == selected?.getElement<IContext>()?.active
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.size == 1
                && isVisible(selected[0])
    }
}