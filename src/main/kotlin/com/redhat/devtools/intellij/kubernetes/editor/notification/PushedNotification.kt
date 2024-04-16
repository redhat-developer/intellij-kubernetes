/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
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
import com.redhat.devtools.intellij.kubernetes.editor.EditorResource
import com.redhat.devtools.intellij.kubernetes.editor.FILTER_PUSHED
import com.redhat.devtools.intellij.kubernetes.editor.Pushed
import com.redhat.devtools.intellij.kubernetes.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.editor.showNotification
import com.redhat.devtools.intellij.kubernetes.model.util.toKindAndNames
import javax.swing.JComponent

/**
 * An editor (panel) notification that informs that the editor was pushed to the cluster.
 */
class PushedNotification(private val editor: FileEditor, private val project: Project) {

    companion object {
        val KEY_PANEL = Key<JComponent>(PushedNotification::class.java.canonicalName)
    }

    fun show(editorResources: Collection<EditorResource>) {
        if (editorResources.isEmpty()) {
            return
        }
        editor.showNotification(KEY_PANEL, { createPanel(editorResources) }, project)
    }

    fun hide() {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(editorResources: Collection<EditorResource>): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        val createdOrUpdated = editorResources
            .filter(FILTER_PUSHED)
            .groupBy { editorResource ->
                (editorResource.getState() as Pushed).updated
            }
        val created = createdOrUpdated[false]
        val updated = createdOrUpdated[true]
        panel.text = createText(created, updated)
        addDismiss(panel) {
            hide()
        }

        return panel
    }

    private fun createText(created: List<EditorResource>?, updated: List<EditorResource>?): String {
        return StringBuilder().apply {
            if (false == created?.isEmpty()) {
                append("Created ${toKindAndNames(created?.map { editorResource -> editorResource.getResource() })} ")
            }
            if (false == updated?.isEmpty()) {
                if (isNotEmpty()) {
                    append(", updated")
                } else {
                    append("Updated")
                }
                append(" ${toKindAndNames(updated.map { editorResource -> editorResource.getResource() })}")
            }
            append(" on cluster.")
        }
        .toString()
    }
}