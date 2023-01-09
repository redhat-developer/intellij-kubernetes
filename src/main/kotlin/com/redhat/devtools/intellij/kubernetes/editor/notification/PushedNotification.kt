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
import com.intellij.util.containers.isNullOrEmpty
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

    fun show(states: Collection<Different>) {
        if (states.isEmpty()) {
            return
        }
        editor.showNotification(KEY_PANEL, { createPanel(states) }, project)
    }

    fun hide() {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(states: Collection<Different>): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        val toCreateOrUpdate = states.groupBy { state ->
            state.exists
        }
        val toCreate = toCreateOrUpdate[false]
        val toUpdate = toCreateOrUpdate[true]
        panel.text = createText(toCreate, toUpdate)
        addDismiss(panel) {
            hide()
        }

        return panel
    }

    private fun createText(created: List<Different>?, updated: List<Different>?): String {
        return StringBuilder().apply {
            if (!created.isNullOrEmpty()) {
                append("Created ${toKindAndNames(created?.map { state -> state.resource })} ")
            }
            if (!updated.isNullOrEmpty()) {
                if (isNotEmpty()) {
                    append(", ")
                }
                append("updated ${toKindAndNames(updated?.map { state -> state.resource })}")
            }
        }
        .toString()
    }
}