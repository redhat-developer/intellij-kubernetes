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
import com.intellij.psi.impl.PsiDocumentTransactionListener
import com.redhat.devtools.intellij.kubernetes.editor.EditorFocusListener
import com.redhat.devtools.intellij.kubernetes.editor.EditorTransactionListener
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor

class KubernetesPluginInitializer : StartupActivity {

    override fun runActivity(project: Project) {
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
            EditorFocusListener(project)
        )
        project.messageBus.connect().subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
            EditorFocusListener(project)
        )
        enableAllEditorsNonProjectEditing(project)
        showResourceEditorNotifications(project)

        project.messageBus.connect().subscribe(PsiDocumentTransactionListener.TOPIC,
            EditorTransactionListener()
        )
    }

    private fun enableAllEditorsNonProjectEditing(project: Project) {
        FileEditorManager.getInstance(project).allEditors
            .mapNotNull { editor -> ResourceEditor.factory.getOrCreate(editor, project) }
            .forEach { resourceEditor -> resourceEditor.enableNonProjectFileEditing() }
    }

    private fun showResourceEditorNotifications(project: Project) {
        val selected = FileEditorManager.getInstance(project).selectedEditor ?: return
        ResourceEditor.factory.getOrCreate(selected, project)?.update()
    }
}