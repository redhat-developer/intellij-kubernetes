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

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
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
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ReloadNotification
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

object ResourceEditor {

    private val KEY_CLUSTER_RESOURCE = Key<ClusterResource>(ClusterResource::class.java.name)
    private val resourceModel = ServiceManager.getService(IResourceModel::class.java)

    @Throws(IOException::class)
    fun open(resource: HasMetadata, project: Project): FileEditor? {
        val file = ResourceFile.replace(resource) ?: return null
        val editors = FileEditorManager.getInstance(project).openFile(file, true, true)
        return editors.getOrNull(0)
    }

    fun close(file: VirtualFile, project: Project) {
        FileEditorManager.getInstance(project).closeFile(file)
        val editor = getEditor(file, project) ?: return
        dispose(editor, file, project)
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

    fun reloadEditor(resource: HasMetadata, editor: FileEditor, project: Project) {
        ApplicationManager.getApplication().invokeAndWait {
            val file = editor.file
            if (file != null) {
                reloadEditor(resource, file)
                updateEditor(editor, project)
            }
        }
    }

    private fun reloadEditor(resource: HasMetadata, file: VirtualFile?) {
        if (file == null) {
            return
        }
        ResourceFile.replace(resource, file)
        FileDocumentManager.getInstance().reloadFiles(file)
    }

    private fun getEditor(file: VirtualFile?, project: Project): FileEditor? {
        if (file == null) {
            return null
        }
        return FileEditorManager.getInstance(project).getEditors(file).firstOrNull()
    }

    fun getResourceFile(editor: FileEditor): VirtualFile? {
        val document = getDocument(editor) ?: return null
        return getResourceFile(document)
    }

    fun getResourceFile(document: Document): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(document)
    }

    private fun getResourceFromFile(editor: FileEditor): HasMetadata? {
        val document = getDocument(editor)
        return if (document?.text == null) {
            null
        } else {
            createResource(document.text)
        }
    }

    private fun getDocument(editor: FileEditor): Document? {
        val file = editor.file ?: return null
        return ReadAction.compute<Document, Exception> {
            FileDocumentManager.getInstance().getDocument(file)
        }
    }

    fun updateEditor(editor: FileEditor, project: Project) {
        ErrorNotification.hide(editor, project)
        ReloadNotification.hide(editor, project)
        DeletedNotification.hide(editor, project)
        val clusterResource = getClusterResource(editor, project) ?: return
        val resourceInFile = getResourceFromFile(editor) ?: return
        if (clusterResource.isDeleted()) {
            DeletedNotification.show(editor, resourceInFile, project)
        } else if (clusterResource.isOutdated(resourceInFile)) {
            val resourceInCluster = loadResourceFromCluster(true, editor, project) ?: return
            ReloadNotification.show(editor, resourceInCluster, project)
        }
    }

    private fun isModified(editor: FileEditor): Boolean {
        return ReadAction.compute<Boolean, Exception> {
            editor.isModified
        }
    }

    fun loadResourceFromCluster(forceLatest: Boolean = false, editor: FileEditor, project: Project): HasMetadata? {
        val clusterResource = getClusterResource(editor, project) ?: return null
        return clusterResource.get(forceLatest)
    }

    fun saveToCluster(resource: HasMetadata, editor: FileEditor?, project: Project): HasMetadata? {
        val cluster = getClusterResource(editor, project)
        val saved = cluster?.saveToCluster(resource)
        if (cluster != null
            && !cluster.isSameResource(saved)) {
            // new resource created - different kind, namespace, name
            // drop existing cluster resource, create new one
            cluster.close()
            createClusterResource(saved, editor, project)
        }
        return saved
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
        return getClusterResource(editor, project)?.contextName
    }

    private fun getClusterResource(editor: FileEditor?, project: Project): ClusterResource? {
        if (editor == null) {
            return null
        }
        var cluster: ClusterResource? = editor.getUserData(KEY_CLUSTER_RESOURCE)
        if (cluster == null) {
            val resource = getResourceFromFile(editor)
            cluster = createClusterResource(resource, editor, project)
        }
        return cluster
    }

    private fun createClusterResource(resource: HasMetadata?, editor: FileEditor?, project: Project): ClusterResource? {
        if (resource == null
            || editor == null) {
            return null
        }
        val cluster = createClusterResource(resource) ?: return null
        editor.putUserData(KEY_CLUSTER_RESOURCE, cluster)
        cluster.addListener(onResourceChanged(editor, project))
        cluster.watch()
        return cluster
    }

    private fun createClusterResource(resource: HasMetadata?): ClusterResource? {
        var clusterResource: ClusterResource? = null
        if (resource != null) {
            // we're using the current context (and the namespace in the resource).
            // This may be wrong for editors that are restored after IDE restart
            // TODO: save context as [FileAttribute] for the file so that it can be restored
            val context = resourceModel.getCurrentContext()
            if (context != null) {
                clusterResource = ClusterResource(resource, context.context.name)
            }
        }
        return clusterResource
    }

    fun createResource(jsonYaml: String): HasMetadata? {
        return try {
            createResource<HasMetadata>(jsonYaml)
        } catch (e: KubernetesClientException) {
            if (e.cause is MismatchedInputException) {
                // invalid json
                null
            } else {
                // unknown type
                return try {
                    createResource<GenericCustomResource>(jsonYaml)
                } catch (e: java.lang.RuntimeException) {
                    null
                }
            }
        } catch (e: RuntimeException) {
            // invalid json/Yaml
            null
        }
    }

    private fun onResourceChanged(editor: FileEditor, project: Project): ModelChangeObservable.IResourceChangeListener {
        return object : ModelChangeObservable.IResourceChangeListener {
            override fun added(added: Any) {
            }

            override fun removed(removed: Any) {
                updateEditor(editor, project)
            }

            override fun modified(modified: Any) {
                updateEditor(editor, project)
            }
        }
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
                virtualFile?.refresh(false, false)
                enableNonProjectFileEditing(virtualFile)
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
            return "$name.$EXTENSION"
        }
    }

    fun enableNonProjectFileEditing(file: VirtualFile?) {
        file?.putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
    }
}

fun FileEditor.showNotification(key: Key<JComponent>, panelFactory: () -> EditorNotificationPanel, project: Project) {
    if (getUserData(key) != null) {
        // already shown
        hideNotification(key, project)
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
