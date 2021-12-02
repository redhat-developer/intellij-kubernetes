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
import com.redhat.devtools.intellij.kubernetes.model.ResourceException

class EditorFocusListener(private val project: Project) : FileEditorManagerListener, FileEditorManagerListener.Before {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        selectionLost(event.oldEditor, project)
        selectionGained(event.newEditor, project)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // editor cannot be found via manager once file was closed
        // deleting file before file was closed (#beforeFileClosed) causes recursion #fileClosed
        ResourceEditorFactory.instance.getExisting(file)?.close()
    }

    private fun selectionGained(editor: FileEditor?, project: Project) {
        if (editor == null) {
            return
        }
        try {
            ResourceEditorFactory.instance.getExistingOrCreate(editor, project)
                ?.startWatch()
                ?.update()
        } catch (e: ResourceException) {
            ErrorNotification(editor, project).show(
                "Error contacting cluster. Make sure it's reachable, api version supported, etc.",
                e.cause ?: e
            )
        }
    }

    private fun selectionLost(editor: FileEditor?, project: Project) {
        if (editor == null) {
            return
        }
        try {
            ResourceEditorFactory.instance.getExistingOrCreate(editor, project)?.stopWatch()
        } catch (e: RuntimeException) {
            ErrorNotification(editor, project).show(
                "Error contacting cluster. Make sure it's reachable, api version supported, etc.",
                e.cause ?: e
            )
        }
    }

}

