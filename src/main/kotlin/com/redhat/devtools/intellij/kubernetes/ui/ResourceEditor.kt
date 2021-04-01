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

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import org.slf4j.LoggerFactory
import java.io.IOException
import com.intellij.openapi.vfs.LocalFileSystem
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets


object ResourceEditor {

	private const val FILE_EXTENSION = "yml"
	private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)
	private const val KEY_RESOURCE = "resource"

	@Throws(IOException::class)
	fun open(project: Project, resource: HasMetadata) {
		val file = createVirtualFile(resource, project) ?: return
		file.putUserData(Key(KEY_RESOURCE), resource)
		file.putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true);
		FileEditorManager.getInstance(project).openFile(file, true)
	}

	@Throws(IOException::class)
	private fun createVirtualFile(resource: HasMetadata, project: Project): VirtualFile? {
		val name = getName(resource)
		val file = File(FileUtils.getTempDirectory(), name)
		FileUtils.write(file, Serialization.asYaml(resource), StandardCharsets.UTF_8)
		file.deleteOnExit()
		return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
	}

	private fun getName(resource: HasMetadata): String {
		val name = when(resource) {
			is Namespace,
			is Project
				-> "${resource.metadata.name}"
			else
				-> "${resource.metadata.name}@${resource.metadata.namespace}"
		}
		return "$name.$FILE_EXTENSION"
	}
}