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
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.util.concurrency.Invoker
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import org.jetbrains.concurrency.CancellablePromise
import org.junit.Test

class TreeUpdaterTest {

    private val syncInvoker: Invoker = mock<Invoker>().apply {
        doAnswer { invocation ->
            // run runnable
            invocation.getArgument<Runnable>(0)?.run()
            // return CancellablePromise to fulfill API contract
            mock<CancellablePromise<*>>()
        }.whenever(this).invokeLater(any())
    }
    private val treeModel: StructureTreeModel<AbstractTreeStructure> = mock {
         on { this.invoker } doReturn syncInvoker
    }
    private val structure: TreeStructure = mock()
    private val resourceModel: IResourceModel = mock()
    private val updater = TestableTreeUpdater(treeModel, structure)

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
        updater.currentNamespace("42")
        // then
        verify(treeModel).invalidate()
    }

    private class TestableTreeUpdater(
        treeModel: StructureTreeModel<AbstractTreeStructure>,
        structure: TreeStructure
    ): TreeUpdater(treeModel, structure) {

    }
}