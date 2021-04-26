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
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.JComponent

object DeletedNotification {

    private val KEY_PANEL = Key<JComponent>(DeletedNotification::javaClass.name)

    fun show(editor: FileEditor, resource: HasMetadata, project: Project) {
        editor.showNotification(KEY_PANEL, { createPanel(editor, resource, project) }, project)
    }

    private fun createPanel(editor: FileEditor, resource: HasMetadata, project: Project): EditorNotificationPanel {
        val panel = EditorNotificationPanel(EditorColors.NOTIFICATION_BACKGROUND)
        panel.setText("${resource.metadata.name} was deleted on server. Keep content?")
        panel.createActionLabel("Close Editor") {
            val file = editor.file
            if (file != null
                && !project.isDisposed) {
                FileEditorManager.getInstance(project).closeFile(file)
                ResourceEditor.delete(FileEditorManager.getInstance(project), file)
            }
        }

        panel.createActionLabel("Keep current") {
            editor.hideNotification(KEY_PANEL, project)
        }
        return panel
    }
}