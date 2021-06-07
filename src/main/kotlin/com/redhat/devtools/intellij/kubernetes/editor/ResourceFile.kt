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

    /**
     * Returns `true` if the given file is a file that this class can handle.
     *
     * @param file the file which should checked whether it can be handled
     * @return true if this class can handle the given file
     */
    fun isResourceFile(file: VirtualFile?): Boolean {
        return file?.path?.endsWith(EXTENSION, true) ?: false
                && file?.path?.startsWith(TEMP_FOLDER.toString()) ?: false
    }

    /**
     * Replaces the content of the given file with the given resource.
     *
     * @param resource the resource that should be set as content of the given file
     * @param file the file whose content should be replaced
     * @return the virtual file whose content was changed
     */
    fun replaceContent(resource: HasMetadata, file: VirtualFile): VirtualFile? {
        return replaceContent(resource, VfsUtilCore.virtualToIoFile(file))
    }

    /**
     * Replaces the content of the given file with the given resource.
     *
     * @param resource the resource that should be set as content of the given file
     * @param file the file whose content should be replaced
     * @return the virtual file whose content was changed
     */
    fun replaceContent(resource: HasMetadata, file: File): VirtualFile? {
        val content = Serialization.asYaml(resource)
        FileUtils.write(file, content, StandardCharsets.UTF_8, false)
        return UIHelper.executeInUI(Supplier {
            WriteAction.compute<VirtualFile?, Exception> {
                val virtualFile = VfsUtil.findFileByIoFile(file, true)
                virtualFile?.refresh(false, false)
                enableNonProjectFileEditing(virtualFile)
                virtualFile
            }
        })
    }

    /**
     * Deletes the given file.
     *
     * @param file the file to delete
     */
    fun delete(file: VirtualFile) {
        UIHelper.executeInUI {
            WriteAction.compute<Unit, Exception> {
                file.delete(this)
            }
        }
    }

    /**
     * Renames the given file so that it can hold the given resource.
     *
     * @param resource the content for the given file
     * @param file the file that should be renamed so that it can hold the given resource
     */
    fun rename(resource: HasMetadata, file: VirtualFile?) {
        if (file == null) {
            return
        }
        val newFile = addAddendum(getFileFor(resource))
        UIHelper.executeInUI {
            WriteAction.compute<Unit, Exception> {
                file.rename(this, newFile.name)
                file.refresh(true, true)
            }
        }
    }

    /**
     * Returns a file for the given resource.
     *
     * @param resource the resource that the file for should be returned for
     * @return the file for the given resource
     */
    fun getFileFor(resource: HasMetadata): File {
        val name = getFilenameFor(resource)
        return File(TEMP_FOLDER.toString(), name)
    }

    private fun getFilenameFor(resource: HasMetadata): String {
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

    /**
     * Adds an addendum to the name of the given file if a file with the name of the given file exists.
     * ex. jedi-sword(2).yml where (2) is the addendum that's added so that the filename is unique.
     *
     * @param file the file whose filename should get a unique addendum
     * @return the file with/or without a unique suffix
     */
    private fun addAddendum(file: File): File {
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

    /**
     * Returns the filename without the the addendum that was added to make the given filename unique.
     * Returns the filename as is if it has no addendum.
     * ex. jedi-sword(2).yml where (2) is the suffix that was added so that the filename is unique.
     *
     * @param filename the filename that should be striped of a unique suffix
     * @return the filename without the unique suffix if it exists
     *
     * @see [addAddendum]
     */
    fun removeAddendum(filename: String): String {
        val suffixStart = filename.indexOf('(')
        if (suffixStart < 0) {
            return filename
        }
        val suffixStop = filename.indexOf(')')
        if (suffixStop < 0) {
            return filename
        }
        return filename.removeRange(suffixStart, suffixStop + 1)
    }
}