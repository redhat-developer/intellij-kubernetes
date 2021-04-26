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

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EditorListener(private val project: Project) : FileEditorManagerListener {

        override fun selectionChanged(event: FileEditorManagerEvent) {
            if (event.newEditor == null
                || !ResourceEditor.isFile(event.newFile)
            ) {
                return
            }
            val editor = event.newEditor!!
            ResourceEditor.watchResource(editor, project)
            ResourceEditor.showNotifications(editor, project)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            ResourceEditor.delete(source, file)
        }
    }

