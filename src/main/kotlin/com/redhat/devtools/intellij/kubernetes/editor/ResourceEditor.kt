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
import com.intellij.openapi.command.WriteCommandAction
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
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.editor.util.getFile
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.createClients
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.NAME_PREFIX_EDITOR
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.reportResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An adapter for [FileEditor] instances that allows to push or load the editor content to/from a remote cluster.
 */
open class ResourceEditor protected constructor(
    var localCopy: HasMetadata?,
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
    private val createResourceFileForVirtual: (file: VirtualFile?) -> ResourceFile? =
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
    private val ideNotification: Notification = Notification(),
    private val documentReplaced: AtomicBoolean = AtomicBoolean(false)

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
            return get(editor, project)
        }

        /**
         * Returns the existing or creates a new [ResourceEditor] for the given [FileEditor] and [Project].
         * Returns `null` if the given [FileEditor] is `null`, not a [ResourceEditor] or the given [Project] is `null`.
         *
         * @return the existing or a new [ResourceEditor].
         */
        fun get(editor: FileEditor?, project: Project?): ResourceEditor? {
            if (editor == null
                || project == null) {
                return null
            }

            val resourceEditor = editor.getUserData(KEY_RESOURCE_EDITOR)
            if (resourceEditor != null) {
                return resourceEditor
            }
            val document = getDocument(editor) ?: return null
            if (!ResourceFile.isResourceFile(getFile(document))) {
                return null
            }
            return create(editor, project)
        }

        fun get(file: VirtualFile?): ResourceEditor? {
            return file?.getUserData(KEY_RESOURCE_EDITOR)
        }

        private fun create(editor: FileEditor, project: Project): ResourceEditor? {
            val telemetry = TelemetryService.instance.action(NAME_PREFIX_EDITOR + "open")
            try {
                val clients = createClients(ClientConfig {}) ?: return null
                val resource = EditorResourceFactory.create(editor, clients) ?: return null
                reportResource(resource, telemetry)
                return create(resource, editor, project, clients)
            } catch(e: ResourceException) {
                ErrorNotification(editor, project).show(e.message ?: "", e.cause?.message)
                telemetry.error(e).send()
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
            editor.file?.putUserData(KEY_RESOURCE_EDITOR, resourceEditor)
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
    }

    open var editorResource: HasMetadata? = localCopy
        get() {
            resourceChangeMutex.withLock {
                return field
            }
        }
        set(resource) {
            resourceChangeMutex.withLock {
                field = resource
            }
        }
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
     * Updates this editor notifications and title. Does nothing if is called right after [replaceDocument].
     *
     * @see [replaceDocument]
     */
    fun update() {
        try {
            if (documentReplaced.compareAndSet(true, false)) {
                /**
                 * update triggered by [replaceDocument]
                 **/
                return
            }
            val resource = createResource.invoke(editor, clients) ?: return
            this.editorResource = resource
            update(resource, clusterResource)
        } catch (e: ResourceException) {
            showErrorNotification(e)
        }
    }

    private fun update(resource: HasMetadata?, clusterResource: ClusterResource?) {
        if (resource == null
            || clusterResource == null) {
            return
        }
        showNotifications(resource, clusterResource)
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
            else -> hideNotifications()
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
                resourceChangeMutex.withLock {
                    this.editorResource = resource
                    this.localCopy = resource
                }
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

    private fun replaceDocument(resource: HasMetadata?) {
        if (resource == null) {
            return
        }
        val document = documentProvider.invoke(editor) ?: return
        val jsonYaml = Serialization.asYaml(resource).trim()
        if (document.text.trim() != jsonYaml) {
            executeWriteAction {
                document.replaceString(0, document.textLength - 1, jsonYaml)
                documentReplaced.set(true)
                val psiDocumentManager = psiDocumentManagerProvider.invoke(project)
                psiDocumentManager.commitDocument(document)
                pulledNotification.show(resource)
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
        this.editorResource = resource
        return clusterResource?.isOutdated(resource) ?: false
    }

    /**
     * Pushes the editor content to the cluster.
     */
    fun push() {
        try {
            val resource = createResource.invoke(editor, clients) ?: return
            this.editorResource = resource
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
                 * set editor resource now using lock,
                 * resource watch change modification notification can get in before document was replaced
                 */
                this.editorResource = updated
                this.localCopy = updated
                updated
            }
            replaceDocument(updatedResource)
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
                // ignore
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
        deleteTemporaryFile()
    }

    private fun deleteTemporaryFile() {
        val file = createResourceFileForVirtual(editor.file)
        if (true == file?.isTemporaryFile()) {
            file.delete()
        }
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
        createResourceFileForVirtual(file)?.enableNonProjectFileEditing()
    }

    fun getTitle(): String? {
        val file = editor.file ?: return null
        return if (true == createResourceFileForVirtual(file)?.isTemporaryFile()) {
            val resource = editorResource ?: return ""
            getTitleFor(resource)
        } else {
            getTitleFor(file)
        }
    }

    private fun getTitleFor(file: VirtualFile): String? {
        return file.name
    }

    private fun getTitleFor(resource: HasMetadata): String {
        return when (resource) {
            is Namespace,
            is io.fabric8.openshift.api.model.Project -> resource.metadata.name
            else -> {
                if (resource.metadata.namespace != null) {
                    "${resource.metadata.name}@${resource.metadata.namespace}"
                } else {
                    resource.metadata.name
                }
            }
        }
    }

    protected open fun executeWriteAction(runnable: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project, runnable)
    }
}

