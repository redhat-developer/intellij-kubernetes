/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
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
import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionViewerTabTitleProvider

open class KubernetesEditorsTabTitleProvider(
    private val fallback: EditorTabTitleProvider = UniqueNameEditorTabTitleProvider()
) : EditorTabTitleProvider {

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        return ResourceEditorTabTitleProvider().getEditorTabTitle(project, file)
            ?: DescriptionViewerTabTitleProvider().getEditorTabTitle(project, file)
            ?: fallback.getEditorTabTitle(project, file)
    }
}