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

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile

fun getProjectAndEditor(file: VirtualFile): ProjectAndEditor? {
    return ProjectManager.getInstance().openProjects
        .filter { project -> project.isInitialized && !project.isDisposed }
        .flatMap { project ->
            FileEditorManager.getInstance(project).getEditors(file).toList()
                .mapNotNull { editor -> ProjectAndEditor(project, editor) }
        }
        .firstOrNull()
}

class ProjectAndEditor(val project: Project, val editor: FileEditor)

