/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
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
import javax.swing.JComponent

class CustomizableNotification(
    private val editor: FileEditor,
    private val project: Project,
    private val customizer: (context: Array<Any>, panel: EditorNotificationPanel) -> EditorNotificationPanel
) {

    companion object {
        val KEY_PANEL = Key<JComponent>(CustomizableNotification::class.java.canonicalName)
    }

    fun show(context: Array<Any>) {
        editor.showNotification(KEY_PANEL, { createPanel(context) }, project)
    }

    fun hide() {
        editor.hideNotification(KEY_PANEL, project)
    }


    private fun createPanel(context: Array<Any>): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        customizer.invoke(context, panel)
        return panel
    }
}