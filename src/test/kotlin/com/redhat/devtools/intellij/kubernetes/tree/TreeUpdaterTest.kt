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
import com.intellij.util.concurrency.Invoker
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.actions.getDescriptor
import com.redhat.devtools.intellij.kubernetes.actions.getElement
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise
import org.junit.Before
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class TreeUpdaterTest {

    private val root = mutableTreeNode(null)
    private val texas = deployment("Texas")
    private val kansas = deployment("Kansas")
    private val mississippi = deployment("Mississippi")
    private val ponyExpress = deployment("Pony Express")
    private val daltonCity = deployment("Dalton City")
    private val lukyLuke = pod("Luky Luke")
    private val jollyJumper = pod("Jolly Jumper")
    // not present in the tree, notified as new element (to be added) that should get displayed
    private val rantanplan = pod("Rantanplan")
    private val joeDalton = pod("Joe Dalton")
    private val williamDalton = pod("William Dalton")
    private val jackDalton = pod("Jack Dalton")
    private val maDalton = pod("Ma Dalton")
    // not present in the tree, notified as new element (to be added) that should NOT get displayed
    private val calamityJane = pod("Calamity Jane")

    private val parents: MutableMap<HasMetadata, MutableList<TreeStructure.Descriptor<*>>> = mutableMapOf()
    private val structure: TreeStructure = object: TreeStructure(mock(), mock(), mock()) {
        override fun isParentDescriptor(descriptor: NodeDescriptor<*>?, element: Any): Boolean {
            // simplified mock impl of structure: rantanplan is to be displayed in 'Kansas' and 'Dalton City'
            // no other new node is to be displayed
            if (element == rantanplan) {
                return kansas == descriptor?.getElement<HasMetadata>()
                        || daltonCity == descriptor?.getElement<HasMetadata>()
            }
            // lookup in map created when adding children to root node
            val parentDescriptors: List<Descriptor<*>>? = parents[element]
            return parentDescriptors?.contains(descriptor) ?: false
        }
    }

    @Before
    fun before() {
        node(root, listOf(
            node(kansas, listOf(
                node(lukyLuke),
                node(jollyJumper)
            )),
            node(texas, listOf(
                node(joeDalton),
                node(williamDalton),
                node(jackDalton)
            )),
            node(mississippi, listOf(
                node(ponyExpress, listOf(
                    node(joeDalton),
                    node(williamDalton),
                    node(jackDalton))
                )
            )),
            node(daltonCity, listOf(
                node(maDalton))
            )
        ))
    }

    private val syncInvoker: Invoker = mock<Invoker>().apply {
        doAnswer { invocation ->
            // run runnable
            invocation.getArgument<Runnable>(0)?.run()
            // return CancellablePromise to fulfill API contract (causes npe otherwise)
            mock<CancellablePromise<*>>()
        }.whenever(this).invokeLater(any())
    }
    private val treeModel: StructureTreeModel<AbstractTreeStructure> = mock {
        on { this.invoker } doReturn syncInvoker
        on { root } doReturn root
        // required to not npe at runtime when mock calls super class
        on { invalidate(any(), any()) } doReturn mock<Promise<TreePath>>()
    }
    private val resourceModel: IResourceModel = mock()
    private val updater = TreeUpdater(treeModel, structure)

    @Test
    fun `#listenTo registers updater as resource change listener of the model`() {
        // given
        // when
        updater.listenTo(resourceModel)
        // then
        verify(resourceModel).addListener(updater)
    }

    @Test
    fun `#currentNamespace invalidates treeModel (tree root node)`() {
        // given
        // when
        updater.currentNamespace("luky luke")
        // then
        verify(treeModel).invalidate()
    }

    @Test
    fun `#removed should invalidate parent that is showing element`() {
        // given
        // when
        updater.removed(maDalton)
        // then
        val invalidatedParent = findNode(daltonCity)[0]
        verify(treeModel).invalidate(invalidatedParent, true)
    }

    @Test
    fun `#removed should invalidate parents that are showing element`() {
        // given
        // when
        updater.removed(joeDalton)
        // then
        /** joe dalton is shown in texas and in pony express **/
        val invalidatedPaths = argumentCaptor<TreePath>()
        verify(treeModel, times(2)).invalidate(invalidatedPaths.capture(), eq(true))
        assertThat(invalidatedPaths.allValues).containsExactlyInAnyOrder(*findNodes(
            texas,
            ponyExpress))
    }

    @Test
    fun `#removed should NOT invalidate parents if element is not shown`() {
        // given
        // when
        updater.removed(rantanplan)
        // then
        /** rantanplan is not shown, no invalidation should happen **/
        verify(treeModel, never()).invalidate(any(), eq(true))
    }

    @Test
    fun `#added should invalidate parents that would display the new element`() {
        // given
        // when
        updater.added(rantanplan)
        // then
        /** new element rantanplan should be shown in kansas and in dalton city according to [structure] **/
        val invalidatedPaths = argumentCaptor<TreePath>()
        verify(treeModel, times(2)).invalidate(invalidatedPaths.capture(), eq(true))
        assertThat(invalidatedPaths.allValues).containsExactlyInAnyOrder(*findNodes(
            kansas,
            daltonCity))
    }

    @Test
    fun `#added should NOT invalidate if no parents would display the new element`() {
        // given
        // when
        updater.added(calamityJane)
        // then
        /** new element calamity jane should NOT be shown in any parent according to [structure] **/
        verify(treeModel, never()).invalidate(any(), eq(true))
    }

    @Test
    fun `#modified should invalidate displayed elements`() {
        // given
        // when
        updater.modified(joeDalton)
        // then
        val invalidatedPaths = argumentCaptor<TreePath>() // joeDalton exists 2x in tree
        verify(treeModel, times(2)).invalidate(invalidatedPaths.capture(), eq(true))
        assertThat(invalidatedPaths.allValues).containsExactlyInAnyOrder(*findNodes(
            joeDalton))
    }

    @Test
    fun `#modified should NOT invalidate element that is NOT displayed`() {
        // given
        // when
        updater.modified(calamityJane)
        // then
        verify(treeModel, never()).invalidate(any(), any())
    }

    @Test
    fun `#modified should set element to nodes that are modified`() {
        // given
        // when
        updater.modified(joeDalton)
        // then
        val modified = findNodes(joeDalton)
        modified.forEach {
            val descriptor = it.lastPathComponent.getDescriptor()
            verify(descriptor)?.setElement(joeDalton)
        }
    }

    private fun deployment(name: String): Deployment {
        return DeploymentBuilder()
            .editOrNewMetadata()
            .withName(name)
            .endMetadata()
            .build()
    }

    private fun pod(name: String): Pod {
        return PodBuilder()
            .editOrNewMetadata()
            .withName(name)
            .endMetadata()
            .build()
    }

    private fun mutableTreeNode(resource: HasMetadata?): DefaultMutableTreeNode {
        val descriptor: TreeStructure.Descriptor<HasMetadata> = mock {
            on { this.element } doReturn resource
        }
        return DefaultMutableTreeNode(descriptor)
    }

    private fun node(
        resource: HasMetadata,
        children: List<DefaultMutableTreeNode> = emptyList()
    ): DefaultMutableTreeNode {
        return node(mutableTreeNode(resource), children)
    }

    private fun node(
        node: DefaultMutableTreeNode,
        children: List<DefaultMutableTreeNode> = emptyList()
    ): DefaultMutableTreeNode {
        node.removeAllChildren()
        children.forEach { child ->
            saveParent(child, node)
            node.add(child)
        }
        return node
    }

    private fun saveParent(node: DefaultMutableTreeNode, parent: DefaultMutableTreeNode) {
        val resource = node.getElement<HasMetadata>()!!
        val descriptor = parent.getDescriptor()!!
        if (parents[resource] == null) {
            parents[resource] = mutableListOf()
        }
        parents[resource]?.add(descriptor)
    }

    private fun findNodes(vararg resources: HasMetadata): Array<TreePath> {
        return resources.flatMap {
            resource -> findNode(resource)
        }.toTypedArray()
    }

    private fun findNode(resource: HasMetadata): MutableList<TreePath> {
        val paths = mutableListOf<TreePath>()
        findNode(resource, root, paths)
        return paths
    }

    private fun findNode(resource: HasMetadata, node: DefaultMutableTreeNode, paths: MutableList<TreePath>): MutableList<TreePath> {
        for (child in node.children()) {
            if (resource == child.getElement<HasMetadata>()) {
                paths.add(TreePathUtil.toTreePath(child))
            }
            findNode(resource, child as DefaultMutableTreeNode, paths)
        }
        return paths
    }
}