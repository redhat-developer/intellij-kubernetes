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
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.kubernetes.editor.ResourceFile

fun getProjectAndEditor(file: VirtualFile): ProjectAndEditor? {
    return ProjectManager.getInstance().openProjects
        .filter { project -> project.isInitialized && !project.isDisposed }
        .flatMap { project ->
            FileEditorManager.getInstance(project).getEditors(file).toList()
                .mapNotNull { editor -> ProjectAndEditor(project, editor) }
        }
        .firstOrNull()
}

fun getResourceFile(document: Document): VirtualFile? {
    val file = FileDocumentManager.getInstance().getFile(document)
    if (!ResourceFile.isResourceFile(file)) {
        return null
    }
    return file
}

fun getFileEditor(project: Project): FileEditor? {
    val editor = FileEditorManager.getInstance(project).selectedEditor ?: return null
    return if (!ResourceFile.isResourceFile(editor.file)) {
        return null
    } else {
        editor
    }
}

fun getDocument(editor: FileEditor): Document? {
    val file = editor.file ?: return null
    return ReadAction.compute<Document, Exception> {
        FileDocumentManager.getInstance().getDocument(file)
    }
}

class ProjectAndEditor(val project: Project, val editor: FileEditor)

