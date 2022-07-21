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
package com.redhat.devtools.intellij.kubernetes.tree.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.treeStructure.Tree
import com.redhat.devtools.intellij.kubernetes.actions.getElement
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JTree
import javax.swing.SwingUtilities

fun getTree(e: AnActionEvent?): Tree? {
    val component = e?.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return null
    return if (component is Tree) {
        component
    } else {
        null
    }
}

inline fun <reified T> getSelectedElement(tree: Tree?): T? {
    if (tree == null) {
        return null
    }
    return tree.lastSelectedPathComponent?.getElement()
}

fun JTree.addDoubleClickListener(listener: MouseListener) {
    this.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) {
            if (event.source !is JTree
                || 2 != event.clickCount
                || !SwingUtilities.isLeftMouseButton(event)
            ) {
                return
            }
            listener.mouseClicked(event)
        }
    })
}
