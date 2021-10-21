/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentTransactionListener
import com.redhat.devtools.intellij.kubernetes.editor.util.getProjectAndEditor
import com.redhat.devtools.intellij.kubernetes.editor.util.getResourceFile

class EditorTransactionListener: PsiDocumentTransactionListener {

    override fun transactionStarted(document: Document, file: PsiFile) {
    }

    override fun transactionCompleted(document: Document, file: PsiFile) {
        getResourceEditor(document)?.update()
    }

    private fun getResourceEditor(document: Document): ResourceEditor? {
        val file = getResourceFile(document) ?: return null
        val projectAndEditor = getProjectAndEditor(file) ?: return null
        return ResourceEditorFactory.instance.getExistingOrCreate(projectAndEditor.editor, projectAndEditor.project)
    }

}