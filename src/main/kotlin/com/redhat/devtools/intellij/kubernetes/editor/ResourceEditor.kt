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
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.createClients
import com.redhat.devtools.intellij.kubernetes.model.util.ResettableLazyProperty
import com.redhat.devtools.intellij.kubernetes.model.util.causeOrExceptionMessage
import com.redhat.devtools.intellij.kubernetes.model.util.isGreaterIntThan
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import org.jetbrains.yaml.YAMLFileType
import kotlin.concurrent.withLock

/**
 * A Decorator for [FileEditor] instances that allows to push or pull the editor content to/from a remote cluster.
 */
open class ResourceEditor(
    val editor: FileEditor,
    private val project: Project,
    /* for mocking purposes */
    private val clients: Clients<out KubernetesClient> = createClients(ClientConfig {}),
    // for mocking purposes
    private val createResource: (editor: FileEditor) -> HasMetadata? =
        EditorResourceFactory::create,
    // for mocking purposes
    private val createClusterResource: (resource: HasMetadata, clients: Clients<out KubernetesClient>) -> ClusterResource? =
        { resource, clients ->
                ClusterResource(resource, clients)
        },
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
    private val getDocument: (FileEditor) -> Document? = ::getDocument,
    // for mocking purposes
    private val getPsiDocumentManager: (Project) -> PsiDocumentManager = { PsiDocumentManager.getInstance(project) },
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo? = {
            file, project -> com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo(file,project)
    },
    // for mocking purposes
    private val documentChanged: AtomicBoolean = AtomicBoolean(false),
    // for mocking purposes
    private val resourceVersion: PersistentEditorValue = PersistentEditorValue(editor)
) {

    companion object {
        val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)
        val KEY_TOOLBAR = Key<ActionToolbar>(ActionToolbar::class.java.name)
        const val ID_TOOLBAR = "Kubernetes.Editor.Toolbar"
        const val TITLE_UNKNOWN_CLUSTERRESOURCE = "Unknown Cluster Resource"
        const val TITLE_UNKNOWN_NAME = "unknown name"
    }

    /** mutex to exclude concurrent execution of push & watch notification **/
    private val resourceChangeMutex = ReentrantLock()
    private var oldClusterResource: ClusterResource? = null
    private var _clusterResource: ClusterResource? = null
    protected open val clusterResource: ClusterResource?
        get() {
            return resourceChangeMutex.withLock {
                if (_clusterResource == null
                    // create new cluster resource if editor has different resource (name, kind, etc. changed)
                    || false == _clusterResource?.isSameResource(editorResource.get())
                ) {
                    oldClusterResource = _clusterResource
                    oldClusterResource?.close()
                    _clusterResource = createClusterResource(editorResource.get(), clients)
                    lastPushedPulled.reset()
                }
                _clusterResource
            }
        }

    open var editorResource = ResettableLazyProperty<HasMetadata?> {
        createResource.invoke(editor)
    }

    protected open var lastPushedPulled = ResettableLazyProperty<HasMetadata?> {
        if (true == clusterResource?.exists()) {
            resourceChangeMutex.withLock { editorResource.get() }
        } else {
            null
        }
    }

    /**
     * Updates this editor notifications and title. Does nothing if is called right after [replaceDocument].
     *
     * @see [replaceDocument]
     */
    fun update() {
        if (documentChanged.compareAndSet(true, false)) {
            /** update triggered by change in document [replaceDocument] */
            return
        }
        runAsync {
            try {
                val resource = createResource.invoke(editor) ?: return@runAsync
                resourceChangeMutex.withLock {
                    this.editorResource.set(resource)
                }
                saveResourceVersion(resource)
                val cluster = clusterResource ?: return@runAsync
                showNotifications(resource, cluster)
            } catch (e: ResourceException) {
                runInUI {
                    hideNotifications()
                    errorNotification.show(
                        e.message ?: "Invalid kubernetes yaml/json",
                        trimWithEllipsis(causeOrExceptionMessage(e), 300) ?: ""
                    )
                }
            }
        }
    }

    private fun saveResourceVersion(resource: HasMetadata?) {
        val version = resource?.metadata?.resourceVersion ?: return
        val storedVersion = resourceVersion.get()
        if (!version.isGreaterIntThan(storedVersion)) {
            // don't get back in version
            return
        }
        resourceVersion.set(version)
    }

    private fun showNotifications(resource: HasMetadata, clusterResource: ClusterResource) {
        when {
            clusterResource.isDeleted() ->
                showDeletedNotification(resource)
            isModified() ->
                showPushNotification(resourceVersion.get())
            clusterResource.isOutdated(resourceVersion.get()) ->
                showPullNotification(resource)
            else ->
                runInUI {
                    hideNotifications()
                }
        }
    }

    private fun showPushNotification(version: String?) {
        val existsOnCluster = (true == clusterResource?.exists())
        val isOutdated = (true == clusterResource?.isOutdated(version))
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideNotifications()
            pushNotification.show(existsOnCluster, isOutdated)
        }
    }

    private fun showDeletedNotification(resource: HasMetadata) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideNotifications()
            deletedNotification.show(resource)
        }
    }

    private fun hideNotifications() {
        errorNotification.hide()
        pullNotification.hide()
        deletedNotification.hide()
        pushNotification.hide()
        pulledNotification.hide()
    }

    private fun showPullNotification(resourceInEditor: HasMetadata) {
        val clusterResource = this.clusterResource ?: return
        val resourceOnCluster = clusterResource.pull() ?: return
        val canPush = clusterResource.canPush(resourceInEditor)
        runInUI {
            hideNotifications()
            pullNotification.show(resourceOnCluster, canPush)
        }
    }

    /**
     * Returns `true` if the resource in the editor has changes that were not pushed (yet).
     *
     * @return true if the resource is dirty
     */
    protected open fun isModified(): Boolean {
        return resourceChangeMutex.withLock {
            val thisResource: HasMetadata? = editorResource.get()
            val thatResource: HasMetadata? = lastPushedPulled.get()
            // dont compare resource version, uid
            runWithoutServerSetProperties(thisResource, thatResource) { thisResource != thatResource }
        }
    }

    /**
     * Pulls the resource from the cluster and replaces the content of this editor.
     * Does nothing if it doesn't exist.
     */
    fun pull() {
        try {
            val cluster = clusterResource ?: return
            runAsync {
                val pulled = pull(cluster) ?: return@runAsync
                resourceVersion.set(pulled.metadata.resourceVersion)
                runInUI {
                    replaceDocument(pulled)
                    hideNotifications()
                    pulledNotification.show(pulled)
                }
            }
        } catch (e: ResourceException) {
            logger<ResourceEditor>().warn(e)
            runInUI {
                hideNotifications()
                errorNotification.show(
                    "Could not pull ${editorResource.get()?.kind} ${editorResource.get()?.metadata?.name ?: ""}",
                    trimWithEllipsis(causeOrExceptionMessage(e), 300)
                )
            }
        }
    }

    private fun pull(cluster: ClusterResource): HasMetadata? {
        return resourceChangeMutex.withLock {
            val pulled = cluster.pull()
            /**
             * set editor resource now,
             * watch change modification notification can get in before document was replaced
             */
            this.editorResource.set(pulled)
            this.lastPushedPulled.set(pulled)
            pulled
        }
    }

    private fun replaceDocument(resource: HasMetadata?) {
        if (resource == null) {
            return
        }
        val manager = getPsiDocumentManager.invoke(project)
        val document = getDocument.invoke(editor) ?: return
        val file = manager.getPsiFile(document) ?: return
        val jsonYaml = serialize(resource, file.fileType) ?: return
        if (document.text.trim() != jsonYaml) {
            runWriteCommand {
                document.replaceString(0, document.textLength, jsonYaml)
                documentChanged.set(true)
                manager.commitDocument(document)
            }
        }
    }

    private fun serialize(resource: HasMetadata, fileType: FileType?): CharSequence? {
        val serializer = when(fileType) {
            YAMLFileType.YML ->
                Serialization.yamlMapper().writerWithDefaultPrettyPrinter()
            JsonFileType.INSTANCE ->
                Serialization.jsonMapper().writerWithDefaultPrettyPrinter()
            else -> null
        }
        return serializer?.writeValueAsString(resource)?.trim()
    }

    /**
     * Pushes the editor content to the cluster.
     */
    fun push() {
        runInUI {
            // hide before running push. Push may take quite some time on remote cluster
            hideNotifications()
        }
        runAsync {
            try {
                resourceChangeMutex.withLock {
                    val cluster = clusterResource ?: return@runAsync
                    val resource = editorResource.get() ?: return@runAsync
                    push(resource, cluster)
                }
            } catch (e: ResourceException) {
                logger<ResourceEditor>().warn(e)
                runInUI {
                    hideNotifications()
                    errorNotification.show(
                        "Could not push ${editorResource.get()?.kind} ${editorResource.get()?.metadata?.name ?: ""}",
                        trimWithEllipsis(causeOrExceptionMessage(e), 300)
                    )
                }
            }
        }
    }

    private fun push(resource: HasMetadata, clusterResource: ClusterResource) {
        val updated = clusterResource.push(resource)
        /**
         * set editor resource now,
         * resource change notification can get in before document was replaced
         */
        saveResourceVersion(updated)
        resourceChangeMutex.withLock {
            /**
             * set [resource], not [updated] so that resource is not modified in later [isModified]
             */
            lastPushedPulled.set(resource)
        }
    }

    fun startWatch(): ResourceEditor {
        clusterResource?.watch()
        return this
    }

    fun stopWatch() {
        // use backing variable to prevent accidental creation
        _clusterResource?.stopWatch()
    }

    private fun createClusterResource(resource: HasMetadata?, clients: Clients<out KubernetesClient>): ClusterResource? {
        if (resource == null) {
            return null
        }
        val clusterResource = createClusterResource.invoke(resource, clients) ?: return null
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
                runAsync {
                    update()
                }
            }

            override fun modified(modified: Any) {
                runAsync {
                    update()
                }
            }
        }
    }

    /**
     * Closes this instance. Closes the resource watch and deletes the temporary file if one was created.
     */
    fun close() {
        // use backing variable to prevent accidental creation
        _clusterResource?.close()
        editor.putUserData(KEY_RESOURCE_EDITOR, null)
        editor.file?.putUserData(KEY_RESOURCE_EDITOR, null)
        createResourceFileForVirtual.invoke(editor.file)?.deleteTemporary()
        resourceVersion.save()
    }

    /**
     * Enables editing of non project files for the file in this editor. This prevents the IDE from presenting the
     * "edit non-project" files dialog.
     */
    fun enableNonProjectFileEditing() {
        if (editor.file == null
            || !isKubernetesResource(
                getKubernetesResourceInfo.invoke(editor.file, project))){
                return
        }
        createResourceFileForVirtual(editor.file)?.enableNonProjectFileEditing()
    }

    fun createToolbar() {
        var editorToolbar: ActionToolbar? = editor.getUserData(KEY_TOOLBAR)
        if (editorToolbar == null) {
            editorToolbar = EditorToolbarFactory.create(ID_TOOLBAR, editor, project)
            editor.putUserData(KEY_TOOLBAR, editorToolbar)
        }
    }

    /** for testing purposes */
    protected open fun runAsync(runnable: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable)
    }

    /** for testing purposes */
    protected open fun runWriteCommand(runnable: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project, runnable)
    }

    /** for testing purposes */
    protected open fun runInUI(runnable: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runnable.invoke()
        } else {
            ApplicationManager.getApplication().invokeLater(runnable)
        }
    }
}

