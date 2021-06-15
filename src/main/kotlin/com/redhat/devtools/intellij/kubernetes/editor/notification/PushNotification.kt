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
import javax.swing.JComponent

object PushNotification {

    private val KEY_PANEL = Key<JComponent>(PushNotification::javaClass.name)

    fun show(editor: FileEditor, project: Project) {
        editor.showNotification(KEY_PANEL, { createPanel(editor, project) }, project)
    }

    fun hide(editor: FileEditor, project: Project) {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(editor: FileEditor, project: Project): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.setText(
            "Push local changes, ${
                if (false == ResourceEditor.get(editor, project)?.existsOnCluster()) {
                    "create new"
                } else {
                    "update existing"
                }
            } resource on server?"
        )
        panel.createActionLabel("Push to Cluster") {
            ResourceEditor.get(editor, project)?.push()
        }
        if (true == ResourceEditor.get(editor, project)?.isOutdated()) {
            panel.createActionLabel("Reload from Cluster") {
                ResourceEditor.get(editor, project)?.replaceEditorContent()
                editor.hideNotification(KEY_PANEL, project)
            }
        }
        panel.createActionLabel("Ignore") {
            editor.hideNotification(KEY_PANEL, project)
        }

        return panel
    }
}