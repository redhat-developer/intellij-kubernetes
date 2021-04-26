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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.ui.FileUserData
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import java.io.File
import java.nio.charset.StandardCharsets

object ResourceEditorFile {

    private const val EXTENSION = YAMLFileType.DEFAULT_EXTENSION

    fun get(resource: HasMetadata): VirtualFile? {
        val name = getName(resource)
        val file = File(FileUtils.getTempDirectory(), name)
        if (!file.exists()) {
            create(resource, file)
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        FileUserData(virtualFile)
            .put(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true);
        return virtualFile
    }

    fun matches(file: VirtualFile?): Boolean {
        return file?.path?.endsWith(EXTENSION, true) ?: false
                && file?.path?.startsWith(FileUtils.getTempDirectoryPath()) ?: false
    }

    fun create(resource: HasMetadata, file: VirtualFile) {
        create(resource, File(file.canonicalPath))
        file.refresh(false, true)
    }

    private fun create(resource: HasMetadata, file: File) {
        val content = Serialization.asYaml(resource)
        FileUtils.write(file, content, StandardCharsets.UTF_8, false)
    }

    fun delete(file: VirtualFile) {
        WriteAction.compute<Unit, Exception> { file.delete(this) }
    }

    private fun getName(resource: HasMetadata): String {
        val name = when (resource) {
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