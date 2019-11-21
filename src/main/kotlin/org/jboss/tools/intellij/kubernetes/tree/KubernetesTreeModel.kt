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
package org.jboss.tools.intellij.kubernetes.tree

import com.intellij.ui.tree.BaseTreeModel
import org.jboss.tools.intellij.kubernetes.tree.nodes.ConnectionRootNode
import javax.swing.tree.DefaultMutableTreeNode


class KubernetesTreeModel: BaseTreeModel<Any>() {

    var root = ConnectionRootNode()

    override fun getRoot(): Any {
        return root
    }

    override fun getChildren(parent: Any?): List<Any> {
        if (parent is DefaultMutableTreeNode) {
            return parent.children().toList();
        } else {
            return emptyList()
        }
    }
}
