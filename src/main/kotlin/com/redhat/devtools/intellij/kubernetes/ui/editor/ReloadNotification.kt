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
import java.io.File
import javax.swing.JComponent

object ReloadNotification {

    private val KEY_PANEL = Key<JComponent>("PANEL")

    fun show(editor: FileEditor, resource: HasMetadata, project: Project) {
        if (editor.getUserData(KEY_PANEL) != null) {
            return
        }
        val panel = createPanel(editor, resource, project)
        editor.putUserData(KEY_PANEL, panel)
        FileEditorManager.getInstance(project).addTopComponent(editor, panel)
    }

    fun hide(editor: FileEditor, project: Project) {
        val panel = editor.getUserData(KEY_PANEL)
        if (panel != null) {
            FileEditorManager.getInstance(project).removeTopComponent(editor, panel)
        }
    }

    private fun createPanel(editor: FileEditor, resource: HasMetadata, project: Project): EditorNotificationPanel {
        val panel = EditorNotificationPanel(EditorColors.NOTIFICATION_BACKGROUND)
        panel.setText("${resource.metadata.name} changed on server. Reload content?")
        panel.createActionLabel("Reload now") {
            val file = editor.file
            if (file != null
                && !project.isDisposed) {
                ResourceEditorFile.create(resource, File(file.canonicalPath))
                file.refresh(false, true)
                ResourceEditor.putUserDataResource(null, editor)
                FileDocumentManager.getInstance().reloadFiles(editor.file!!)
                FileEditorManager.getInstance(project).removeTopComponent(editor, panel)
            }
        }

        panel.createActionLabel("Keep current") {
            hide(editor, project)
        }
        return panel
    }
}