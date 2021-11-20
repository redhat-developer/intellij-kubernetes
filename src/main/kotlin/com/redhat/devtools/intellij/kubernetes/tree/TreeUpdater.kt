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
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreePathUtil
import com.redhat.devtools.intellij.kubernetes.actions.getDescriptor
import com.redhat.devtools.intellij.kubernetes.actions.getElement
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * An adapter that listens to events of the IKubernetesResourceModel and invalidates or updates
 * nodes, descriptors accordingly.
 *
 * @see IResourceModel
 * @see StructureTreeModel
 */
class TreeUpdater<Structure : AbstractTreeStructure>(
    private val treeModel: StructureTreeModel<Structure>,
    private val structure: TreeStructure,
    model: IResourceModel
) : ModelChangeObservable.IResourceChangeListener {

    init {
        model.addListener(this)
    }

    override fun currentNamespace(namespace: String?) {
        treeModel.invoker.invokeLater {
            invalidateRoot()
        }
    }

    override fun removed(removed: Any) {
        treeModel.invoker.invokeLater {
            invalidateParent(removed)
        }
    }

    override fun added(added: Any) {
        treeModel.invoker.invokeLater {
            invalidateParent(added)
        }
    }

    override fun modified(modified: Any) {
        treeModel.invoker.invokeLater {
            invalidateNode(modified)
        }
    }

    private fun invalidateParent(element: Any) {
        val parents = getParentNodes(element).map { TreePathUtil.toTreePath(it) }
        invalidatePaths(parents)
    }

    private fun invalidateNode(element: Any) {
        val paths = getPaths(element)

        if (paths.isEmpty()) {
            return
        }
        // update node
        updateDescriptors(paths, element)
        // cause reload of children
        invalidatePaths(paths)
    }

    private fun updateDescriptors(paths: List<TreePath>, element: Any) {
        paths.forEach { path ->
            val descriptor = TreePathUtil.toTreeNode(path)?.getDescriptor() ?: return
            descriptor.setElement(element)
            descriptor.update()
        }
    }


    private fun invalidatePaths(paths: Collection<TreePath>) {
        paths.forEach { path ->
            invalidatePath(path)
        }
    }

    private fun invalidatePath(path: TreePath) {
        if (path.lastPathComponent == treeModel.root) {
            invalidateRoot()
        } else {
            treeModel.invalidate(path, true)
        }
    }

    private fun getPaths(element: Any?): List<TreePath> {
        return if (element == null) {
            return emptyList()
        } else if (isRootNode(element)) {
            listOf(TreePath(treeModel.root))
        } else {
            findNodes({ node: TreeNode -> hasElement(element, node) }, treeModel.root)
                .map { node -> TreePathUtil.pathToTreeNode(node) }
        }
    }

    private fun isRootNode(element: Any?): Boolean {
        val descriptor = (treeModel.root as? DefaultMutableTreeNode)?.userObject as? NodeDescriptor<*>
        return descriptor?.element == element
    }

    private fun invalidateRoot() {
        treeModel.invalidate()
    }

    private fun getParentNodes(element: Any): Collection<TreeNode> {
        val rootNode = treeModel.root
        if (true == rootNode.getDescriptor()?.hasElement(element)) {
            return listOf(rootNode)
        }

        return getAllNodes(rootNode)
            .filter { node -> structure.isParentDescriptor(node.getDescriptor(), element) }
    }

    private fun getAllNodes(start: TreeNode): Collection<TreeNode> {
        return findNodes({ true }, start)
    }

    private fun findNodes(condition: (child: TreeNode) -> Boolean, start: TreeNode): Collection<TreeNode> {
        val nodes = mutableListOf<TreeNode>()
        findNodes(condition, start, nodes)
        return nodes
    }

    private fun findNodes(condition: (child: TreeNode) -> Boolean, start: TreeNode, matchingNodes: MutableList<TreeNode>) {
        for (child in start.children()) {
            if (condition.invoke(child)) {
                matchingNodes.add(child)
            }
            findNodes(condition, child, matchingNodes)
        }
    }

    private fun hasElement(element: Any, node: TreeNode): Boolean {
        return (element is HasMetadata && element.isSameResource(node.getElement())
                || element == node.getElement())
    }
}
