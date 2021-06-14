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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ReloadNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getProjectAndEditor
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import com.redhat.devtools.intellij.kubernetes.model.util.createClients
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import java.io.IOException

open class ResourceEditor protected constructor(
    private var localCopy: HasMetadata?,
    private val editor: FileEditor,
    private val project: Project,
    private val clients: Clients<out KubernetesClient>
) {
    companion object {
        private val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)

        fun isResourceEditor(editor: FileEditor?): Boolean {
            return ResourceFile.isResourceFile(editor?.file)
        }

        @Throws(IOException::class)
        fun open(resource: HasMetadata, project: Project): ResourceEditor? {
            val file = ResourceFile.create(resource)?.write(resource) ?: return null
            val editor = FileEditorManager.getInstance(project)
                .openFile(file, true, true)
                .getOrNull(0)
                ?: return null
            val clients = createClients(ClientConfig {  }) ?: return null
            return create(resource, editor, project, clients)
        }

        fun get(document: Document): ResourceEditor? {
            val file = getResourceFile(document) ?: return null
            val projectAndEditor = getProjectAndEditor(file) ?: return null
            return get(projectAndEditor.editor, projectAndEditor.project)
        }

        fun get(editor: FileEditor?, project: Project?): ResourceEditor? {
            if (editor == null
                || !editor.isValid
                || !isResourceEditor(editor)
                || project == null) {
                return null
            }
            return editor.getUserData(KEY_RESOURCE_EDITOR)
                ?: create(editor, project)
        }

        private fun create(editor: FileEditor, project: Project): ResourceEditor? {
            val clients = createClients(ClientConfig {}) ?: return null
            val resource = EditorResourceFactory.create(editor, clients) ?: return null
            return create(resource, editor, project, clients)
        }

        private fun create(
            resource: HasMetadata,
            editor: FileEditor,
            project: Project,
            clients: Clients<out KubernetesClient>
        ): ResourceEditor {
            val resourceEditor = ResourceEditor(resource, editor, project, clients)
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
    }

    private var oldClusterResource: ClusterResource? = null
    private var _clusterResource: ClusterResource? = null
    private val clusterResource: ClusterResource?
        get() {
            synchronized(this) {
                if (_clusterResource == null
                    || false == _clusterResource?.isSameResource(localCopy)) {
                    oldClusterResource = _clusterResource
                    oldClusterResource?.close()
                    _clusterResource = createClusterResource(localCopy, clients)
                }
                return _clusterResource
            }
        }

    fun updateEditor() {
        try {
            val resource = EditorResourceFactory.create(editor, clients) ?: return
            updateEditor(resource)
        } catch (e: ResourceException) {
            showErrorNotification(editor, project, e)
        }
    }

    private fun updateEditor(resource: HasMetadata) {
        updateEditor(resource, clusterResource, oldClusterResource)
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

    private fun renameEditor(resource: HasMetadata, editor: FileEditor) {
        val file = ResourceFile.create(editor.file) ?: return
        val newFile = ResourceFile.create(resource)
        if (!file.hasEqualBasePath(newFile)) {
            file.rename(resource)
        }
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
                showReloadNotification(editor, resource, project)
            clusterResource.canPush(resource) ->
                PushNotification.show(editor, project)
        }
    }

    private fun showReloadNotification(
        editor: FileEditor,
        resource: HasMetadata,
        project: Project
    ) {
        val resourceOnCluster = clusterResource?.get(false)
        if (!hasLocalChanges(resource)
            && resourceOnCluster != null) {
            reloadEditor(resourceOnCluster)
        } else {
            ReloadNotification.show(editor, resource, project)
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
                    this.localCopy = resource
                }
            }
        }
    }

    fun existsOnCluster(): Boolean {
        return clusterResource?.exists() ?: false
    }

    fun isOutdated(): Boolean {
        val resource = EditorResourceFactory.create(editor, clients)
        return clusterResource?.isOutdated(resource) ?: false
    }

    fun push() {
        try {
            this.localCopy = EditorResourceFactory.create(editor, clients) ?: return
            push(localCopy, clusterResource)
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

    /**
     * Returns `true` if the given resource is dirty aka has modifications that were not pushed.
     *
     * @param resource to be checked for modification
     * @return true if the resource is dirty
     */
    private fun hasLocalChanges(resource: HasMetadata): Boolean {
        return resource != this.localCopy
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

