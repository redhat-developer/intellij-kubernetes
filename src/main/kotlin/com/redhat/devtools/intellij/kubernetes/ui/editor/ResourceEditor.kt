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

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.util.sameRevision
import com.redhat.devtools.intellij.kubernetes.ui.FileUserData
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

object ResourceEditor {

    val KEY_RESOURCE = Key<HasMetadata>("RESOURCE")

    private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)
    private val model = ServiceManager.getService(IResourceModel::class.java)

    @Throws(IOException::class)
    fun open(project: Project, resource: HasMetadata) {
        val file = ResourceEditorFile.get(resource)
        FileUserData(file)
            .put(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true);
        val editor = openEditor(file, project)
        putUserData(KEY_RESOURCE, resource, editor)
    }

    private fun openEditor(virtualFile: VirtualFile?, project: Project): FileEditor? {
        if (virtualFile == null) {
            return null
        }
        val editors = FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
        return editors.getOrNull(0)
    }

    fun isFile(file: VirtualFile?): Boolean {
        return ResourceEditorFile.matches(file)
    }

    fun delete(file: VirtualFile) {
        ResourceEditorFile.delete(file)
    }

    fun create(resource: HasMetadata, file: File) {
        ResourceEditorFile.create(resource, file)
    }

    fun <T> getUserData(key: Key<T>, editor: FileEditor?): T? {
        return editor?.getUserData(key)
    }

    fun <T> putUserData(key: Key<T>, value: T?, editor: FileEditor?) {
        editor?.putUserData(key, value)
    }

    fun compareToCluster(editor: FileEditor, project: Project) {
        val resource = getResource(editor) ?: return
        val latestRevision = model.resource(resource)
        if (latestRevision == null) {
            DeletedNotification.show(editor, resource, project)
        } else if (!resource.sameRevision(latestRevision)) {
            ReloadNotification.show(editor, resource, project)
        }
    }

    private fun getResource(editor: FileEditor): HasMetadata? {
        var resource = getUserData(KEY_RESOURCE, editor)
        if (resource == null
            && editor.file != null
        ) {
            val document = FileDocumentManager.getInstance().getDocument(editor.file!!)
            if (document?.text != null) {
                resource = Serialization.unmarshal(document.text, HasMetadata::class.java)
                putUserData(KEY_RESOURCE, resource, editor)
            }
        }
        return resource
    }

    object ResourceEditorFile {

        private val EXTENSION = YAMLFileType.DEFAULT_EXTENSION

        fun get(resource: HasMetadata): VirtualFile? {
            val name = getName(resource)
            val file = File(FileUtils.getTempDirectory(), name)
            if (!file.exists()) {
                create(resource, file)
            }
            return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        }

        fun matches(file: VirtualFile?): Boolean {
            return file?.path?.endsWith(EXTENSION, true) ?: false
                    && file?.path?.startsWith(FileUtils.getTempDirectoryPath()) ?: false
        }

        fun create(resource: HasMetadata, file: File) {
            val content = Serialization.asYaml(resource)
            FileUtils.write(file, content, StandardCharsets.UTF_8, false)
        }

        fun delete(file: VirtualFile) {
            WriteAction.compute<Unit, Exception> { file.delete(this) }
        }

        private fun getName(resource: HasMetadata): String {
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
            return "$name.${EXTENSION}"
        }
    }
}

