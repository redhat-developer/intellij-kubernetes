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
import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource

open class ResourceEditorTabTitleProvider(
    private val fallback: EditorTabTitleProvider = UniqueNameEditorTabTitleProvider()
) : EditorTabTitleProvider {

    companion object {
        const val TITLE_UNKNOWN_CLUSTERRESOURCE = "Unknown Cluster Resource"
        const val TITLE_UNKNOWN_NAME = "unknown name"
    }

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        return if (isTemporary(file)) {
            val resourceInfo = getKubernetesResourceInfo(file, project)
            if (resourceInfo != null
                && isKubernetesResource(resourceInfo)
            ) {
                getTitleFor(resourceInfo)
            } else {
                TITLE_UNKNOWN_CLUSTERRESOURCE
            }
        } else {
            fallback.getEditorTabTitle(project, file)
        }
    }

    private fun getTitleFor(info: KubernetesResourceInfo): String {
        val name = info.name ?: TITLE_UNKNOWN_NAME
        val namespace = info.namespace
        return if (namespace == null) {
            name
        } else {
            "$name@$namespace"
        }
    }

    /* for testing purposes */
    protected open fun getKubernetesResourceInfo(file: VirtualFile, project: Project): KubernetesResourceInfo? {
        return com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo(file, project)
    }

    /* for testing purposes */
    protected open fun isTemporary(file: VirtualFile): Boolean {
        return ResourceFile.isTemporary(file)
    }

}