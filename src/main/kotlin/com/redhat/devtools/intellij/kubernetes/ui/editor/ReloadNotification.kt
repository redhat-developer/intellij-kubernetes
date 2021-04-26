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
package com.redhat.devtools.intellij.kubernetes.ui.editor

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.JComponent

object ReloadNotification {

    private val KEY_PANEL = Key<JComponent>(ReloadNotification::javaClass.name)

    fun show(editor: FileEditor, resource: HasMetadata, project: Project) {
        editor.showNotification(KEY_PANEL, { createPanel(editor, resource, project) }, project)
    }

    fun hide(editor: FileEditor, project: Project) {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(editor: FileEditor, resource: HasMetadata, project: Project): EditorNotificationPanel {
        val panel = EditorNotificationPanel(EditorColors.NOTIFICATION_BACKGROUND)
        panel.setText("${resource.metadata.name} changed on server. Reload?")
        panel.createActionLabel("Reload now") {
            val file = editor.file
            if (file != null
                && !project.isDisposed) {
                val editorModel = ResourceEditor.getEditorModel(editor, project)
                if (editorModel != null) {
                    ResourceEditorFile.create(editorModel.getLatestRevision(), file)
                    FileDocumentManager.getInstance().reloadFiles(editor.file!!)
                    FileEditorManager.getInstance(project).removeTopComponent(editor, panel)
                }
            }
        }

        panel.createActionLabel("Keep current") {
            editor.hideNotification(KEY_PANEL, project)
        }
        return panel
    }
}