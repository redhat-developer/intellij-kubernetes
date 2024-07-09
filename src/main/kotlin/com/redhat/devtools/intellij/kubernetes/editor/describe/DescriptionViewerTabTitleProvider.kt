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
package com.redhat.devtools.intellij.kubernetes.editor.describe

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.fabric8.kubernetes.api.model.HasMetadata

class DescriptionViewerTabTitleProvider: EditorTabTitleProvider {

	companion object {
		const val TITLE_UNKNOWN_CLUSTERRESOURCE = "Unknown Cluster Resource"
		const val TITLE_UNKNOWN_NAME = "unknown name"
	}

	override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
		if (!isDescribeFile(file)) {
			return null
		}

		val resource = DescriptionViewerFactory.getResource(file)
		return getTitleFor(resource)
	}

	private fun getTitleFor(resource: HasMetadata?): String {
		val resourceLabel = if (resource == null) {
			TITLE_UNKNOWN_CLUSTERRESOURCE
		} else {
			val kind = resource.kind
			val name = resource.metadata?.name ?: TITLE_UNKNOWN_NAME
			"$kind $name"
		}
		return "Describe $resourceLabel"
	}

	private fun isDescribeFile(file: VirtualFile): Boolean {
		return DescriptionViewerFactory.isDescriptionFile(file)
	}
}