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

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBEmptyBorder

class EditorToolbarFactory {
    companion object {
        fun create(actionId: String, editor: FileEditor, project: Project): ActionToolbar {
            val actionManager = ActionManager.getInstance()
            val group = actionManager.getAction(actionId) as ActionGroup
            val toolbar = actionManager
                .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true) as ActionToolbarImpl
            toolbar.targetComponent = editor.component
            toolbar.isOpaque = false
            toolbar.border = JBEmptyBorder(0, 2, 0, 2)
            invokeLater {
                FileEditorManager.getInstance(project).addTopComponent(editor, toolbar)
            }
            return toolbar
        }
    }
}
