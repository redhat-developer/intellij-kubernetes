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
package org.jboss.tools.intellij.kubernetes.actions

import com.intellij.icons.AllIcons.Actions.Refresh
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.treeStructure.Tree
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure

class RefreshAction: AnAction(Refresh) {

    override fun actionPerformed(event: AnActionEvent) {
        val tree: Tree = event.getTree()
        val element = tree.getSelectedNode()?.getDescriptor()?.element ?: return
        val model = getResourceModel(getEventProject(event)) ?: return
        invalidate(element, model)
    }

    private fun invalidate(element: Any, model: IResourceModel) {
        if (element is TreeStructure.Folder) {
            model.invalidate(element.kind)
        } else {
            model.invalidate(element)
        }
    }
}
