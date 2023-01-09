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
import com.redhat.devtools.intellij.kubernetes.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.editor.showNotification
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.JComponent

/**
 * An editor (panel) notification that informs that the resource was pulled from the cluster.
 */
class PulledNotification(private val editor: FileEditor, private val project: Project) {

    companion object {
        private val KEY_PANEL = Key<JComponent>(PulledNotification::class.java.canonicalName)
    }

    fun show(resource: HasMetadata) {
        editor.showNotification(KEY_PANEL, { createPanel(resource) }, project)
    }

    fun hide() {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(resource: HasMetadata): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.text =
            "Pulled ${resource.kind} '${resource.metadata.name}' ${
                if (resource.metadata.resourceVersion != null) {
                    "to revision ${resource.metadata.resourceVersion}"
                } else {
                    ""
                }
            }"
        addDismiss(panel) {
            hide()
        }
        return panel
    }
}