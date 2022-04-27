/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.actions.impl.MutableDiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil

/**
 * A provider that can diff the content of a file with a given String.
 */
open class ResourceDiff(private val project: Project) {

    /**
     * Opens a diff (window) that compares the given file with the given String.
     * The diff is opened in a non-modal dialog with 2 parts. On the left there's the file while on the right, there's the given String.
     * The diff allows editing in the file and moving changes from the content to the file (not in the opposite direction).
     * The given lambda is run when the user is closing the diff (window).
     *
     * @param file the file whose content shall be compared
     * @param toCompare the String to compare with the content of the file
     * @param onClosed the lambda that's run when the diff (window) is closed
     */
    fun open(file: VirtualFile, toCompare: String, onClosed: () -> Unit) {
        val chain: MutableDiffRequestChain = createDiffRequestChain(file, toCompare) ?: return
        scrollToFirstChange(chain)
        runInUI {
            val hint = DiffDialogHints(WindowWrapper.Mode.NON_MODAL, null) { wrapper ->
                UIUtil.runWhenWindowClosed(wrapper.window) { onClosed.invoke() }
            }
            DiffManager.getInstance().showDiff(project, chain, hint)
        }
    }

    private fun createDiffRequestChain(file: VirtualFile, toCompare: String): MutableDiffRequestChain? {
        val contentFactory = DiffContentFactory.getInstance()
        val left = contentFactory.create(project, file)
        val right = contentFactory.create(project, toCompare, file.fileType)
        val chain = MutableDiffRequestChain(left, right)
        val requestFactory = DiffRequestFactory.getInstance()
        chain.windowTitle = requestFactory.getTitle(file)
        chain.title1 = requestFactory.getContentTitle(file)
        chain.title2 = "Cluster Resource"
        return chain
    }

    private fun scrollToFirstChange(chain: MutableDiffRequestChain) {
        val content = chain.content2
        if (content !is DocumentContent) {
            return
        }
        val editors = EditorFactory.getInstance().getEditors(content.document)
        if (editors.isEmpty()) {
            return
        }
        val currentLine = editors[0].caretModel.logicalPosition.line
        chain.putRequestUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentLine))
    }

    /** for testing purposes */
    protected open fun runInUI(runnable: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runnable.invoke()
        } else {
            ApplicationManager.getApplication().invokeLater(runnable)
        }
    }
}