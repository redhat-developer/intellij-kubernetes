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

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.common.utils.UIHelper
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import io.fabric8.openshift.api.model.Project
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier

open class ResourceFile protected constructor(
    open val path: Path,
    protected open var _virtualFile: VirtualFile? = null
) {

    companion object {
        const val EXTENSION = YAMLFileType.DEFAULT_EXTENSION
        @JvmStatic
        val TEMP_FOLDER: Path = Paths.get(FileUtils.getTempDirectoryPath(), "intellij-kubernetes")

        fun create(resource: HasMetadata): ResourceFile? {
            val path = getPathFor(resource)
            return create(path)
        }

        fun create(virtualFile: VirtualFile?): ResourceFile? {
            if (virtualFile == null) {
                return null
            }
            return create(VfsUtil.virtualToIoFile(virtualFile).toPath(), virtualFile)
        }

        fun create(path: Path, virtualFile: VirtualFile? = null): ResourceFile? {
            if (!isResourceFile(path)) {
                return null
            }
            return ResourceFile(path, virtualFile)
        }

        /**
         * Returns `true` if the given file is a file that this class can handle.
         *
         * @param file the file which should checked whether it can be handled
         * @return true if this class can handle the given file
         */
        fun isResourceFile(file: VirtualFile?): Boolean {
            if (file == null) {
                return false
            }
            return isResourceFile(VfsUtil.virtualToIoFile(file).toPath())
        }

        /**
         * Returns `true` if the given file is a file that this class can handle.
         *
         * @param path the file which should checked whether it can be handled
         * @return true if this class can handle the given file
         */
        fun isResourceFile(path: Path?): Boolean {
            return path?.toString()?.endsWith(EXTENSION, true) ?: false
                    && path?.toString()?.startsWith(TEMP_FOLDER.toString()) ?: false
        }

        /**
         * Returns a path for the given resource.
         *
         * @param resource the resource that the file for should be returned for
         * @return the path for the given resource
         */
        private fun getPathFor(resource: HasMetadata): Path {
            val name = getFilenameFor(resource)
            return Paths.get(TEMP_FOLDER.toString(), name)
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

    }

    protected open val virtualFile: VirtualFile?
        get() {
            if (_virtualFile == null) {
                // lookup virtual file if not present yet
                _virtualFile = findVirtualFile(path)
            }
            return _virtualFile
        }

    /**
     * Writes the given resource to file in this ResourceFile. A new file is created if it doesn't exist yet.
     *
     * @param resource the resource that should be set as content of the given file
     * @return the virtual file whose content was changed
     */
    fun write(resource: HasMetadata): VirtualFile? {
        val content = Serialization.asYaml(resource)
        write(content, path)
        return executeReadAction {
            virtualFile?.refresh(false, false)
            enableNonProjectFileEditing()
            virtualFile
        }
    }

    /**
     * Deletes the file that this class is dealing with.
     */
    fun delete() {
        executeWriteAction {
            virtualFile?.delete(this)
        }
    }

    /**
     * Renames this file so that it can hold the given resource.
     *
     * @param resource the content for the given file
     */
    fun rename(resource: HasMetadata) {
        val newPath = addAddendum(getPathFor(resource))
        executeWriteAction {
            virtualFile?.rename(this, newPath.fileName.toString())
            virtualFile?.refresh(true, true)
        }
    }

    /**
     * Adds an addendum to the name of the given file if a file with the same name already exists.
     * Returns the path as is if the path doesn't exist yet.
     * ex. jedi-sword(2).yml where (2) is the addendum that's added so that the filename is unique.
     *
     * @param path the file whose filename should get a unique addendum
     * @return the file with/or without a unique suffix
     */
    private fun addAddendum(path: Path): Path {
        if (!exists(path)) {
            return path
        }
        val name = FileUtilRt.getNameWithoutExtension(path.toString())
        val suffix = FileUtilRt.getExtension(path.toString())
        val parent = path.parent
        var i = 1
        var unused: Path?
        do {
            unused = parent.resolve("$name(${i++}).$suffix")
        } while (unused != null && exists(unused))
        return unused!!
    }

    fun hasEqualBasePath(file: ResourceFile?): Boolean {
        if (file == null) {
            return false
        }
        return getBasePath() == file.getBasePath()
    }

    private fun getBasePath(): String {
        return removeAddendum(path.fileName.toString())
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
    private fun removeAddendum(filename: String): String {
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

    open fun enableNonProjectFileEditing() {
        virtualFile?.putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
    }

    protected open fun write(content: String, path: Path) {
        FileUtils.write(path.toFile(), content, StandardCharsets.UTF_8, false)
    }

    protected open fun exists(path: Path): Boolean {
        return Files.exists(path)
    }

    protected open fun findVirtualFile(path: Path): VirtualFile? {
        return VfsUtil.findFile(path, true)
    }

    protected open fun <R> executeReadAction(callable: () -> R): R {
        return UIHelper.executeInUI(Supplier {
            ReadAction.compute<R, java.lang.Exception>(callable)
        })
    }

    protected open fun executeWriteAction(runnable: () -> Unit) {
        UIHelper.executeInUI {
            WriteAction.compute<Unit, java.lang.Exception>(runnable)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourceFile

        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

}