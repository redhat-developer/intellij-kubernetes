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
package com.redhat.devtools.intellij.kubernetes.ui

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import org.jetbrains.yaml.YAMLLanguage
import org.slf4j.LoggerFactory
import java.io.IOException


object ResourceEditor {

	private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)
	private const val KEY_RESOURCE = "resource"

	@Throws(IOException::class)
	fun open(project: Project, resource: HasMetadata) {
		val file = createVirtualFile(resource, project) ?: return
		openEditor(project, resource, file)
	}

	private fun openEditor(project: Project, resource: HasMetadata, file: VirtualFile) {
		file.putUserData(Key(KEY_RESOURCE), resource)
		FileEditorManager.getInstance(project).openFile(file, true)
	}

	@Throws(IOException::class)
	private fun createVirtualFile(resource: HasMetadata, project: Project): VirtualFile? {
		val name = getName(resource)
		val file = ScratchRootType.getInstance().findFile(project, name, ScratchFileService.Option.existing_only)
		return if (file != null
			&& file.exists()) {
			file
		} else {
			ScratchRootType.getInstance().createScratchFile(
				project, name, YAMLLanguage.INSTANCE, Serialization.asYaml(resource)
			)
		}
	}

	private fun getName(resource: HasMetadata): String {
		return when(resource) {
			is Namespace,
			is Project
				-> "${resource.metadata.name}"
			else
				-> "${resource.metadata.name}@${resource.metadata.name}"
		}
	}
}