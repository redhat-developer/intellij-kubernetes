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

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor.enableNonProjectFileEditing
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import io.fabric8.openshift.api.model.Project
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.function.Supplier

object ResourceFile {

    private const val EXTENSION = YAMLFileType.DEFAULT_EXTENSION
    private val TEMP_FOLDER = Paths.get(FileUtils.getTempDirectoryPath(), "intellij-kubernetes")

    fun matches(file: VirtualFile?): Boolean {
        return file?.path?.endsWith(EXTENSION, true) ?: false
                && file?.path?.startsWith(TEMP_FOLDER.toString()) ?: false
    }

    fun replace(resource: HasMetadata): VirtualFile? {
        return replace(resource, getFile(resource))
    }

    fun replace(resource: HasMetadata, file: VirtualFile): VirtualFile? {
        return replace(resource, VfsUtilCore.virtualToIoFile(file))
    }

    fun delete(file: VirtualFile) {
        UIHelper.executeInUI {
            WriteAction.compute<Unit, Exception> {
                file.delete(this)
            }
        }
    }

    fun rename(resource: HasMetadata, file: VirtualFile?) {
        if (file == null) {
            return
        }
        UIHelper.executeInUI {
            WriteAction.compute<Unit, Exception> {
                val newFile = addUniqueSuffix(getFile(resource))
                file.rename(this, newFile.name)
                file.refresh(true, true)
            }
        }
    }

    private fun replace(resource: HasMetadata, file: File): VirtualFile? {
        return UIHelper.executeInUI(Supplier {
            WriteAction.compute<VirtualFile?, Exception> {
                val content = Serialization.asYaml(resource)
                FileUtils.write(file, content, StandardCharsets.UTF_8, false)
                val virtualFile = VfsUtil.findFileByIoFile(file, true)
                virtualFile?.refresh(false, false)
                enableNonProjectFileEditing(virtualFile)
                virtualFile
            }
        })
    }

    fun getFile(resource: HasMetadata): File {
        val name = getName(resource)
        return File(TEMP_FOLDER.toString(), name)
    }

    private fun getName(resource: HasMetadata): String {
        val name = when (resource) {
            is Namespace,
            is Project -> resource.metadata.name
            else -> {
                if (resource.metadata.namespace != null) {
                    "${resource.metadata.name}@${resource.metadata.namespace}"
                } else {
                    resource.metadata.name
                }
            }
        }
        return "$name.$EXTENSION"
    }

    private fun addUniqueSuffix(file: File): File {
        if (!file.exists()) {
            return file
        }
        val name = FileUtilRt.getNameWithoutExtension(file.absolutePath)
        val suffix = FileUtilRt.getExtension(file.absolutePath)
        var i = 1
        var unused: File?
        do {
            unused = File("$name(${i++}).$suffix")
        } while (unused!!.exists())
        return unused
    }

    fun removeUniqueSuffix(name: String): String {
        val suffixStart = name.indexOf('(')
        if (suffixStart < 0) {
            return name
        }
        val suffixStop = name.indexOf(')')
        if (suffixStop < 0) {
            return name
        }
        return name.removeRange(suffixStart, suffixStop + 1)
    }
}