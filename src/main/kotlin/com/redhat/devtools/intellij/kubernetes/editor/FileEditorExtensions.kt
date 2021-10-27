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
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel
import javax.swing.JComponent

/**
 * Shows in this [FileEditor] the notification that the given factory creates and stores it under the given key.
 * An existing notification is removed beforehand.
 *
 * @param key that the notification is stored with
 * @param notificationFactory that creates the notification that will be shown
 * @param project that this editor belongs to
 */
fun FileEditor.showNotification(key: Key<JComponent>, notificationFactory: () -> EditorNotificationPanel, project: Project) {
        if (project.isDisposed) {
                return
        }
        if (getUserData(key) != null) {
            // already shown, remove existing
            hideNotification(key, project)
        }
        val panel = notificationFactory.invoke()
        FileEditorManager.getInstance(project).addTopComponent(this, panel)
        this.putUserData(key, panel)
}

/**
 * Hides a notification that exists in this [FileEditor] for the given key and [Project].
 *
 * @param key that the notification is stored with
 * @param project that this editor belongs to
 */
fun FileEditor.hideNotification(key: Key<JComponent>, project: Project) {
        if (project.isDisposed) {
                return
        }
        val panel = this.getUserData(key) ?: return
        FileEditorManager.getInstance(project).removeTopComponent(this, panel)
        this.putUserData(key, null)
}
