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

import com.intellij.icons.AllIcons.Actions.Refresh
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.treeStructure.Tree
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourceModel
import javax.swing.tree.DefaultMutableTreeNode

class RefreshAction: AnAction(Refresh) {

    override fun actionPerformed(event: AnActionEvent?) {
        val tree: Tree = getTree(event)
        val selectedNode = tree.selectionModel.selectionPath?.lastPathComponent
        val modelObject = getDescriptorElement(selectedNode)
        KubernetesResourceModel.instance.refresh(modelObject)
    }

    private fun getTree(event: AnActionEvent?): Tree {
        return event?.getData(PlatformDataKeys.CONTEXT_COMPONENT) as Tree
    }

    private fun getDescriptorElement(node: Any?): Any? {
        if (node !is DefaultMutableTreeNode) {
            return null
        }
        return (node.userObject as NodeDescriptor<*>).element
    }

}