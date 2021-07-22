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

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AppUIUtil
import com.intellij.util.ui.JBEmptyBorder
import com.redhat.devtools.intellij.common.utils.UIHelper
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.createClients
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An adapter for [FileEditor] instances that allows to push or load the editor content to/from a remote cluster.
 */
open class ResourceEditor protected constructor(
    private var localCopy: HasMetadata?,
    private val editor: FileEditor,
    private val project: Project,
    private val clients: Clients<out KubernetesClient>,
    // for mocking purposes
    private val createResource: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? =
        EditorResourceFactory::create,
    // for mocking purposes
    private val createClusterResource: (resource: HasMetadata, clients: Clients<out KubernetesClient>) -> ClusterResource =
        { resource, clients -> ClusterResource(resource, clients) },
    // for mocking purposes
    private val createFileForVirtual: (file: VirtualFile?) -> ResourceFile? =
        ResourceFile.Factory::create,
    // for mocking purposes
    private val createFileForResource: (resource: HasMetadata) -> ResourceFile? =
        ResourceFile.Factory::create,
    // for mocking purposes
    private val pushNotification: PushNotification = PushNotification(editor, project),
    // for mocking purposes
    private val pullNotification: PullNotification = PullNotification(editor, project),
    // for mocking purposes
    private val pulledNotification: PulledNotification = PulledNotification(editor, project),
    // for mocking purposes
    private val deletedNotification: DeletedNotification = DeletedNotification(editor, project),
    // for mocking purposes
    private val errorNotification: ErrorNotification = ErrorNotification(editor, project),
    // for mocking purposes
    private val documentProvider: (FileEditor) -> Document? = ::getDocument,
    // for mocking purposes
    private val psiDocumentManagerProvider: (Project) -> PsiDocumentManager = { PsiDocumentManager.getInstance(project) },
    // for mocking purposes
    private val ideNotification: Notification = Notification()
) {
    companion object {
        private val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)
        private val KEY_TOOLBAR = Key<ActionToolbar>(ActionToolbar::class.java.name)

        /**
         * Opens a new editor for the given [HasMetadata] and [Project].
         *
         * @param resource to edit
         * @param project that this editor belongs to
         * @return the new [ResourceEditor] that was opened
         */
        fun open(resource: HasMetadata, project: Project): ResourceEditor? {
            val file = ResourceFile.create(resource)?.write(resource) ?: return null
            val editor = FileEditorManager.getInstance(project)
                .openFile(file, true, true)
                .getOrNull(0)
                ?: return null
            val clients = createClients(ClientConfig {}) ?: return null
            return create(resource, editor, project, clients)
        }

        /**
         * Returns the existing or creates a new [ResourceEditor] for the given [FileEditor] and [Project].
         * Returns `null` if the given [FileEditor] is `null`, not a [ResourceEditor] or the given [Project] is `null`.
         *
         * @return the existing or a new [ResourceEditor].
         */
        fun get(editor: FileEditor?, project: Project?): ResourceEditor? {
            if (editor == null
                || !isResourceEditor(editor)
                || project == null) {
                return null
            }
            return editor.getUserData(KEY_RESOURCE_EDITOR)
                ?: create(editor, project)
        }

        private fun create(editor: FileEditor, project: Project): ResourceEditor? {
            try {
                val clients = createClients(ClientConfig {}) ?: return null
                val resource = EditorResourceFactory.create(editor, clients) ?: return null
                return create(resource, editor, project, clients)
            } catch(e: ResourceException) {
                ErrorNotification(editor, project).show(e.message ?: "", e.cause?.message)
                return null
            }
        }

        private fun create(
            resource: HasMetadata,
            editor: FileEditor,
            project: Project,
            clients: Clients<out KubernetesClient>
        ): ResourceEditor {
            val resourceEditor = ResourceEditor(resource, editor, project, clients)
            editor.putUserData(KEY_RESOURCE_EDITOR, resourceEditor)
            createToolbar(editor, project)
            return resourceEditor
        }

        private fun createToolbar(editor: FileEditor, project: Project) {
            var editorToolbar: ActionToolbar? = editor.getUserData(KEY_TOOLBAR)
            if (editorToolbar != null) {
                return
            }
            val actionManager = ActionManager.getInstance()
            val group = actionManager.getAction("Kubernetes.Editor.Toolbar") as ActionGroup
            editorToolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true) as ActionToolbarImpl
            editorToolbar.isOpaque = false
            editorToolbar.border = JBEmptyBorder(0, 2, 0, 2)
            editor.putUserData(KEY_TOOLBAR, editorToolbar)
            AppUIUtil.invokeOnEdt {
                FileEditorManager.getInstance(project).addTopComponent(editor, editorToolbar)
            }
        }

        private fun isResourceEditor(editor: FileEditor?): Boolean {
            return ResourceFile.isResourceFile(editor?.file)
        }
    }

    protected open var editorResource: HasMetadata? = localCopy
    /** mutex to exclude concurrent execution of push & watch notification **/
    private val resourceChangeMutex = ReentrantLock()
    private var oldClusterResource: ClusterResource? = null
    private var _clusterResource: ClusterResource? = null
    private val clusterResource: ClusterResource?
        get() {
            return resourceChangeMutex.withLock {
                if (_clusterResource == null
                    // create new cluster resource if editor has different resource (name, kind, etc. changed)
                    || false == _clusterResource?.isSameResource(editorResource)
                ) {
                    oldClusterResource = _clusterResource
                    oldClusterResource?.close()
                    _clusterResource = createClusterResource(editorResource, clients)
                }
                _clusterResource
            }
        }

    /**
     * Updates this editors notifications and title.
     *
     * @see [FileEditor.isValid]
     */
    fun update() {
        try {
            val resource = createResource.invoke(editor, clients) ?: return
            resourceChangeMutex.withLock {
                this.editorResource = resource
            }
            update(resource, clusterResource, oldClusterResource)
        } catch (e: ResourceException) {
            showErrorNotification(e)
        }
    }

    private fun update(resource: HasMetadata?, clusterResource: ClusterResource?, oldClusterResource: ClusterResource?) {
        if (resource == null
            || clusterResource == null) {
            return
        }
        if (clusterResource != oldClusterResource) {
            // new cluster resource was created, resource has thus changed
            renameEditor(resource, editor)
        }
        showNotifications(resource, clusterResource)
    }

    private fun renameEditor(resource: HasMetadata, editor: FileEditor) {
        val file = createFileForVirtual.invoke(editor.file) ?: return
        val newFile = createFileForResource.invoke(resource)
            if (!file.hasEqualBasePath(newFile)) {
            file.rename(resource)
        }
    }

    private fun showNotifications(
        resource: HasMetadata,
        clusterResource: ClusterResource
    ) {
        when {
            clusterResource.isDeleted()
                    && !clusterResource.isModified(resource) -> {
                hideNotifications()
                deletedNotification.show(resource)
            }
            clusterResource.isOutdated(resource) -> {
                hideNotifications()
                showPulledOrPullNotification(resource)
            }
            clusterResource.canPush(resource) -> {
                hideNotifications()
                pushNotification.show()
            }
        }
    }

    private fun hideNotifications() {
        errorNotification.hide()
        pullNotification.hide()
        deletedNotification.hide()
        pushNotification.hide()
        pulledNotification.hide()
    }

    private fun showPulledOrPullNotification(resource: HasMetadata) {
        val resourceOnCluster = clusterResource?.get(false)
        if (resourceOnCluster != null) {
            if (!hasLocalChanges(resource)) {
                replaceDocument(resourceOnCluster)
                pulledNotification.show(resourceOnCluster)
            } else {
                pullNotification.show(resourceOnCluster)
            }
        }
    }

    /**
     * Returns `true` if the given resource is dirty aka has modifications that were not pushed.
     *
     * @param resource to be checked for modification
     * @return true if the resource is dirty
     */
    private fun hasLocalChanges(resource: HasMetadata): Boolean {
        return resourceChangeMutex.withLock {
            resource != this.localCopy
        }
    }

    private fun showErrorNotification(e: Throwable) {
        hideNotifications()
        errorNotification.show(e.message ?: "", e.cause?.message)
    }

    /**
     * Pulls the resource from the cluster and replaces the content of this editor.
     * Does nothing if it doesn't exist.
     */
    fun pull() {
        hideNotifications()
        val pulledResource = resourceChangeMutex.withLock {
            val pulled = clusterResource?.get() ?: return
            /**
             * set editor resource now,
             * watch change modification notification can get in before document was replaced
             */
            this.editorResource = pulled
            this.localCopy = pulled
            pulled
        }
        replaceDocument(pulledResource)
    }

    private fun replaceDocument(resource: HasMetadata) {
        resourceChangeMutex.withLock {
            this.editorResource = resource
            this.localCopy = resource
        }
        val document = documentProvider.invoke(editor)
        if (document != null) {
            executeWriteAction {
                document.setText(Serialization.asYaml(resource))
                psiDocumentManagerProvider.invoke(project).commitDocument(document)
            }
        }
    }

    /**
     * Returns `true` if the resource in this editor exists on the cluster. Returns `false` otherwise.
     * @return true if the resource in this editor exists on the cluster
     */
    fun existsOnCluster(): Boolean {
        return clusterResource?.exists() ?: false
    }

    fun isOutdated(): Boolean {
        val resource = createResource.invoke(editor, clients) ?: return false
        resourceChangeMutex.withLock {
            this.editorResource = resource
        }
        return clusterResource?.isOutdated(resource) ?: false
    }

    /**
     * Pushes the editor content to the cluster.
     */
    fun push() {
        try {
            val resource = createResource.invoke(editor, clients) ?: return
            resourceChangeMutex.withLock {
                this.editorResource = resource
            }
            push(resource, clusterResource)
        } catch (e: Exception) {
            showErrorNotification(e)
        }
    }

    private fun push(resource: HasMetadata?, clusterResource: ClusterResource?) {
        try {
            if (resource == null
                || clusterResource == null
            ) {
                return
            }
            hideNotifications()
            val updatedResource = resourceChangeMutex.withLock {
                val updated = clusterResource.push(resource)
                /**
                 * set editor resource now,
                 * watch change modification notification can get in before document was replaced
                 */
                this.editorResource = updated
                this.localCopy = updated
                updated
            }
            if (updatedResource != null) {
                replaceDocument(updatedResource)
            }
        } catch (e: ResourceException) {
            logger<ResourceEditor>().warn(e)
            ideNotification.error(
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

    fun startWatch(): ResourceEditor {
        clusterResource?.watch()
        return this
    }

    fun stopWatch() {
        clusterResource?.stopWatch()
    }

    private fun createClusterResource(resource: HasMetadata?, clients: Clients<out KubernetesClient>): ClusterResource? {
        if (resource == null) {
            return null
        }
        val clusterResource = createClusterResource.invoke(resource, clients)
        clusterResource.addListener(onResourceChanged())
        clusterResource.watch()
        return clusterResource
    }

    private fun onResourceChanged(): ModelChangeObservable.IResourceChangeListener {
        return object : ModelChangeObservable.IResourceChangeListener {
            override fun added(added: Any) {
            }

            override fun removed(removed: Any) {
                showNotifications()
            }

            override fun modified(modified: Any) {
                showNotifications()
            }

            private fun showNotifications() {
                val pair = resourceChangeMutex.withLock {
                    val sameResource = editorResource?.isSameResource(localCopy) ?: false
                    Pair(sameResource, editorResource)
                }
                if (clusterResource != null
                    && pair.first
                    && pair.second != null
                ) {
                    showNotifications(pair.second!!, clusterResource!!)
                }
            }
        }
    }

    fun close() {
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
        createFileForVirtual(file)?.enableNonProjectFileEditing()
    }

    protected open fun executeWriteAction(runnable: () -> Unit) {
        UIHelper.executeInUI {
            WriteAction.compute<Unit, Exception>(runnable)
        }
    }
}

