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
package com.redhat.devtools.intellij.kubernetes

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.treeStructure.Tree
import com.redhat.devtools.intellij.common.compat.PopupHandlerAdapter
import com.redhat.devtools.intellij.common.tree.StructureTreeModelFactory
import com.redhat.devtools.intellij.kubernetes.actions.addDoubleClickListener
import com.redhat.devtools.intellij.kubernetes.actions.getElement
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorFactory
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure
import com.redhat.devtools.intellij.kubernetes.tree.TreeUpdater
import io.fabric8.kubernetes.api.model.HasMetadata
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JTree
import javax.swing.tree.MutableTreeNode


class KubernetesToolWindowFactory: ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val tree = createTree(project)
        val panel = ScrollPaneFactory.createScrollPane(tree)
        PopupHandlerAdapter.install(tree, "com.redhat.devtools.intellij.kubernetes.tree", ActionPlaces.UNKNOWN)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        toolWindow.contentManager.addContent(contentFactory.createContent(panel, "", false))
    }

    private fun createTree(project: Project): Tree {
        val resourceModel = ApplicationManager.getApplication().getService(IResourceModel::class.java)
        val structure = TreeStructure(project, resourceModel)
        val treeModel = StructureTreeModelFactory.create(structure, project)
        val tree = Tree(AsyncTreeModel(treeModel, project))
        tree.isRootVisible = false
        tree.cellRenderer = NodeRenderer()
        tree.addDoubleClickListener(openResourceEditor(project))
        TreeUpdater(treeModel, structure).listenTo(resourceModel)
        ResourceWatchController.install(tree)
        return tree
    }

    private fun openResourceEditor(project: Project): MouseListener {
        return object: MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val tree = event.source as JTree
                val point: Point = event.point
                val path = tree.getPathForLocation(point.x, point.y) ?: return
                val node = path.lastPathComponent as? MutableTreeNode ?: return
                val resource = node.getElement<HasMetadata>() ?: return
                ResourceEditorFactory.instance.openEditor(resource, project)
            }
        }
    }
}
