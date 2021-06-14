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
package com.redhat.devtools.intellij.kubernetes.editor.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.editor.showNotification
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
        val panel = EditorNotificationPanel()
        panel.setText("${resource.metadata.name} changed on server. Reload?")
        panel.createActionLabel("Reload from Cluster") {
            ResourceEditor.get(editor, project)?.replaceEditor()
            editor.hideNotification(KEY_PANEL, project)
        }

        panel.createActionLabel("Push to Cluster") {
            ResourceEditor.get(editor, project)?.push()
        }

        panel.createActionLabel("Keep current") {
            editor.hideNotification(KEY_PANEL, project)
        }
        return panel
    }

}