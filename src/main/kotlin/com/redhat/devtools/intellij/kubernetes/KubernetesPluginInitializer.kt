/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.redhat.devtools.intellij.kubernetes.editor.EditorListener
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification

class KubernetesPluginInitializer : StartupActivity {

    override fun runActivity(project: Project) {
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
            EditorListener(project)
        )
        enableNonProjectEditing(project)
        showResourceEditorNotifications(project)
    }

    private fun enableNonProjectEditing(project: Project) {
        FileEditorManager.getInstance(project).allEditors
            .filter { editor -> ResourceEditor.isResourceEditor(editor) }
            .mapNotNull { editor -> ResourceEditor.getResourceFile(editor) }
            .forEach { file -> ResourceEditor.enableNonProjectFileEditing(file) }
    }

    private fun showResourceEditorNotifications(project: Project) {
        val selected = FileEditorManager.getInstance(project).selectedEditor ?: return
        if (ResourceEditor.isResourceEditor(selected)) {
            try {
                ResourceEditor.showNotifications(selected, project)
            } catch (e: RuntimeException) {
                ErrorNotification.show(
                    selected,
                    project,
                    "Error contacting cluster. Make sure it's reachable, api version supported, etc.",
                    e.cause ?: e)
            }
        }
    }
}