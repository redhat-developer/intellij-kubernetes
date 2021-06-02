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
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentTransactionListener

class EditorTransactionListener: PsiDocumentTransactionListener {

    override fun transactionStarted(document: Document, file: PsiFile) {
    }

    override fun transactionCompleted(document: Document, file: PsiFile) {
        updateEditor(document)
    }

    private fun updateEditor(document: Document) {
        val file = ResourceEditor.getResourceFile(document) ?: return
        val projectAndEditor = getProjectAndEditor(file) ?: return
        val editor = projectAndEditor.editor
        val project = projectAndEditor.project
        if (!editor.isValid
            || !ResourceEditor.isResourceEditor(editor)) {
            return
        }
        ResourceEditor.updateEditor(editor, project)
    }

    private fun getProjectAndEditor(file: VirtualFile): ProjectAndEditor? {
        return ProjectManager.getInstance().openProjects
            .filter { project -> project.isInitialized && !project.isDisposed }
            .flatMap { project ->
                FileEditorManager.getInstance(project).getEditors(file).toList()
                    .mapNotNull { editor -> ProjectAndEditor(project, editor) }
            }
            .firstOrNull()
    }

    private class ProjectAndEditor(val project: Project, val editor: FileEditor)

}