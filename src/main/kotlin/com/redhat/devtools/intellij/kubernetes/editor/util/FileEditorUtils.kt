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
import com.intellij.psi.PsiDocumentManager
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
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

fun getFile(document: Document): VirtualFile? {
    return FileDocumentManager.getInstance().getFile(document)
}

fun getResourceFile(document: Document): VirtualFile? {
    val file = FileDocumentManager.getInstance().getFile(document)
    if (!ResourceFile.isValidType(file)) {
        return null
    }
    return file
}

fun getSelectedFileEditor(project: Project): FileEditor? {
    return FileEditorManager.getInstance(project).selectedEditor ?: return null
}

fun getDocument(editor: FileEditor): Document? {
    val file = editor.file ?: return null
    return ReadAction.compute<Document, Exception> {
        FileDocumentManager.getInstance().getDocument(file)
    }
}

/**
 * Returns `true` if the given document for the given psi document manager has a kubernetes resource
 */
fun hasKubernetesResource(document: Document?, psiDocumentManager: PsiDocumentManager): Boolean {
    if (document == null) {
        return false
    }
    return try {
        val typeInfo = ReadAction.compute<KubernetesTypeInfo, RuntimeException> {
            val psiFile = psiDocumentManager.getPsiFile(document)
            KubernetesTypeInfo.extractMeta(psiFile)
        }
        (typeInfo.kind != null
                && typeInfo.apiGroup != null)
    } catch (e: java.lang.RuntimeException) {
        false
    }
}

/**
 * Returns [KubernetesResourceInfo] for the given document and psi document manager
 *
 * @param document the document to check for being a kubernetes resource
 * @param psiDocumentManager the psi document manager to use for inspection
 */
fun getKubernetesResourceInfo(document: Document?, psiDocumentManager: PsiDocumentManager): KubernetesResourceInfo? {
    if (document == null) {
        return null
    }
    return try {
            ReadAction.compute<KubernetesResourceInfo, RuntimeException> {
                val psiFile = psiDocumentManager.getPsiFile(document)
                KubernetesResourceInfo.extractMeta(psiFile)
            }
        } catch (e: java.lang.RuntimeException) {
            null
        }
    }

class ProjectAndEditor(val project: Project, val editor: FileEditor)

