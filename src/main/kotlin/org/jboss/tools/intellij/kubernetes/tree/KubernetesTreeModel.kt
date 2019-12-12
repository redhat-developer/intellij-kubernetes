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

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.StructureTreeModel
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceChangedObservableImpl
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class KubernetesTreeModel: StructureTreeModel(true) {

    private var treeStructure: AbstractTreeStructure? = null

    init {
        KubernetesResourceModel.addListener(object: ResourceChangedObservableImpl.ResourceChangeListener {
            override fun removed(removed: Any) {
                invalidatePath { getTreePath(getParentElement(removed)) }
            }

            override fun added(added: Any) {
                invalidatePath { getTreePath(getParentElement(added)) }
            }

            override fun modified(modified: Any) {
                invalidatePath { getTreePath(modified) }
            }
        })
    }

    override fun setStructure(structure: AbstractTreeStructure?) {
        super.setStructure(structure)
        this.treeStructure = structure
    }

    private fun invalidatePath(pathSupplier: () -> TreePath) {
        invoker.invokeLaterIfNeeded {
            val path = pathSupplier()
            if (path.lastPathComponent == root) {
                invalidateRoot()
            }
            invalidate(path, true)
        }
    }

    private fun invalidateRoot() {
        invalidate(null)
    }

    private fun getParentElement(element: Any): Any? {
        return treeStructure?.getParentElement(element)
    }

    private fun getTreePath(element: Any?): TreePath {
        val path =
            if (isRootNode(element)) {
                TreePath(root)
            } else {
                findTreePath(element, root as? DefaultMutableTreeNode)
            }
        return path ?: TreePath(root)
    }

    private fun isRootNode(element: Any?): Boolean {
        val descriptor = (root as? DefaultMutableTreeNode)?.userObject as? NodeDescriptor<*>
        return descriptor?.element == element
    }

    private fun findTreePath(element: Any?, start: DefaultMutableTreeNode?): TreePath? {
        if (element == null
            || start == null) {
            return null;
        }
        for (child in start.children()) {
            if (child !is DefaultMutableTreeNode) {
                continue
            }
            if (hasElement(element, child)) {
                return TreePath(child.path);
            }
            val path = findTreePath(element, child);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private fun hasElement(element: Any?, node: DefaultMutableTreeNode): Boolean {
        val descriptor = node.userObject as? NodeDescriptor<*>
        return descriptor?.element == element
    }

    /*
     * using treewalker
     *
        private fun invalidate(element: HasMetadata, consumer: Consumer<DefaultMutableTreeNode>) {
        invoker.invokeLater {
            val visitor: TreeVisitor = object: ByComponent<HasMetadata, DefaultMutableTreeNode>(element, DefaultMutableTreeNode::class.java) {
                override fun matches(pathComponent: DefaultMutableTreeNode, thisComponent: HasMetadata): Boolean {
                    return getElement(pathComponent) == thisComponent
                }

                override fun contains(pathComponent: DefaultMutableTreeNode, thisComponent: HasMetadata): Boolean {
                    return true
                }

                private fun getElement(node: DefaultMutableTreeNode): Any {
                    return (node.userObject as NodeDescriptor<*>).element
                }
            }
            val walker: AbstractTreeWalker<DefaultMutableTreeNode> = object : AbstractTreeWalker<DefaultMutableTreeNode>(visitor) {
                    public override fun getChildren(pathComponent: DefaultMutableTreeNode): Collection<DefaultMutableTreeNode> {
                        val childrenArray = pathComponent.children()
                        return childrenArray.toList() as List<DefaultMutableTreeNode>
                    }
                }
            walker.start(root as DefaultMutableTreeNode)
            walker.promise().onSuccess{ consumer }
        }
    }
     */
}