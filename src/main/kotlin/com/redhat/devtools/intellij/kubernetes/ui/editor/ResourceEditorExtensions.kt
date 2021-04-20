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
package com.redhat.devtools.intellij.kubernetes.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel
import javax.swing.JComponent

fun FileEditor.showNotification(
    key: Key<JComponent>,
    panelFactory: () -> EditorNotificationPanel,
    project: Project
) {
    if (this.getUserData(key) != null) {
        return
    }
    val panel = panelFactory.invoke()
    this.putUserData(key, panel)
    FileEditorManager.getInstance(project).addTopComponent(this, panel)
}

fun FileEditor.hideNotification(key: Key<JComponent>, project: Project) {
    val panel = this.getUserData(key)
    if (panel != null) {
        FileEditorManager.getInstance(project).removeTopComponent(this, panel)
    }
}
