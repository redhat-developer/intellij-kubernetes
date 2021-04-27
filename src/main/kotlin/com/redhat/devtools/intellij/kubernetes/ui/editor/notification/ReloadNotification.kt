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
package com.redhat.devtools.intellij.kubernetes.ui.editor.notification

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.kubernetes.ui.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.ui.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.ui.editor.showNotification
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.JComponent

object ReloadNotification {

    private val KEY_PANEL = Key<JComponent>(ReloadNotification::javaClass.name)
    private val panel: EditorNotificationPanel? = null


    fun show(editor: FileEditor, resource: HasMetadata, project: Project) {
        editor.showNotification(KEY_PANEL, { getOrCreatePanel(editor, resource, project) }, project)
    }

    fun hide(editor: FileEditor, project: Project) {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun getOrCreatePanel(
        editor: FileEditor,
        resource: HasMetadata,
        project: Project
    ): EditorNotificationPanel {
        return panel ?: createPanel(editor, resource, project)
    }

    private fun createPanel(editor: FileEditor, resource: HasMetadata, project: Project): EditorNotificationPanel {
        val panel = EditorNotificationPanel(EditorColors.NOTIFICATION_BACKGROUND)
        panel.setText("${resource.metadata.name} changed on server. Reload?")
        panel.createActionLabel("Reload now") {
            val file = editor.file
            if (file != null
                && !project.isDisposed) {
                reloadEditor(editor, project, file)
            }
        }

        panel.createActionLabel("Keep current") {
            editor.hideNotification(KEY_PANEL, project)
        }
        return panel
    }

    private fun reloadEditor(editor: FileEditor, project: Project, file: VirtualFile) {
        val latestRevision = ResourceEditor.getLatestResource(editor, project)
        if (latestRevision != null) {
            ResourceEditor.replaceFile(latestRevision, file, project)
        }
    }
}