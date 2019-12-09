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

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.StructureTreeModel
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceChangedObservableImpl
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class KubernetesTreeModel: StructureTreeModel(true) {
    init {
        KubernetesResourceModel.addListener(object: ResourceChangedObservableImpl.ResourceChangeListener {
            override fun removed(removed: List<Any>) {
            }

            override fun added(removed: List<Any>) {
            }

            override fun modified(modified: List<Any>) {
                modified.forEach {
                    invalidate(it)
                };
            }
        })
    }

    private fun invalidate(element: Any) {
        invoker.invokeLaterIfNeeded {
            val path = getTreePath(element)
            if (path.lastPathComponent == root) {
                // invalidate root
                invalidate({})
            }
            invalidate(path, true)
        }
    }

    private fun getTreePath(element: Any): TreePath {
        return when (element) {
            is NamespacedKubernetesClient
                -> TreePath(root)
            is HasMetadata
                -> findTreePath(element, root as DefaultMutableTreeNode) ?: TreePath(root)
            else
                -> TreePath(root)
        }
    }

    private fun findTreePath(element: HasMetadata, start: DefaultMutableTreeNode?): TreePath? {
        if (start == null) {
            return null;
        }
        for (child in start.children()) {
            if (child !is DefaultMutableTreeNode) {
                continue
            }
            if (hasElement(element, child)) {
                return TreePath(child.path);
            }
            val path = findTreePath(element, child as? DefaultMutableTreeNode);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private fun hasElement( element: Any?, node: DefaultMutableTreeNode): Boolean {
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