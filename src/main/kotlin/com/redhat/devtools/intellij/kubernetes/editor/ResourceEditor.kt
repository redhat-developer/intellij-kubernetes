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
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
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
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import java.io.IOException

class ResourceEditor(
    private val editor: FileEditor,
    private val project: Project
) {

    constructor(resource: HasMetadata?, editor: FileEditor, project: Project): this(editor, project) {
        this.resource = resource
    }

    companion object {
        private val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)
        private val KEY_CLIENTS = Key<Clients<out KubernetesClient>>(Clients::class.java.name)

        fun isResourceEditor(editor: FileEditor?): Boolean {
            return ResourceFile.isResourceFile(editor?.file)
        }

        @Throws(IOException::class)
        fun open(resource: HasMetadata, project: Project): ResourceEditor? {
            val file = ResourceFile.create(resource)?.write(resource) ?: return null
            val editor = FileEditorManager.getInstance(project)
                .openFile(file, true, true)
                .getOrNull(0)
            return create(resource, editor, project)
        }

        fun get(document: Document): ResourceEditor? {
            val file = getResourceFile(document) ?: return null
            val projectAndEditor = getProjectAndEditor(file) ?: return null
            return get(projectAndEditor.editor, projectAndEditor.project)
        }

        fun get(editor: FileEditor?, project: Project?): ResourceEditor? {
            if (false == editor?.isValid) {
                return null
            }
            return editor?.getUserData(KEY_RESOURCE_EDITOR)
                ?: create(editor, project)
        }

        private fun create(editor: FileEditor?, project: Project?): ResourceEditor? {
            return create(null, editor, project)
        }

        private fun create(resource: HasMetadata?, editor: FileEditor?, project: Project?): ResourceEditor? {
            if (editor == null
                || !isResourceEditor(editor)
                || project == null
            ) {
                return null
            }
            val resourceEditor = ResourceEditor(resource, editor, project)
            editor.putUserData(KEY_RESOURCE_EDITOR, resourceEditor)
            return resourceEditor
        }

        private fun getResourceFile(document: Document): VirtualFile? {
            val file = FileDocumentManager.getInstance().getFile(document)
            if (!ResourceFile.isResourceFile(file)) {
                return null
            }
            return file
        }

        private fun getProjectAndEditor(file: VirtualFile): ProjectAndEditor? {
            return ProjectManager.getInstance().openProjects
                .filter { project -> project.isInitialized && !project.isDisposed }
                .flatMap { project ->
                    FileEditorManager.getInstance(project).getEditors(file).toList()
                        .mapNotNull { editor -> ProjectAndEditor(project, editor) }
                }
                .firstOrNull()
        }

        private class ProjectAndEditor(val project: Project, val editor: FileEditor)

    }

    private var resource: HasMetadata? = null
    private val clients: Clients<out KubernetesClient>?
        get() {
            var clients = editor.getUserData(KEY_CLIENTS)
            if (clients == null) {
                // we're using the current context (and the namespace in the resource).
                // This may be wrong for editors that are restored after IDE restart
                // TODO: save context as [FileAttribute] for the file so that it can be restored
                val context = ServiceManager.getService(IResourceModel::class.java).getCurrentContext()
                if (context != null) {
                    clients = ::createClients.invoke(context.context.context.cluster)
                    editor.putUserData(KEY_CLIENTS, clients)
                }
            }
            return clients
        }

    private var oldClusterResource: ClusterResource? = null
    private var _clusterResource: ClusterResource? = null
    private val clusterResource: ClusterResource?
        get() {
            synchronized(this) {
                if (_clusterResource == null
                    || false == _clusterResource?.isSameResource(resource)) {
                    oldClusterResource = _clusterResource
                    oldClusterResource?.close()
                    _clusterResource = createClusterResource(resource, clients)
                }
                return _clusterResource
            }
        }

    fun updateEditor() {
        try {
            this.resource = createResource(editor) ?: return
            updateEditor(resource, clusterResource, oldClusterResource)
        } catch (e: ResourceException) {
            showErrorNotification(editor, project, e)
        }
    }

    private fun updateEditor(resource: HasMetadata?, clusterResource: ClusterResource?, oldClusterResource: ClusterResource?) {
            if (resource == null
                || clusterResource == null) {
                return
            }
            if (clusterResource != oldClusterResource) {
                // new cluster resource was created, resource has thus changed
                renameEditor(resource, editor)
            }
            showNotifications(clusterResource, resource, editor, project)
    }

    private fun showNotifications(
        clusterResource: ClusterResource,
        resource: HasMetadata,
        editor: FileEditor,
        project: Project
    ) {
        hideNotifications(editor, project)
        when {
            clusterResource.isDeleted()
                    && !clusterResource.isModified(resource) ->
                DeletedNotification.show(editor, resource, project)
            clusterResource.isOutdated(resource) ->
                ReloadNotification.show(editor, resource, project)
            clusterResource.canPush(resource) ->
                PushNotification.show(editor, project)
        }
    }

    private fun showErrorNotification(editor: FileEditor, project: Project, e: Throwable) {
        hideNotifications(editor, project)
        ErrorNotification.show(
            editor, project,
            e.message ?: "", e.cause?.message
        )
    }

    private fun hideNotifications(editor: FileEditor, project: Project) {
        ErrorNotification.hide(editor, project)
        ReloadNotification.hide(editor, project)
        DeletedNotification.hide(editor, project)
        PushNotification.hide(editor, project)
    }

    fun reloadEditor() {
        val resource = clusterResource?.get(false) ?: return
        reloadEditor(resource)
    }

    private fun reloadEditor(resource: HasMetadata) {
        UIHelper.executeInUI {
            if (editor.file != null) {
                val file = ResourceFile.create(editor.file)?.write(resource)
                if (file != null) {
                    FileDocumentManager.getInstance().reloadFiles(file)
                }
            }
        }
    }

    fun existsOnCluster(): Boolean {
        return clusterResource?.exists() ?: false
    }

    fun isOutdated(): Boolean {
        return clusterResource?.isOutdated(resource) ?: false
    }

    fun push() {
        try {
            this.resource = createResource(editor) ?: return
            push(resource, clusterResource)
        } catch (e: Exception) {
            showErrorNotification(editor, project, e)
        }
    }

    private fun push(resource: HasMetadata?, clusterResource: ClusterResource?) {
        try {
            if (resource == null
                || clusterResource == null) {
                return
            }
            hideNotifications(editor, project)
            val updatedResource = clusterResource.push(resource) ?: return
            reloadEditor(updatedResource)
        } catch (e: ResourceException) {
            logger<ResourceEditor>().warn(e)
            Notification().error(
                "Could not save ${
                    if (resource != null) {
                        "${resource.kind} ${resource.metadata.name}"
                    } else {
                        ""
                    }
                } to cluster",
                trimWithEllipsis(e.message, 300) ?: ""
            )
        }
    }

    private fun renameEditor(resource: HasMetadata, editor: FileEditor) {
        val file = ResourceFile.create(editor.file) ?: return
        val newFile = ResourceFile.create(resource)
        if (!file.hasEqualBasePath(newFile)) {
            file.rename(resource)
        }
    }

    fun startWatch(): ResourceEditor {
        clusterResource?.watch()
        return this
    }

    fun stopWatch() {
        clusterResource?.stopWatch()
    }

    private fun createClusterResource(resource: HasMetadata?, clients: Clients<out KubernetesClient>?): ClusterResource? {
        if (resource == null
            || clients == null) {
            return null
        }
        val clusterResource = ClusterResource(resource, clients)
        clusterResource.addListener(onResourceChanged())
        clusterResource.watch()
        return clusterResource
    }

    private fun onResourceChanged(): ModelChangeObservable.IResourceChangeListener {
        return object : ModelChangeObservable.IResourceChangeListener {
            override fun added(added: Any) {
            }

            override fun removed(removed: Any) {
                updateEditor()
            }

            override fun modified(modified: Any) {
                updateEditor()
            }
        }
    }

    private fun createResource(editor: FileEditor): HasMetadata? {
        return createResource(getDocument(editor))
    }

    private fun createResource(document: Document?): HasMetadata? {
        return if (document?.text == null) {
            null
        } else {
            val clients = clients ?: return null
            createResource(document.text, clients)
        }
    }

    private fun getDocument(editor: FileEditor): Document? {
        val file = editor.file ?: return null
        return ReadAction.compute<Document, Exception> {
            FileDocumentManager.getInstance().getDocument(file)
        }
    }

    private fun createResource(jsonYaml: String, clients: Clients<out KubernetesClient>): HasMetadata {
        return try {
            val resource = createResource<HasMetadataResource>(jsonYaml)
            val definitions = CustomResourceDefinitionMapping.getDefinitions(clients.get())
            if (CustomResourceDefinitionMapping.isCustomResource(resource, definitions)) {
                createResource<GenericCustomResource>(jsonYaml)
            } else {
                createResource(jsonYaml)
            }
        } catch (e: Exception) {
            throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
        }
    }

    fun closeClusterResource() {
        clusterResource?.close()
    }

    /**
     * Enables editing of non project files for the given file. This prevents the IDE from presenting the
     * "edit non-project" files dialog.
     *
     * @param file to enable the (non-project file) editing for
     */
    fun enableNonProjectFileEditing(file: VirtualFile? = editor.file) {
        if (file == null) {
            return
        }
        ResourceFile.create(file)?.enableNonProjectFileEditing()
    }
}

