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

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.model.util.writeFile
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException


object ResourceEditor {

	public val USER_DATA_RESOURCE = Key<HasMetadata>("RESOURCE")
	private const val FILE_EXTENSION = "yml"
	private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)

	@Throws(IOException::class)
	fun open(project: Project, resource: HasMetadata) {
		val file = getFile(resource)
		var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
		if (virtualFile == null) {
			writeFile(resource, file)
		} else {
			var editor = getOpenEditor(virtualFile, project)
			if (editor == null) {
				writeFile(resource, file)
				virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
			}
		}
		FileUserData(virtualFile)
			.put(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true);
		val editor = openEditor(virtualFile, project)
		editor?.putUserData(USER_DATA_RESOURCE, resource)
	}

	private fun openEditor(
		virtualFile: VirtualFile?,
		project: Project
	): FileEditor? {
		if (virtualFile == null) {
			return null
		}
		val editors = FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
		return editors[0]
	}

	private fun getOpenEditor(file: VirtualFile?, project: Project): FileEditor? {
		if (file == null) {
			return null
		}
		return FileEditorManager.getInstance(project).getEditors(file).getOrNull(0)
	}

	private fun getFile(resource: HasMetadata): File {
		val name = getFileName(resource)
		return File(FileUtils.getTempDirectory(), name)
	}

	private fun getFileName(resource: HasMetadata): String {
		val name = when(resource) {
			is Namespace,
			is Project
				-> "${resource.metadata.name}"
			else
				-> {
				if (resource.metadata.namespace != null) {
					"${resource.metadata.name}@${resource.metadata.namespace}"
				} else {
					"${resource.metadata.name}"
				}
			}
		}
		return "$name.$FILE_EXTENSION"
	}

	private fun confirmOverwrite(): Boolean {
		val answer = Messages.showYesNoDialog(
			"Editor content is out of date. Replace it?",
			"Overwrite content?",
			Messages.getQuestionIcon())
		return answer == Messages.OK
	}

}