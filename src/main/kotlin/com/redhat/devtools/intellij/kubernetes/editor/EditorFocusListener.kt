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
        handleSelectionLost(event.oldEditor, event.oldFile, project)
        handleSelectionGained(event.newEditor, event.newFile, project)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        ResourceEditor.onClosed(file)
    }

    override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) {
        ResourceEditor.onBeforeClosed(source.getSelectedEditor(file), source.project)
    }

    private fun handleSelectionLost(editor: FileEditor?, file: VirtualFile?, project: Project) {
        if (editor == null
            || !ResourceEditor.isResourceFile(file)) {
            return
        }
        try {
            ResourceEditor.stopWatch(editor)
        } catch (e: RuntimeException) {
            ErrorNotification.show(
                editor,
                project,
                "Error contacting cluster. Make sure it's reachable, api version supported, etc.",
                e.cause ?: e
            )
        }
    }

    private fun handleSelectionGained(editor: FileEditor?, file: VirtualFile?, project: Project) {
        if (editor == null
            || !ResourceEditor.isResourceFile(file)) {
            return
        }
        try {
            ResourceEditor.startWatch(editor, project)
            ResourceEditor.updateEditor(editor, project)
        } catch (e: ResourceException) {
            ErrorNotification.show(
                editor,
                project,
                "Error contacting cluster. Make sure it's reachable, api version supported, etc.",
                e.cause ?: e
            )
        }
    }

}

