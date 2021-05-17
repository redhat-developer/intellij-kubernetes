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
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import io.fabric8.kubernetes.client.KubernetesClientException

class EditorListener(private val project: Project) : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        handleSelectionLost(event.oldEditor, event.oldFile, project)
        handleSelectionGained(event.newEditor, event.newFile, project)
    }

    private fun handleSelectionLost(editor: FileEditor?, file: VirtualFile?, project: Project) {
        if (editor == null
            || !ResourceEditor.isResourceFile(file)) {
            return
        }
        try {
            ResourceEditor.stopWatch(editor, project)
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
            ResourceEditor.showNotifications(editor, project)
        } catch (e: KubernetesClientException) {
            ErrorNotification.show(
                editor,
                project,
                "Error contacting cluster. Make sure it's reachable, api version supported, etc.",
                e.cause ?: e
            )
        }
    }

}

