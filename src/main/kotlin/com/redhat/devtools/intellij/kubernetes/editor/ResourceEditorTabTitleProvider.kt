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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource

open class ResourceEditorTabTitleProvider: EditorTabTitleProvider {
	companion object {
		const val TITLE_UNKNOWN_CLUSTERRESOURCE = "Unknown Cluster Resource"
		const val TITLE_UNKNOWN_NAME = "unknown name"
	}

	override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
		if (!isResourceFile(file)) {
			return null
		}

		val resourceInfo = getKubernetesResourceInfo(file, project)
		return if (resourceInfo != null
			&& isKubernetesResource(resourceInfo)
		) {
			getTitleFor(resourceInfo)
		} else {
			TITLE_UNKNOWN_CLUSTERRESOURCE
		}
	}

	private fun getTitleFor(info: KubernetesResourceInfo): String {
		val name = info.name ?: TITLE_UNKNOWN_NAME
		val namespace = info.namespace ?: return name
		return "$name@$namespace"
	}

	protected open fun isResourceFile(file: VirtualFile): Boolean {
		return ResourceFile.isResourceFile(file)
	}

	/* for testing purposes */
	protected open fun getKubernetesResourceInfo(file: VirtualFile, project: Project): KubernetesResourceInfo? {
		return com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo(file, project)
	}

}