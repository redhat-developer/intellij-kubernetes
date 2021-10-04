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

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.kubernetes.editor.util.getProjectAndEditor

class KubernetesEditorTabTitleProvider : EditorTabTitleProvider {

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        val editor = ResourceEditor.get(getProjectAndEditor(file)?.editor, project) ?: return null
        return editor.getTitle()
    }

}