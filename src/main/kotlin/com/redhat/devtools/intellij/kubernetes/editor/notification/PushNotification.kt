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
import com.intellij.util.containers.isNullOrEmpty
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

    fun show(showPull: Boolean, states: Collection<Different>) {
        if (states.isEmpty()) {
            return
        }
        editor.showNotification(KEY_PANEL, { createPanel(showPull, states) }, project)
    }

    fun hide() {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(showPull: Boolean, states: Collection<Different>): EditorNotificationPanel {
        val toCreateOrUpdate = states.groupBy { state ->
            state.exists
        }
        val toCreate = toCreateOrUpdate[false]
        val toUpdate = toCreateOrUpdate[true]
        val text = createText(toCreate, toUpdate)
        return createPanel(text,
            true == toUpdate?.isNotEmpty(),
            showPull && states.any { state -> state.isOutdatedVersion})
    }

    private fun createText(toCreate: List<Different>?, toUpdate: List<Different>?): String {
        return StringBuilder().apply {
            if (!toCreate.isNullOrEmpty()) {
                append("Push to create ${toKindAndNames(toCreate?.map { state -> state.resource })}")
            }
            if (!toUpdate.isNullOrEmpty()) {
                if (isNotEmpty()) {
                    append(", ")
                } else {
                    append("Push to ")
                }
                append("update ${toKindAndNames(toUpdate?.map { state -> state.resource })}")
            }
            append("?")
        }
        .toString()
    }

    private fun createPanel(text: String, existsOnCluster: Boolean, isOutdated: Boolean): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.text = text
        addPush(panel)
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