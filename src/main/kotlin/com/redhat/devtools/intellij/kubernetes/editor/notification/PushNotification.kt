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
import com.redhat.devtools.intellij.kubernetes.editor.Different
import com.redhat.devtools.intellij.kubernetes.editor.EditorResource
import com.redhat.devtools.intellij.kubernetes.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.editor.showNotification
import com.redhat.devtools.intellij.kubernetes.model.util.toKindAndNames
import javax.swing.JComponent

/**
 * An editor (panel) notification that informs of a change in the editor that may be pushed to the cluster.
 */
class PushNotification(private val editor: FileEditor, private val project: Project) {

    companion object {
        val KEY_PANEL = Key<JComponent>(PushNotification::class.java.canonicalName)
    }

    fun show(showPull: Boolean, editorResources: Collection<EditorResource>) {
        val toCreateOrUpdate = editorResources
            .filter { editorResource ->
                editorResource.getState() is Different
            }
            .groupBy { editorResource ->
                (editorResource.getState() as Different).exists
            }
        val toCreate = toCreateOrUpdate[false] ?: emptyList()
        val toUpdate = toCreateOrUpdate[true] ?: emptyList()
        if (toCreate.isEmpty()
            && toUpdate.isEmpty()) {
            return
        }
        editor.showNotification(KEY_PANEL, { createPanel(showPull, toCreate, toUpdate) }, project)
    }

    fun hide() {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(
        showPull: Boolean,
        toCreate: Collection<EditorResource>,
        toUpdate: Collection<EditorResource>
    ): EditorNotificationPanel {
        val text = createText(toCreate, toUpdate)
        return createPanel(text,
            toUpdate.isNotEmpty(),
            showPull && toUpdate.any {
                editorResource -> editorResource.isOutdatedVersion()
            })
    }

    private fun createText(toCreate: Collection<EditorResource>?, toUpdate: Collection<EditorResource>?): String {
        return StringBuilder().apply {
            if (false == toCreate?.isEmpty()) {
                append("Push to create ${toKindAndNames(toCreate.map { editorResource -> editorResource.getResource() })}")
            }
            if (false == toUpdate?.isEmpty()) {
                if (isNotEmpty()) {
                    append(", ")
                } else {
                    append("Push to ")
                }
                append("update ${toKindAndNames(toUpdate.map { editorResource -> editorResource.getResource() })}")
            }
            append("?")
        }
        .toString()
    }

    private fun createPanel(text: String, existsOnCluster: Boolean, isOutdated: Boolean): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.text = text
        addPush(false, panel)
        if (isOutdated) {
            addPull(panel)
        }
        if (existsOnCluster) {
            addDiff(panel)
        }
        addDismiss(panel) {
            hide()
        }

        return panel
    }
}