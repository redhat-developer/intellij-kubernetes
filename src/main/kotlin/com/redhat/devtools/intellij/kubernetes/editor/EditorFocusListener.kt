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
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification

class EditorFocusListener(private val project: Project) : FileEditorManagerListener, FileEditorManagerListener.Before {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        selectionLost(event.oldEditor, project)
        selectionGained(event.newEditor, project)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // editor cannot be found via manager once file was closed
        // deleting file before file was closed (#beforeFileClosed) causes recursion #fileClosed
        getExisting(file)?.close()
    }

    private fun selectionGained(editor: FileEditor?, project: Project) {
        if (editor == null) {
            return
        }
        try {
            ResourceEditorFactory.instance.getExistingOrCreate(editor, project)
                ?.startWatch()
                ?.update()
        } catch (e: RuntimeException) {
            showErrorNotification(e, editor, project)
        }
    }

    private fun selectionLost(editor: FileEditor?, project: Project) {
        if (editor == null) {
            return
        }
        try {
            getExisting(editor)?.stopWatch()
        } catch (e: RuntimeException) {
            showErrorNotification(e, editor, project)
        }
    }

    private fun showErrorNotification(
        e: RuntimeException,
        editor: FileEditor,
        project: Project
    ) {
        ErrorNotification(editor, project).show(
            e.message ?: "Undefined error",
            e)
    }

}

