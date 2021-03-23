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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

object ResourceEditor {

	private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)
	private const val KEY_RESOURCE = "resource"

	@Throws(IOException::class)
	fun open(project: Project, resource: HasMetadata) {
		ProgressManager.getInstance().run(
			object : Task.Backgroundable(project, "Saving resource to file...", true) {

				@Throws(IOException::class)
				override fun run(progress: ProgressIndicator) {
					val file = createVirtualFile(resource) ?: return;
					ApplicationManager.getApplication().invokeLater {
						openEditor(project, resource, file)
					}
				}
			})
	}

	private fun openEditor(project: Project, resource: HasMetadata, file: VirtualFile) {
		file.putUserData(Key(KEY_RESOURCE), resource)
		FileEditorManager.getInstance(project).openFile(file, true)
	}

	@Throws(IOException::class)
	private fun createVirtualFile(resource: HasMetadata): VirtualFile? {
		val file: File = createTempFile(resource)
		return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
	}

	@Throws(IOException::class)
	private fun createTempFile(resource: HasMetadata): File {
		val file = File(FileUtils.getTempDirectory(), getId(resource) + ".yml")
		if (file.exists()) {
			return file
		}
		FileUtils.write(file, Serialization.asYaml(resource), StandardCharsets.UTF_8)
		file.deleteOnExit()
		return file
	}

	private fun getId(resource: HasMetadata): String {
		return when(resource) {
			is Namespace,
			is Project
				-> "${resource.metadata.name}"
			else
				-> "${resource.metadata.name}@${resource.metadata.name}"
		}
	}
}