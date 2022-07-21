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
package com.redhat.devtools.intellij.kubernetes.console

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.redhat.devtools.intellij.common.utils.UIHelper.executeInUI
import java.util.function.Supplier

object ConsolesToolWindow {

    const val ID = "Kubernetes Consoles"

    fun add(tab: ConsoleTab<*, *>, project: Project): Boolean {
        return executeInUI(Supplier {
            var added = false
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID)
            if (toolWindow != null) {
                val existing = toolWindow.contentManager.findContent(tab.getDisplayName())
                if (existing == null) {
                    val content = createContent(tab)
                    addContent(toolWindow, content)
                    added = true
                }
                selectTab(tab.getDisplayName(), toolWindow.contentManager)
                ensureOpened(toolWindow)
            }
            added
        })
    }

    private fun createContent(tab: ConsoleTab<*, *>): Content {
        val content = ContentFactory.SERVICE.getInstance().createContent(
            tab.createComponent(),
            tab.getDisplayName(),
            true
        )
        Disposer.register(content, tab)
        content.isCloseable = true
        return content
    }

    private fun addContent(toolWindow: ToolWindow, content: Content) {
        executeInUI {
            toolWindow.contentManager.addContent(content)
        }
    }

    private fun selectTab(tabName: String, manager: ContentManager) {
        executeInUI {
            val content = manager.findContent(tabName)
            manager.setSelectedContent(content)
        }
    }

    private fun ensureOpened(toolWindow: ToolWindow) {
        executeInUI {
            if (toolWindow.isVisible
                && toolWindow.isActive
                && toolWindow.isAvailable
            ) {
                return@executeInUI
            }
            toolWindow.setToHideOnEmptyContent(true)
            toolWindow.setAvailable(true, null)
            toolWindow.activate(null)
            toolWindow.show(null)
        }
    }
}