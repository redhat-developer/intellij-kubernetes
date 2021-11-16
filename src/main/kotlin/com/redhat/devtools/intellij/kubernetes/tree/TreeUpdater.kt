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
 * An adapter that listens to events of the IKubernetesResourceModel and operates these changes on the
 * StructureTreeModel (to which the swing tree listens and updates accordingly)
 *
 * @see IResourceModel
 * @see StructureTreeModel
 */
class TreeUpdater<Structure : AbstractTreeStructure>(
    private val treeModel: StructureTreeModel<Structure>,
    private val structure: MultiParentTreeStructure,
    model: IResourceModel
) : ModelChangeObservable.IResourceChangeListener {

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
        invalidateNode(modified)
    }

    private fun invalidateParent(element: Any) {
        treeModel.invoker.invokeLater {
            val parents = getParentNodes(element).map { TreePath(it) }
            invalidatePaths(parents)
        }
    }

    private fun invalidateNode(element: Any) {
        treeModel.invoker.invokeLater {
            val nodes = getTreeNodes(element)
            nodes.forEach { path ->
                invalidatePath(path)
            }
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
        val all = mutableListOf<TreeNode>()
        getAllNodes(start, all)
        return all
    }

    private fun getAllNodes(start: TreeNode, allNodes: MutableList<TreeNode>) {
        for (child in start.children()) {
            allNodes.add(child)
            getAllNodes(child, allNodes)
        }
    }

    private fun findNodes(element: Any, start: TreeNode): Collection<TreeNode> {
        val nodes = mutableListOf<TreeNode>()
        findNodes(element, start, nodes)
        return nodes
    }

    private fun findNodes(element: Any, start: TreeNode, matchingNodes: MutableList<TreeNode>) {
        for (child in start.children()) {
            if (hasElement(element, child)) {
                matchingNodes.add(child)
            }
            findNodes(element, child, matchingNodes)
        }
    }

    private fun hasElement(element: Any, node: TreeNode): Boolean {
        return ((element is HasMetadata && element.isSameResource(node.getElement()))
                || element == node.getElement())
    }

    private fun getTreeNodes(element: Any?): List<TreePath> {
        return if (element == null) {
            return emptyList()
        } else if (isRootNode(element)) {
            listOf(TreePath(treeModel.root))
        } else {
            findNodes(element, treeModel.root)
                .map { TreePath(it) }
        }
    }

    private fun isRootNode(element: Any?): Boolean {
        val descriptor = (treeModel.root as? DefaultMutableTreeNode)?.userObject as? NodeDescriptor<*>
        return descriptor?.element == element
    }
}
