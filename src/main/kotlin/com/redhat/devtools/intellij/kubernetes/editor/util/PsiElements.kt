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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope

object PsiElements {

    fun getAll(type: FileType, project: Project?): List<PsiElement> {
        if (project == null) {
            return emptyList()
        }

        val scope = ProjectScope.getProjectScope(project)
        return FileTypeIndex.getFiles(type, scope)
            .mapNotNull { file ->PsiManager.getInstance(project).findFile(file) }
            .flatMap { file -> file.getAllElements() }
    }
}