/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope

object PsiElements {

    fun getAll(type: FileType, project: Project?): List<PsiElement> {
        if (project == null) {
            return emptyList()
        }

        val scope = ProjectScope.getEverythingScope(project)
        val psiManager = PsiManager.getInstance(project)

        return FileTypeIndex.getFiles(type, scope)
            .mapNotNull { file -> psiManager.findFile(file) }
            .flatMap { file -> file.getAllElements() }
    }

    fun getAllNoExclusions(fileType: FileType, project: Project): List<PsiElement> {
        val basePath = project.basePath ?: return emptyList()
        val projectBaseDir = LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return emptyList()

        val collector = AllFilesCollector(fileType)
        VfsUtilCore.visitChildrenRecursively(projectBaseDir, collector)
        val manager = PsiManager.getInstance(project)
        return collector.getCollected()
            .mapNotNull { file -> manager.findFile(file) }
            .flatMap { psiFile -> psiFile.getAllElements() }
    }

    private class AllFilesCollector(private val fileType: FileType): VirtualFileVisitor<Unit>() {

        private val collected = HashSet<VirtualFile>()

        override fun visitFile(file: VirtualFile): Boolean {
            if (!isFileType(file, fileType)) {
                return true
            }
            collected.add(file)
            return true
        }

        fun getCollected(): Collection<VirtualFile> {
            return collected
        }

        private fun isFileType(file: VirtualFile, fileType: FileType): Boolean {
            return !file.isDirectory
                    && !file.fileType.isBinary
                    && fileType == file.fileType
        }

    }
}