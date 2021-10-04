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
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.io.exists
import com.intellij.util.ui.EdtInvocationManager
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.addAddendum
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import com.redhat.devtools.intellij.kubernetes.model.util.removeAddendum
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import io.fabric8.openshift.api.model.Project
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.jetbrains.yaml.YAMLFileType
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier

open class ResourceFile protected constructor(
    open val virtualFile: VirtualFile
) {

    companion object Factory {
        const val EXTENSION = YAMLFileType.DEFAULT_EXTENSION

        @JvmStatic
        val TEMP_FOLDER: Path = Paths.get(FileUtil.resolveShortWindowsName(FileUtil.getTempDirectory()), "intellij-kubernetes")

        @JvmStatic
        fun create(resource: HasMetadata): ResourceFile? {
            val virtualFile = createVirtualFile(getPathFor(resource)) ?: return null
            return ResourceFile(virtualFile)
        }

        @JvmStatic
        fun create(virtualFile: VirtualFile?): ResourceFile? {
            if (virtualFile == null
                || !isResourceFile(virtualFile)) {
                return null
            }
            return ResourceFile(virtualFile)
        }

        @JvmStatic
        fun create(path: Path): ResourceFile? {
            return create(createVirtualFile(path))
        }

        private fun createVirtualFile(path: Path): VirtualFile? {
            var filePath = path
            return try {
                if (filePath.exists()) {
                    filePath = addAddendum(path)
                }
                Files.createDirectories(filePath.parent)
                Files.createFile(filePath)
                VfsUtil.findFile(filePath, true)
            } catch(e: IOException) {
                logger<ResourceFile>().warn("Could not create file $path")
                Notification().error("Could not open editor", "Could not create file $path: ${trimWithEllipsis(e.message, 50)}")
                null
            }
        }

        /**
         * Returns a path for the given resource. The path used is a folder intellij-kubernetes in the system temp folder.
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

        /**
         * Returns `true` if the given file is
         * * on the local filesystem
         * * is a yaml/json file
         * * contains yaml/json for a kubernetes resource
         *
         * @param file the file which should checked whether it can be handled
         * @return true if this class can handle the given file
         */
        fun isResourceFile(file: VirtualFile?): Boolean {
            if (file == null
                || !isLocalFile(file)
                || !isYamlOrJson(file)) {
                return false
            }
            return isKubernetesResource(file)
        }

        private fun isYamlOrJson(file: VirtualFile?): Boolean {
            if (file == null
                || true == file.extension?.isBlank()) {
                return false
            }
            val type = getFileType(file)
            return YAMLFileType.YML == type
                    || JsonFileType.INSTANCE == type
        }

        fun getFileType(file: VirtualFile) =
            FileTypeRegistry.getInstance().getFileTypeByExtension(file.extension!!)

        private fun isKubernetesResource(file: VirtualFile): Boolean {
            val jsonYaml = IOUtils.toString(file.inputStream, Charset.defaultCharset())
            return try {
                createResource<KubernetesResource?>(jsonYaml) != null
            } catch (e: RuntimeException) {
                false
            }
        }

        private fun isLocalFile(file: VirtualFile): Boolean {
            return file.isInLocalFileSystem
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
        if (EdtInvocationManager.getInstance().isEventDispatchThread) {
            executeReadAction {
                virtualFile.refresh(false, false)
            }
        } else {
            virtualFile.refresh(false, false)
        }
        enableNonProjectFileEditing()
        return virtualFile
    }

    /**
     * Deletes the file that this class is dealing with.
     */
    fun delete() {
        executeWriteAction {
            virtualFile.delete(this)
        }
    }

    fun hasEqualBasePath(file: ResourceFile?): Boolean {
        if (file == null) {
            return false
        }
        return getBasePath() == file.getBasePath()
    }

    fun getBasePath(): String {
        return removeAddendum(PathUtil.getFileName(virtualFile.path))
    }

    fun isTemporaryFile(): Boolean {
        return virtualFile.path.startsWith(TEMP_FOLDER.toString())
    }

    open fun enableNonProjectFileEditing() {
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