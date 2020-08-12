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
package org.jboss.tools.intellij.kubernetes

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.treeStructure.Tree
import com.redhat.devtools.intellij.common.tree.StructureTreeModelFactory
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure
import org.jboss.tools.intellij.kubernetes.tree.TreeUpdater

class KubernetesToolWindowFactory: ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val resourceModel = ServiceManager.getService(IResourceModel::class.java)
        val structure = TreeStructure(resourceModel)
        val treeModel = StructureTreeModelFactory.create(structure, project)
        TreeUpdater(treeModel, structure, resourceModel)
        val tree = Tree(AsyncTreeModel(treeModel))
        tree.isRootVisible = false
        tree.cellRenderer = NodeRenderer()
        val panel = ScrollPaneFactory.createScrollPane(tree)
        PopupHandler.installPopupHandler(tree, "org.jboss.tools.intellij.kubernetes.tree", ActionPlaces.UNKNOWN)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        toolWindow.contentManager.addContent(contentFactory.createContent(panel, "", false))
    }
}