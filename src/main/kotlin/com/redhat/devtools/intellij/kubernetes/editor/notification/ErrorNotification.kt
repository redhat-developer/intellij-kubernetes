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

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.awt.RelativePoint
import com.redhat.devtools.intellij.kubernetes.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.editor.showNotification
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import java.awt.Point
import javax.swing.JComponent

object ErrorNotification {

    private val KEY_PANEL = Key<JComponent>(ErrorNotification::javaClass.name)

    fun show(editor: FileEditor, project: Project, title: String, e: Throwable) {
        editor.showNotification(KEY_PANEL, { createPanel(editor, title, e) }, project)
    }

    fun hide(editor: FileEditor, project: Project) {
        editor.hideNotification(KEY_PANEL, project)
    }

    private fun createPanel(editor: FileEditor, title: String, e: Throwable): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.icon(AllIcons.Ide.FatalError)
        panel.setText(title)
        addDetailsAction(e, panel, editor)
        return panel
    }

    private fun addDetailsAction(e: Throwable, panel: EditorNotificationPanel, editor: FileEditor) {
        if (e.message == null) {
            return
        }
        panel.createActionLabel("Details") {
            val balloonBuilder = JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(
                    trimWithEllipsis(e.message!!.replace("\n", ""), 170),
                    MessageType.ERROR,
                    null)
            val balloon = balloonBuilder
                .setDialogMode(true)
                .createBalloon()
            showBelow(balloon, panel)
            Disposer.register(editor, balloon)
        }
    }

    private fun showBelow(balloon: Balloon, panel: EditorNotificationPanel) {
        val below = RelativePoint(panel, Point(panel.bounds.width / 2, panel.bounds.height))
        balloon.show(below, Balloon.Position.below)
    }
}