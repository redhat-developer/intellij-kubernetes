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

import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.EDT
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier

/**
 * Helper that offers operations on the [VirtualFile] for a [ResourceEditor]
 */
open class ResourceFile protected constructor(
    open val virtualFile: VirtualFile
) {

    companion object Factory {
        const val EXTENSION = YAMLFileType.DEFAULT_EXTENSION

        @JvmStatic
        val TEMP_FOLDER: Path = Paths.get(FileUtil.resolveShortWindowsName(FileUtil.getTempDirectory()))

        @JvmStatic
        fun create(resource: HasMetadata): ResourceFile? {
            val virtualFile = createTemporaryFile(resource) ?: return null
            return ResourceFile(virtualFile)
        }

        @JvmStatic
        fun create(virtualFile: VirtualFile?): ResourceFile? {
            if (virtualFile == null
                || !isValidType(virtualFile)) {
                return null
            }
            return ResourceFile(virtualFile)
        }

        private fun createTemporaryFile(resource: HasMetadata): VirtualFile? {
            return try {
                val file = FileUtilRt.createTempFile(resource.metadata.name, ".$EXTENSION")
                VfsUtil.findFileByIoFile(file, true)
            } catch(e: IOException) {
                logger<ResourceFile>().warn("Could not create file: ${e.message}", e)
                // https://youtrack.jetbrains.com/issue/IDEA-225226
                Notification()
                    .error("Could create file", "Could not create file for ${resource.kind} '${resource.metadata.name}': ${trimWithEllipsis(e.message, 50)}. Maybe do File > Invalidate Caches.")
                null
            }
        }

        /**
         * Returns `true` if the given file is
         * * on the local filesystem
         * * is a yaml/json file
         * * contains yaml/json for a kubernetes resource
         *
         * @param file the file which should checked whether it can be handled
         * @return true if this class can handle the given file
         */
        fun isValidType(file: VirtualFile?): Boolean {
            return file != null
                    && isLocalFile(file)
                    && isYamlOrJson(file)
        }

        /**
         * Returns `true` if the given file is a temporary file.
         *
         * @param virtualFile to check if it's a temporary file
         * @return true if the given file is a temporary file
         */
        fun isTemporary(virtualFile: VirtualFile?): Boolean {
            return virtualFile != null
                    && virtualFile.path.startsWith(TEMP_FOLDER.toString())
        }

        private fun isYamlOrJson(file: VirtualFile): Boolean {
            if (true == file.extension?.isBlank()) {
                return false
            }
            val type = getFileType(file)
            return YAMLFileType.YML == type
                    || JsonFileType.INSTANCE == type
        }

        /** public for testing purposes **/
        fun getFileType(file: VirtualFile): FileType {
            return FileTypeRegistry.getInstance().getFileTypeByFile(file)
        }

        private fun isLocalFile(file: VirtualFile?): Boolean {
            return file?.isInLocalFileSystem ?: false
        }
    }

    /**
     * Writes the given resource to file in this ResourceFile. A new file is created if it doesn't exist yet.
     *
     * @param resource the resource that should be set as content of the given file
     * @return the virtual file whose content was changed
     */
    fun write(resource: HasMetadata): VirtualFile {
        val content = Serialization.asYaml(resource)
        write(content, VfsUtil.virtualToIoFile(virtualFile).toPath())
        /**
         * When invoking synchronous refresh from a thread other than the event dispatch thread,
         * the current thread must NOT be in a read action, otherwise a deadlock may occur
         */
        if (EDT.isCurrentThreadEdt()) {
            executeReadAction {
                virtualFile.refresh(false, false)
            }
        } else {
            virtualFile.refresh(false, false)
        }
        enableEditingNonProjectFile()
        return virtualFile
    }

    /**
     * Deletes this resource file if it is a temporary file.
     * Does nothing if it's not.
     *
     * @see [isTemporary]
     */
    fun deleteTemporary() {
        if (!isTemporary(virtualFile)) {
            return
        }
        executeWriteAction {
            try {
                if (virtualFile.isValid
                    && virtualFile.exists()) {
                    virtualFile.delete(this)
                }
            } catch (t: Exception) {
                logger<ResourceFile>().warn("Could not delete virtual file ${virtualFile.path}, e")
            }
        }
    }

    /**
     * Returns `true` if the file in this instance is a temporary file.
     *
     * @return true if this resource file is a temporary
     */
    fun isTemporary(): Boolean {
        return Factory.isTemporary(virtualFile)
    }

    open fun enableEditingNonProjectFile() {
        virtualFile.putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
    }

    protected open fun write(content: String, path: Path) {
        executeWriteAction {
            FileUtils.write(path.toFile(), content, StandardCharsets.UTF_8, false)
        }
    }

    protected open fun exists(path: Path): Boolean {
        return Files.exists(path)
    }

    protected open fun <R> executeReadAction(callable: () -> R): R {
        return ReadAction.compute<R, java.lang.Exception>(callable)
    }

    protected open fun executeWriteAction(runnable: () -> Unit) {
        UIHelper.executeInUI(Supplier {
            WriteAction.compute<Unit, java.lang.Exception>(runnable)
        })
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (virtualFile.path != (other as ResourceFile).virtualFile.path) return false

        return true
    }

    override fun hashCode(): Int {
        return virtualFile.path.hashCode()
    }

}