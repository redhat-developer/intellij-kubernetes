/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_RESOURCE_KIND
import javax.swing.tree.TreePath

class OpenInBrowserAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val model = getResourceModel() ?: return
        val currentContext = model.getCurrentContext() ?: return
        run("Opening Dashboard...", true)
             {
                val telemetry = TelemetryService.instance.action("open in browser")
                    .property(PROP_RESOURCE_KIND, currentContext.name)
                try {
                    val url = currentContext.getDashboardUrl()
                    BrowserUtil.open(url.toString())
                    telemetry.success().send()
                } catch (e: Exception) {
                    logger<OpenInBrowserAction>().warn("Could not open in browser", e)
                    Notification().error("Could not open in Browser", e.message ?: "")
                    telemetry.error(e).send()
                }
            }
    }

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.any { isVisible(it) }
            ?: false
    }

    override fun isVisible(selected: Any?): Boolean {
        val context = selected?.getElement<IActiveContext<*,*>>()
        return context != null
    }
}
