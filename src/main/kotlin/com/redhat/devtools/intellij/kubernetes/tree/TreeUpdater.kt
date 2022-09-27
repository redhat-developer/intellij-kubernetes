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
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreePathUtil
import com.redhat.devtools.intellij.kubernetes.actions.getDescriptor
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
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
class TreeUpdater(
    private val treeModel: StructureTreeModel<AbstractTreeStructure>,
    private val structure: TreeStructure
) : IResourceModelListener, Disposable {

    private var model: IResourceModel? = null

    init {
        Disposer.register(treeModel, this)
    }

    fun listenTo(model: IResourceModel): TreeUpdater {
        model.addListener(this)
        this.model = model
        return this
    }

    override fun currentNamespaceChanged(new: IActiveContext<*, *>?, old: IActiveContext<*,*>?) {
        treeModel.invoker.invokeLater {
            val contexts = findNodes(old)
                .map { TreePathUtil.toTreePath(it) }
            // have existing node point to new context (is pointing to old context that was replaced)
            setElement(new, contexts)
            invalidatePaths(contexts)
        }
    }

    /**
     * Sets the given element to all the nodes specified by the given [TreePath]s.
     * Does nothing if the element is `null`.
     *
     * @param element the element to be set to the node
     * @param paths the paths to the nodes whose element should be set
     */
    private fun setElement(element: Any?, paths: Collection<TreePath>) {
        if (element == null) {
            return
        }

        paths.forEach { it.lastPathComponent.getDescriptor()?.setElement(element) }
    }

    override fun removed(removed: Any) {
        treeModel.invoker.invokeLater {
            val parents = findNodes(removed)
                .map { TreePathUtil.toTreePath(it.parent) }
            invalidatePaths(parents)
        }
    }

    override fun added(added: Any) {
        treeModel.invoker.invokeLater {
            val parents = getPotentialParentNodes(added)
                .map { TreePathUtil.toTreePath(it) }
            invalidatePaths(parents)
        }
    }

    override fun modified(modified: Any) {
        treeModel.invoker.invokeLater {
            val paths = findNodes(modified)
                .map { node -> TreePathUtil.pathToTreeNode(node) }
            // update node
            updateDescriptors(paths, modified)
            // trigger reload of children
            invalidatePaths(paths)
        }
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

    private fun isRootNode(element: Any?): Boolean {
        val descriptor = (treeModel.root as? DefaultMutableTreeNode)?.userObject as? NodeDescriptor<*>
        return element == descriptor?.element
    }

    private fun invalidateRoot() {
        treeModel.invalidate()
    }

    private fun getPotentialParentNodes(element: Any): Collection<TreeNode> {
        val rootNode = treeModel.root
        if (true == rootNode.getDescriptor()?.hasElement(element)) {
            return listOf(rootNode)
        }
        // lookup descriptor that would display new element that's not displayed yet
        // in case of an 'added' event
        return getAllNodes(rootNode)
            .filter { node -> structure.isParentDescriptor(node.getDescriptor(), element) }
    }

    private fun getAllNodes(start: TreeNode): Collection<TreeNode> {
        return findNodes({ true }, start)
    }

    private fun findNodes(element: Any?): Collection<TreeNode> {
        return if (element == null) {
            emptyList()
        } else if (isRootNode(element)) {
            listOf(treeModel.root)
        } else {
            findNodes({ node: TreeNode -> hasElement(element, node) }, treeModel.root)
        }
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
        return node.getDescriptor()?.hasElement(element) ?: false
    }

    override fun dispose() {
        model?.removeListener(this)
    }

}
