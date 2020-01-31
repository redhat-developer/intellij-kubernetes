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
package org.jboss.tools.intellij.kubernetes.actions

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import org.jboss.tools.intellij.kubernetes.model.IKubernetesResourceModel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode


fun AnActionEvent.getTree(): Tree {
    return this.getData(PlatformDataKeys.CONTEXT_COMPONENT) as Tree
}

fun AnActionEvent.getSelectedNode(): DefaultMutableTreeNode? {
    return getTree()?.getSelectedNode()
}

fun AnAction.getResourceModel(project: Project?): IKubernetesResourceModel? {
    if (project == null) {
        return null
    }
    return ServiceManager.getService(project, IKubernetesResourceModel::class.java)
}

fun JTree.getSelectedNode(): DefaultMutableTreeNode? {
    return this.selectionModel.selectionPath?.lastPathComponent as DefaultMutableTreeNode
}

fun DefaultMutableTreeNode.getDescriptorElement(): Any? {
    return (this.userObject as NodeDescriptor<*>).element
}
