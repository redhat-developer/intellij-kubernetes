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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.context.ActiveContext
import com.redhat.devtools.intellij.kubernetes.model.util.toResource
import com.redhat.devtools.intellij.kubernetes.ui.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.ui.editor.notification.ReloadNotification
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

object ResourceEditor {

    private val KEY_CLUSTER_RESOURCE = Key<ClusterResource>(ClusterResource::class.java.name)

    private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)
    private val resourceModel = ServiceManager.getService(IResourceModel::class.java)

    @Throws(IOException::class)
    fun open(resource: HasMetadata, project: Project): FileEditor? {
        val file = ResourceFile.replace(resource) ?: return null
        val editors = FileEditorManager.getInstance(project).openFile(file, true, true)
        return editors.getOrNull(0)
    }

    fun close(file: VirtualFile, project: Project) {
        FileEditorManager.getInstance(project).closeFile(file)
    }

    fun dispose(editor: FileEditor, file: VirtualFile, project: Project) {
        val clusterResource = getClusterResource(editor, project) ?: return
        clusterResource.close()
        ResourceFile.delete(file)
    }

    fun isResourceEditor(editor: FileEditor): Boolean {
        return isResourceFile(editor.file)
    }

    fun isResourceFile(file: VirtualFile?): Boolean {
        return ResourceFile.matches(file)
    }

    fun replaceFile(resource: HasMetadata, file: VirtualFile, project: Project) {
        ResourceFile.replace(resource, file)
        FileDocumentManager.getInstance().reloadFiles(file)
        val editor = getEditor(file, project) ?: return
        showNotifications(editor, project)
    }

    private fun getEditor(file: VirtualFile?, project: Project): FileEditor? {
        if (file == null) {
            return null
        }
        return FileEditorManager.getInstance(project).getEditors(file).firstOrNull()
    }

    fun getResourceFile(document: Document): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(document)
    }

    fun showNotifications(editor: FileEditor, project: Project) {
        val clusterResource = getClusterResource(editor, project) ?: return
        if (clusterResource.isDeleted()) {
            DeletedNotification.show(editor, clusterResource.get(), project)
            ReloadNotification.hide(editor, project)
        } else if (clusterResource.isUpdated()) {
            ReloadNotification.show(editor, clusterResource.get(), project)
            DeletedNotification.hide(editor, project)
        }
    }

    fun getLatestResource(editor: FileEditor, project: Project): HasMetadata? {
        val clusterResource = getClusterResource(editor, project) ?: return null
        return clusterResource.getLatest()
    }

    fun replaceResource(resource: HasMetadata, editor: FileEditor?, project: Project): HasMetadata? {
        return getClusterResource(editor, project)?.replace(resource)
    }

    fun startWatch(editor: FileEditor?, project: Project) {
        val clusterResource = getClusterResource(editor, project) ?: return
        clusterResource.watch()
    }

    fun stopWatch(editor: FileEditor?, project: Project) {
        val clusterResource = getClusterResource(editor, project) ?: return
        clusterResource.stopWatch()
    }

    fun getContextName(editor: FileEditor?, project: Project): String? {
        val clusterResource = getClusterResource(editor, project)
        return clusterResource?.contextName
    }

    private fun getClusterResource(editor: FileEditor?, project: Project): ClusterResource? {
        if (editor == null) {
            return null
        }
        var clusterResource: ClusterResource? = getUserData(KEY_CLUSTER_RESOURCE, editor)
        if (clusterResource == null) {
            clusterResource = createClusterResource(editor) ?: return null
            putUserData(KEY_CLUSTER_RESOURCE, clusterResource, editor)
            clusterResource.addListener(onResourceChanged(editor, project))
            clusterResource.watch()
        }
        return clusterResource
    }

    private fun createClusterResource(editor: FileEditor): ClusterResource? {
        val file = editor.file ?: return null
        var clusterResource: ClusterResource? = null
        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document?.text != null) {
            val resource = toResource<HasMetadata>(document.text)
            val context = resourceModel.getCurrentContext() as? ActiveContext
            if (context != null
                && resource != null) {
                clusterResource = ClusterResource(resource, context)
            }
        }
        return clusterResource
    }

    private fun onResourceChanged(editor: FileEditor, project: Project): ModelChangeObservable.IResourceChangeListener {
        return object : ModelChangeObservable.IResourceChangeListener {
            override fun added(added: Any) {
            }

            override fun removed(removed: Any) {
                showNotifications(editor, project)
            }

            override fun modified(modified: Any) {
                showNotifications(editor, project)
            }
        }
    }

    private fun <T> getUserData(key: Key<T>, editor: FileEditor?): T? {
        return editor?.getUserData(key)
    }

    private fun <T> putUserData(key: Key<T>, value: T?, editor: FileEditor?) {
        editor?.putUserData(key, value)
    }

    private object ResourceFile {

        private const val EXTENSION = YAMLFileType.DEFAULT_EXTENSION

        fun matches(file: VirtualFile?): Boolean {
            return file?.path?.endsWith(EXTENSION, true) ?: false
                    && file?.path?.startsWith(FileUtils.getTempDirectoryPath()) ?: false
        }

        fun replace(resource: HasMetadata): VirtualFile? {
            return replace(resource, getFile(resource))
        }

        fun replace(resource: HasMetadata, file: VirtualFile): VirtualFile? {
            return replace(resource, VfsUtilCore.virtualToIoFile(file))
        }

        fun delete(file: VirtualFile) {
            WriteAction.compute<Unit, Exception> {
                file.delete(this)
            }
        }

        private fun replace(resource: HasMetadata, file: File): VirtualFile? {
            return WriteAction.compute<VirtualFile?, Exception> {
                val content = Serialization.asYaml(resource)
                FileUtils.write(file, content, StandardCharsets.UTF_8, false)
                val virtualFile = VfsUtil.findFileByIoFile(file, true)
                virtualFile?.putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
                virtualFile
            }
        }

        private fun getFile(resource: HasMetadata): File {
            val name = getName(resource)
            return File(FileUtils.getTempDirectory(), name)
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
            return "$name.${EXTENSION}"
        }
    }
}

fun FileEditor.showNotification(key: Key<JComponent>, panelFactory: () -> EditorNotificationPanel, project: Project) {
    if (this.getUserData(key) != null) {
        // already shown
        return
    }
    val panel = panelFactory.invoke()
    FileEditorManager.getInstance(project).addTopComponent(this, panel)
    this.putUserData(key, panel)
}

fun FileEditor.hideNotification(key: Key<JComponent>, project: Project) {
    val panel = this.getUserData(key) ?: return
    FileEditorManager.getInstance(project).removeTopComponent(this, panel)
    this.putUserData(key, null)
}


