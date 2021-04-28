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

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EditorListener(private val project: Project) : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        handleSelectionLost(event.oldEditor, event.oldFile, project)
        handleSelectionGained(event.newEditor, event.newFile, project)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val editor = source.getEditors(file).firstOrNull() ?: return
        ResourceEditor.dispose(editor, file, project)
    }

    private fun handleSelectionLost(editor: FileEditor?, file: VirtualFile?, project: Project) {
        if (editor == null
            || !ResourceEditor.isResourceFile(file)
        ) {
            return
        }
        ResourceEditor.stopWatch(editor, project)
    }

    private fun handleSelectionGained(editor: FileEditor?, file: VirtualFile?, project: Project) {
        if (editor == null
            || !ResourceEditor.isResourceFile(file)
        ) {
            return
        }
        ResourceEditor.startWatch(editor, project)
        ResourceEditor.showNotifications(editor, project)
    }

}

