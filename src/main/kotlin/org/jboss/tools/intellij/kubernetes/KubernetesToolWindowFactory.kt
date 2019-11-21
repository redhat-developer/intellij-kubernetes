/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBDefaultTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import org.jboss.tools.intellij.kubernetes.tree.KubernetesTreeModel
import java.awt.BorderLayout

class KubernetesToolWindowFactory: ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BorderLayout()
        val model = KubernetesTreeModel()
        model.set
        val tree = Tree(kubernetesTreeModel)
        tree.cellRenderer = JBDefaultTreeCellRenderer()
        PopupHandler.installPopupHandler(tree, "org.jboss.tools.intellij.kubernetes.tree", ActionPlaces.UNKNOWN)
        panel.add(tree, BorderLayout.PAGE_START)
        var contentFactory = ContentFactory.SERVICE.getInstance()
        toolWindow.contentManager.addContent(contentFactory.createContent(panel, "", false))
    }
}
