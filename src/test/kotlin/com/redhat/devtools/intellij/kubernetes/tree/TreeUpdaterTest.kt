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
import com.nhaarman.mockitokotlin2.argThat
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
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Fakes.deployment
import com.redhat.devtools.intellij.kubernetes.model.mocks.Fakes.pod
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Descriptor
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise
import org.junit.Before
import org.junit.Test

class TreeUpdaterTest {

    private val resourceModel = mock<IResourceModel>()

    private val luckyLukeContext = mock<IActiveContext<*, *>>()
    private val root = mutableTreeNode(resourceModel)
    private val texas = deployment("Texas")
    private val kansas = deployment("Kansas")
    private val mississippi = deployment("Mississippi")
    private val ponyExpress = deployment("Pony Express")
    private val daltonCity = deployment("Dalton City")
    private val luckyLuke = pod("Lucky Luke")
    private val jollyJumper = pod("Jolly Jumper")
    // not present in the tree, notified as new element (to be added) that should get displayed
    private val rantanplan = pod("Rantanplan")
    private val joeDalton = pod("Joe Dalton")
    private val williamDalton = pod("William Dalton")
    private val jackDalton = pod("Jack Dalton")
    private val maDalton = pod("Ma Dalton")
    // not present in the tree, notified as new element (to be added) that should NOT get displayed
    private val calamityJane = pod("Calamity Jane")

    private val smurfsContext = mock<IActiveContext<*, *>>()
    private val papaSmurf = pod("Papa Smurf")
    private val gargamel = pod("Gargamel")

    private val parents: MutableMap<Any, MutableList<Descriptor<*>>> = mutableMapOf()
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
            node(luckyLukeContext, listOf(
                node(kansas, listOf(
                    node(luckyLuke),
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
                ))
            ),
            node(smurfsContext, listOf(
                node(papaSmurf, listOf()),
                node(gargamel, listOf())
            ))
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
        on { invoker } doReturn syncInvoker
        on { root } doReturn root
        // required to not npe at runtime when mock calls super class
        on { invalidate(any(), any()) } doReturn mock<Promise<TreePath>>()
    }
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
    fun `#currentNamespace invalidates context`() {
        // given
        val newLuckyLukeContext = mock<IActiveContext<*,*>>()
        // when
        updater.currentNamespaceChanged(newLuckyLukeContext, luckyLukeContext)
        // then
        verify(treeModel).invalidate(
            argThat { path: TreePath ->
                path.lastPathComponent.getDescriptor()?.element == luckyLukeContext
            },
            eq(true)
        )
    }

    @Test
    fun `#currentNamespace sets new context to node`() {
        // given
        val newLuckyLukeContext = mock<IActiveContext<*,*>>()
        // when
        updater.currentNamespaceChanged(newLuckyLukeContext, luckyLukeContext)
        // then
        val descriptor = findNode(luckyLukeContext).firstOrNull()?.lastPathComponent?.getDescriptor()
        assertThat(descriptor).isNotNull()
        verify(descriptor)?.setElement(newLuckyLukeContext)
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
        assertThat(invalidatedPaths.allValues)
            .containsExactlyInAnyOrder(*findNodes(joeDalton))
    }

    @Test
    fun `#modified(resource model) should invalidate tree model (aka root node)`() {
        // given
        // when
        updater.modified(resourceModel)
        // then
        verify(treeModel).invalidate()
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

    @Test
    fun `#dispose should stop listening to resource model`() {
        // given
        updater.listenTo(resourceModel)
        // when
        updater.dispose()
        // then
        verify(resourceModel).removeListener(updater)
    }

    @Test
    fun `#dispose should NOT stop listening to resource model if it never listened to it`() {
        // given dont listen to resource model
        // when
        updater.dispose()
        // then
        verify(resourceModel, never()).removeListener(updater)
    }

    private fun node(
        element: Any,
        children: List<DefaultMutableTreeNode> = emptyList()
    ): DefaultMutableTreeNode {
        return node(mutableTreeNode(element), children)
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

    private fun mutableTreeNode(element: Any): DefaultMutableTreeNode {
        val descriptor = objectIdentityDescriptor(element)
        return DefaultMutableTreeNode(descriptor)
    }

    private fun mutableTreeNode(model: IResourceModel): DefaultMutableTreeNode {
        val descriptor = objectIdentityDescriptor(model)
        return DefaultMutableTreeNode(descriptor)
    }

    private fun <T> objectIdentityDescriptor(element: T): Descriptor<T> {
        return mock {
            // mock ResourceDescriptor#hasElement
            on { hasElement(any()) } doAnswer {
                val given = it.arguments[0]
                given === element
            }
            on { getElement() } doReturn element
        }
    }

    private fun saveParent(node: DefaultMutableTreeNode, parent: DefaultMutableTreeNode) {
        val element = node.getElement<Any>() ?: return
        val descriptor = parent.getDescriptor() ?: return
        if (parents[element] == null) {
            parents[element] = mutableListOf()
        }
        parents[element]?.add(descriptor)
    }

    private fun findNodes(vararg element: Any): Array<TreePath> {
        return element.flatMap {
            element -> findNode(element)
        }.toTypedArray()
    }

    private fun findNode(element: Any): MutableList<TreePath> {
        val paths = mutableListOf<TreePath>()
        findNode(element, root, paths)
        return paths
    }

    private fun findNode(element: Any, node: DefaultMutableTreeNode, paths: MutableList<TreePath>): MutableList<TreePath> {
        for (child in node.children()) {
            if (element == child.getElement<Any>()) {
                paths.add(TreePathUtil.toTreePath(child))
            }
            findNode(element, child as DefaultMutableTreeNode, paths)
        }
        return paths
    }
}