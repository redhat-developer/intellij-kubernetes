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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ReloadNotification
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionMapping
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata.HasMetadataResource
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import com.redhat.devtools.intellij.kubernetes.model.util.createClients
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import org.apache.commons.io.FileUtils
import org.jetbrains.yaml.YAMLFileType
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.function.Supplier
import javax.swing.JComponent

object ResourceEditor {

    private val KEY_CLUSTER_RESOURCE = Key<ClusterResource>(ClusterResource::class.java.name)
    private val KEY_CLIENTS = Key<Clients<out KubernetesClient>>(Clients::class.java.name)
    private val resourceModel = ServiceManager.getService(IResourceModel::class.java)

    @Throws(IOException::class)
    fun open(resource: HasMetadata, project: Project): FileEditor? {
        val file = ResourceFile.replace(resource) ?: return null
        val editors = FileEditorManager.getInstance(project).openFile(file, true, true)
        val editor = editors.getOrNull(0)
        createClusterResource(resource, editor, project)
        return editor
    }

    fun onClosed(file: VirtualFile) {
        if (!isResourceFile(file)) {
            return
        }
        ResourceFile.delete(file)
    }

    fun onBeforeClosed(editor: FileEditor?, project: Project?) {
        if (editor == null
            || project == null
            || !isResourceEditor(editor)) {
            return
        }
        getClusterResource(editor)?.close()
    }

    fun updateEditor(editor: FileEditor, project: Project) {
        try {
            val resource = createResource(editor) ?: return
            val oldClusterResource = getClusterResource(editor)
            val clusterResource = getOrCreateClusterResource(resource, editor, project) ?: return
            if (clusterResource != oldClusterResource) {
                renameEditor(editor, resource)
            }
            showNotifications(oldClusterResource, clusterResource, editor, resource, project)
        } catch (e: ResourceException) {
            showErrorNotification(editor, project, e)
        }
    }

    private fun showNotifications(
        oldClusterResource: ClusterResource?,
        clusterResource: ClusterResource,
        editor: FileEditor,
        resource: HasMetadata,
        project: Project
    ) {
        hideNotifications(editor, project)
        if (oldClusterResource != null
            && oldClusterResource.isDeleted()
            && !clusterResource.exists()) {
            DeletedNotification.show(editor, resource, project)
        } else if (clusterResource.isOutdated(resource)) {
            ReloadNotification.show(editor, resource, project)
        } else if (clusterResource.canPush(resource)) {
            PushNotification.show(editor, resource, project)
        }
    }

    private fun showErrorNotification(editor: FileEditor, project: Project, e: Throwable) {
        hideNotifications(editor, project)
        ErrorNotification.show(editor, project,
            e.message ?: "", e.cause?.message)
    }

    private fun hideNotifications(editor: FileEditor, project: Project) {
        ErrorNotification.hide(editor, project)
        ReloadNotification.hide(editor, project)
        DeletedNotification.hide(editor, project)
        PushNotification.hide(editor, project)
    }

    fun isResourceEditor(editor: FileEditor?): Boolean {
        if (editor == null) {
            return false
        }
        return isResourceFile(editor.file)
    }

    fun reloadEditor(resource: HasMetadata, editor: FileEditor) {
        UIHelper.executeInUI {
            val file = editor.file
            if (file != null) {
                reloadEditor(resource, file)
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

    fun getResourceFile(editor: FileEditor): VirtualFile? {
        return editor.file
    }

    fun getResourceFile(document: Document): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(document)
    }

    private fun isResourceFile(file: VirtualFile?): Boolean {
        return ResourceFile.matches(file)
    }

    private fun createResource(editor: FileEditor): HasMetadata? {
        val document = getDocument(editor)
        return if (document?.text == null) {
            null
        } else {
            val clients = getOrCreateClients(editor)
            if (clients == null) {
                null
            } else {
                createResource(document.text, clients)
            }
        }
    }

    private fun createResource(jsonYaml: String, clients: Clients<out KubernetesClient>): HasMetadata {
        try {
            val resource = createResource<HasMetadataResource>(jsonYaml)
            val definitions = CustomResourceDefinitionMapping.getDefinitions(clients.get())
            return if (CustomResourceDefinitionMapping.isCustomResource(resource, definitions)) {
                createResource<GenericCustomResource>(jsonYaml)
            } else {
                createResource(jsonYaml)
            }
        } catch (e: RuntimeException) {
            throw ResourceException("Invalid yaml/json", e)
        }
    }

    private fun getDocument(editor: FileEditor): Document? {
        val file = editor.file ?: return null
        return ReadAction.compute<Document, Exception> {
            FileDocumentManager.getInstance().getDocument(file)
        }
    }

    fun loadResourceFromCluster(forceLatest: Boolean = false, editor: FileEditor): HasMetadata? {
        val clusterResource = getClusterResource(editor) ?: return null
        return clusterResource.get(forceLatest)
    }

    fun existsOnCluster(editor: FileEditor): Boolean {
        return getClusterResource(editor)?.exists() ?: return false
    }

    fun isOutdated(editor: FileEditor): Boolean {
        val resource = createResource(editor)
        return getClusterResource(editor)?.isOutdated(resource) ?: return false
    }

    private fun isModified(editor: FileEditor): Boolean {
        val resource = createResource(editor)
        return getClusterResource(editor)?.isModified(resource) ?: return false
    }

    fun push(editor: FileEditor, project: Project) {
        try {
            val editorResource = createResource(editor) ?: return
            try {
                val cluster = getClusterResource(editor)
                if (isSameResourceOnCluster(editorResource, cluster)) {
                    pushResource(editorResource, cluster, editor, project)
                } else {
                    val newCluster = createClusterResource(editorResource, editor, project)
                    val updatedResource = pushResource(editorResource, newCluster, editor, project)
                    if (updatedResource != null) {
                        renameEditor(editor, updatedResource)
                    }
                }
            } catch (e: ResourceException) {
                logger<ResourceEditor>().warn(e)
                Notification().error(
                    "Could not save ${editorResource.kind} ${editorResource.metadata.name} to cluster",
                    trimWithEllipsis(e.message, 300) ?: ""
                )
            }
        } catch (e: ResourceException) {
            showErrorNotification(editor, project, e)
        }
    }

    private fun isSameResourceOnCluster(resource: HasMetadata, cluster: ClusterResource?): Boolean {
        val oldResource = cluster?.get(false)
        return oldResource != null
                && oldResource.isSameResource(resource)
    }

    private fun pushResource(resource: HasMetadata, cluster: ClusterResource?, editor: FileEditor, project: Project): HasMetadata? {
        if (cluster == null) {
            return null
        }
        hideNotifications(editor, project)
        val updatedResource = cluster.push(resource) ?: return null
        reloadEditor(updatedResource, editor)
        return updatedResource
    }

    private fun renameEditor(editor: FileEditor, newResource: HasMetadata) {
        val file = getResourceFile(editor)
        if (shouldChangeFilename(newResource, getResourceFile(editor))) {
            ResourceFile.rename(newResource, file)
        }
    }

    private fun shouldChangeFilename(resource: HasMetadata, file: VirtualFile?): Boolean {
        if (file == null) {
            return false
        }
        val existingName = ResourceFile.removeUniqueSuffix(file.name)
        val newName = ResourceFile.getFile(resource).name
        return existingName != newName
    }

    fun startWatch(editor: FileEditor?, project: Project) {
        if (editor == null) {
            return
        }
        val resource = createResource(editor) ?: return
        val clusterResource = getOrCreateClusterResource(resource, editor, project) ?: return
        clusterResource.watch()
    }

    fun stopWatch(editor: FileEditor?) {
        val clusterResource = getClusterResource(editor) ?: return
        clusterResource.stopWatch()
    }

    fun enableNonProjectFileEditing(file: VirtualFile?) {
        file?.putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
    }

    private fun getClusterResource(editor: FileEditor?): ClusterResource? {
        if (editor == null) {
            return null
        }
        return editor.getUserData(KEY_CLUSTER_RESOURCE)
    }

    private fun getOrCreateClusterResource(resource: HasMetadata, editor: FileEditor?, project: Project): ClusterResource? {
        var cluster = getClusterResource(editor)
        if (cluster == null
            || !cluster.isSameResource(resource)) {
            cluster = createClusterResource(resource, editor, project)
        }
        return cluster
    }

    private fun createClusterResource(resource: HasMetadata?, editor: FileEditor?, project: Project): ClusterResource? {
        if (editor == null) {
            return null
        }
        getClusterResource(editor)?.close()
        if (resource == null) {
            return null
        }
        val clients = getOrCreateClients(editor)
        val clusterResource = createClusterResource(resource, clients) ?: return null
        editor.putUserData(KEY_CLUSTER_RESOURCE, clusterResource)
        clusterResource.addListener(onResourceChanged(editor, project))
        clusterResource.watch()
        return clusterResource
    }

    private fun createClusterResource(resource: HasMetadata?, clients: Clients<out KubernetesClient>?): ClusterResource? {
        var clusterResource: ClusterResource? = null
        if (resource != null
            && clients != null) {
            clusterResource = ClusterResource(resource, clients)
        }
        return clusterResource
    }

    private fun getOrCreateClients(editor: FileEditor?): Clients<out KubernetesClient>? {
        if (editor == null) {
            return null
        }
        var clients = editor.getUserData(KEY_CLIENTS)
        if (clients == null) {
            // we're using the current context (and the namespace in the resource).
            // This may be wrong for editors that are restored after IDE restart
            // TODO: save context as [FileAttribute] for the file so that it can be restored
            val context = resourceModel.getCurrentContext()
            if (context != null) {
                clients = ::createClients.invoke(context.context.context.cluster)
                editor.putUserData(KEY_CLIENTS, clients)
            }
        }
        return clients
    }

    private fun onResourceChanged(editor: FileEditor, project: Project): ModelChangeObservable.IResourceChangeListener {
        return object : ModelChangeObservable.IResourceChangeListener {
            override fun added(added: Any) {
            }

            override fun removed(removed: Any) {
                updateEditor(editor, project)
            }

            override fun modified(modified: Any) {
                val resource = modified as? HasMetadata
                UIHelper.executeInUI {
                    if (resource != null
                        && !isModified(editor)) {
                        reloadEditor(resource, editor)
                    } else {
                        updateEditor(editor, project)
                    }
                }
            }
        }
    }

    private object ResourceFile {

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
