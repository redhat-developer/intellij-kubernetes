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
package org.jboss.tools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.StructureTreeModel
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ModelChangeObservable
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * An adapter that listens to events of the IKubernetesResourceModel and operates these changes on the
 * StructureTreeModel (to which the swing tree listens and updates accordingly)
 *
 * @see IResourceModel
 * @see StructureTreeModel
 */
class ResourceModelAdapter<Structure: AbstractTreeStructure>(
    private val treeModel: StructureTreeModel<Structure>,
    private val structure: AbstractTreeStructure,
    model: IResourceModel)
    : ModelChangeObservable.IResourceChangeListener {

    init {
        model.addListener(this)
    }

    override fun currentNamespace(namespace: String?) {
        invalidateRoot()
    }

    override fun removed(removed: Any) {
        invalidateParent(removed)
    }

    override fun added(added: Any) {
        invalidateParent(added)
    }

    override fun modified(modified: Any) {
        invalidatePath { getTreePath(modified) }
    }

    private fun invalidateParent(element: Any) {
        val parent = getParentElement(element)
        if (parent is Collection<*>) {
            invalidatePath(parent)
        } else {
            invalidatePath { getTreePath(parent) }
        }
    }

    private fun invalidatePath(parent: Collection<*>) {
        parent.forEach {
            if (it != null) {
                invalidatePath { getTreePath(it) }
            }
        }
    }

    private fun invalidatePath(pathSupplier: () -> TreePath) {
        treeModel.invoker.runOrInvokeLater {
            val path = pathSupplier()
            if (path.lastPathComponent == treeModel.root) {
                invalidateRoot()
            }
            treeModel.invalidate(path, true)
        }
    }

    private fun invalidateRoot() {
        treeModel.invalidate()
    }

    private fun getParentElement(element: Any): Any? {
        return structure.getParentElement(element)
    }

    private fun getTreePath(element: Any?): TreePath {
        val path =
            if (isRootNode(element)) {
                TreePath(treeModel.root)
            } else {
                findTreePath(element, treeModel.root as? DefaultMutableTreeNode)
            }
        return path ?: TreePath(treeModel.root)
    }

    private fun isRootNode(element: Any?): Boolean {
        val descriptor = (treeModel.root as? DefaultMutableTreeNode)?.userObject as? NodeDescriptor<*>
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
        val descriptor = node.userObject as? NodeDescriptor<*> ?: return false
        return if (descriptor is TreeStructure.Descriptor) {
            descriptor.isMatching(element)
        } else {
            descriptor.element == element
        }
    }
}
