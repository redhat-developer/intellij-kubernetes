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
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.redhat.devtools.intellij.common.utils.MetadataClutter
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.util.ResettableLazyProperty
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.toTitle
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
    private val resourceModel: IResourceModel,
    private val project: Project,
    // for mocking purposes
    private val createResource: (editor: FileEditor) -> HasMetadata? =
        EditorResourceFactory::create,
    // for mocking purposes
    private val clusterResourceFactory: (resource: HasMetadata?, context: IActiveContext<out HasMetadata, out KubernetesClient>?) -> ClusterResource? =
        ClusterResource.Factory::create,
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
    @Suppress("NAME_SHADOWING")
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo? = {
            file, project -> com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo(file,project)
    },
    // for mocking purposes
    private val documentChanged: AtomicBoolean = AtomicBoolean(false),
    // for mocking purposes
    private val resourceVersion: PersistentEditorValue = PersistentEditorValue(editor),
    // for mocking purposes
    private val diff: ResourceDiff = ResourceDiff(project)
): IResourceModelListener, Disposable {

    init {
        Disposer.register(editor, this)
        resourceModel.addListener(this)
    }

    companion object {
        val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)
        val KEY_TOOLBAR = Key<ActionToolbar>(ActionToolbar::class.java.name)
        const val ID_TOOLBAR = "Kubernetes.Editor.Toolbar"
    }

    /** mutex to exclude concurrent execution of push & watch notification **/
    private val resourceChangeMutex = ReentrantLock()
    private var oldClusterResource: ClusterResource? = null
    private var _clusterResource: ClusterResource? = null
    protected open val clusterResource: ClusterResource?
        get() {
            return resourceChangeMutex.withLock {
                if (_clusterResource == null
                    || true == _clusterResource?.isClosed()
                    // create new cluster resource if editor has different resource (name, kind, etc. changed)
                    || false == _clusterResource?.isSameResource(editorResource.get())
                ) {
                    oldClusterResource = _clusterResource
                    oldClusterResource?.close()
                    _clusterResource = createClusterResource(editorResource.get(), resourceModel.getCurrentContext())
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
     * @param deletedOnCluster `true` if resource was deleted on cluster, `false` otherwise
     *
     * @see [replaceDocument]
     */
    fun update(deletedOnCluster: Boolean = false) {
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
                val cluster = clusterResource
                showNotifications(deletedOnCluster, resource, cluster)
            } catch (e: Exception) {
                runInUI {
                    hideNotifications()
                    errorNotification.show(
                        toTitle(e),
                        toMessage(e.cause)
                    )
                }
            }
        }
    }

    private fun showNotifications(deleted: Boolean, resource: HasMetadata, clusterResource: ClusterResource?) {
        when {
            clusterResource == null ->
                showClusterErrorNotification()
            deleted ->
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

    private fun showClusterErrorNotification() {
        runInUI {
            hideNotifications()
            errorNotification.show(
                "Error contacting cluster. Make sure it's reachable, current context set, etc.",
                null
            )
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
            val editorResource: HasMetadata? = this.editorResource.get()
            val lastPushedPulled: HasMetadata? = this.lastPushedPulled.get()
            // dont compare resource version, uid
            runWithoutServerSetProperties(editorResource, lastPushedPulled) { editorResource != lastPushedPulled }
        }
    }

    /**
     * Pulls the resource from the cluster and replaces the content of this editor.
     * Does nothing if it doesn't exist.
     */
    fun pull() {
        val cluster = clusterResource ?: return
        runAsync {
            try {
                val pulled = pull(cluster) ?: return@runAsync
                saveResourceVersion(pulled)
                runInUI {
                    replaceDocument(pulled)
                    hideNotifications()
                    pulledNotification.show(pulled)
                }
            } catch (e: Exception) {
                logger<ResourceEditor>().warn(e)
                runInUI {
                    hideNotifications()
                    errorNotification.show(
                        "Could not pull ${editorResource.get()?.kind} ${editorResource.get()?.metadata?.name ?: ""}",
                        toMessage(e.cause)
                    )
                }
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
        val jsonYaml = serialize(resource, getFileType(document, manager)) ?: return
        if (document.text.trim() != jsonYaml) {
            runWriteCommand {
                document.replaceString(0, document.textLength, jsonYaml)
                documentChanged.set(true)
                manager.commitDocument(document)
            }
        }
    }

    private fun getFileType(document: Document?, manager: PsiDocumentManager): FileType? {
        if (document == null) {
            return null
        }
        return runReadCommand {
            val file = manager.getPsiFile(document)
            file?.fileType
        }
    }

    private fun serialize(resource: HasMetadata, fileType: FileType?): String? {
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
            } catch (e: Exception) {
                logger<ResourceEditor>().warn(e)
                runInUI {
                    hideNotifications()
                    errorNotification.show(
                        "Could not push ${editorResource.get()?.kind} ${editorResource.get()?.metadata?.name ?: ""}",
                        toMessage(e.cause)
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
             * save pushed [resource] so that resource is not modified in later [isModified]
             */
            lastPushedPulled.set(resource)
        }
    }

    fun diff() {
        val clusterResource = clusterResource?.pull() ?: return
        val manager = getPsiDocumentManager.invoke(project)
        val file = editor.file ?: return
        runInUI {
            val document = getDocument.invoke(editor) ?: return@runInUI
            val serialized = serialize(clusterResource, getFileType(document, manager)) ?: return@runInUI
            val documentBeforeDiff = document.text
            diff.open(file, serialized) { onDiffClosed(clusterResource, documentBeforeDiff) }
        }
    }

    /*
    * Protected visibility for testing purposes:
    * Tried capturing callback parameter in #diff and running it, but it didn't work.
    * Document mock didn't return multiple return values (1.doc before diff, 2. doc after diff).
    * Made callback protected to be able to override it with public visibility in TestableResourceEditor instead
    * so that tests can call it directly.
    */
    protected open fun onDiffClosed(resource: HasMetadata, documentBeforeDiff: String?) {
        val afterDiff = getDocument.invoke(editor)?.text
        val modified = (documentBeforeDiff != afterDiff)
        if (modified) {
            saveResourceVersion(resource)
            update()
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

    fun removeClutter() {
        val resource = editorResource.get() ?: return
        MetadataClutter.remove(resource.metadata)
        replaceDocument(resource)
        runInUI {
            hideNotifications()
        }
    }

    private fun createClusterResource(resource: HasMetadata?, context: IActiveContext<out HasMetadata, out KubernetesClient>?): ClusterResource? {
        val clusterResource = clusterResourceFactory.invoke(resource, context) ?: return null
        clusterResource.addListener(onResourceChanged())
        clusterResource.watch()
        return clusterResource
    }

    private fun onResourceChanged(): IResourceModelListener {
        return object : IResourceModelListener {
            override fun added(added: Any) {
                // ignore
            }

            override fun removed(removed: Any) {
                update(true)
            }

            override fun modified(modified: Any) {
                update()
            }
        }
    }

    /**
     * Closes this instance and cleans up references to it.
     * - Removes the resource model listener,
     * - closes the [clusterResource],
     * - removes the references in editor- and editor file-userdata
     * - saves the resource version
     */
    fun close() {
        resourceModel.removeListener(this)
        // use backing variable to prevent accidental creation
        _clusterResource?.close()
        editor.putUserData(KEY_RESOURCE_EDITOR, null)
        editor.file?.putUserData(KEY_RESOURCE_EDITOR, null)
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

    override fun modified(modified: Any) {
        if (modified is IResourceModel) {
            // active context changed, recreate cluster resource
            recreateClusterResource()
            resourceVersion.set(null)
            update()
        }
    }

    override fun currentNamespaceChanged(new: IActiveContext<*,*>?, old: IActiveContext<*,*>?) {
        // current namespace in same context has changed, recreate cluster resource
        recreateClusterResource()
        resourceVersion.set(null)
        update()
    }

    private fun saveResourceVersion(resource: HasMetadata?) {
        val version = resource?.metadata?.resourceVersion ?: return
        resourceVersion.set(version)
    }

    /**
     * Closes the current [clusterResource] so that a new one is created when it is accessed (ex. in [update]).
     *
     * @see clusterResource
     */
    private fun recreateClusterResource() {
        resourceChangeMutex.withLock {
            clusterResource?.close()
            clusterResource// accessing causes re-creation
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
    protected open fun <R: Any> runReadCommand(runnable: () -> R?): R? {
        return ReadAction.compute<R, Exception>(runnable)
    }

    /** for testing purposes */
    protected open fun runInUI(runnable: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runnable.invoke()
        } else {
            ApplicationManager.getApplication().invokeLater(runnable)
        }
    }

    override fun dispose() {
        resourceModel.removeListener(this)
    }
}
