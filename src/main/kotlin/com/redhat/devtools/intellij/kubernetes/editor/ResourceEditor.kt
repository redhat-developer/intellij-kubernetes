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
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedOnCluster
import com.redhat.devtools.intellij.kubernetes.editor.notification.Different
import com.redhat.devtools.intellij.kubernetes.editor.notification.Error
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.Identical
import com.redhat.devtools.intellij.kubernetes.editor.notification.Modified
import com.redhat.devtools.intellij.kubernetes.editor.notification.Outdated
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ResourceState
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.areEqual
import com.redhat.devtools.intellij.kubernetes.model.util.hasGenerateName
import com.redhat.devtools.intellij.kubernetes.model.util.hasName
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.toNames
import com.redhat.devtools.intellij.kubernetes.model.util.toTitle
import io.fabric8.kubernetes.api.model.HasMetadata
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A Decorator for [FileEditor] instances that allows to push or pull the editor content to/from a remote cluster.
 */
open class ResourceEditor(
    val editor: FileEditor,
    private val resourceModel: IResourceModel,
    private val project: Project,
    // for mocking purposes
    private val createResources: (string: String?, fileType: FileType?, currentNamespace: String?) -> List<HasMetadata> =
        EditorResourceSerialization::deserialize,
    private val serialize: (resources: Collection<HasMetadata>, fileType: FileType?) -> String? =
        EditorResourceSerialization::serialize,
    // for mocking purposes
    private val createResourceFileForVirtual: (file: VirtualFile?) -> ResourceFile? =
        ResourceFile.Factory::create,
    // for mocking purposes
    private val pushNotification: PushNotification = PushNotification(editor, project),
    // for mocking purposes
    private val pushedNotification: PushedNotification = PushedNotification(editor, project),
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
    // for mocking purposes
    @Suppress("NAME_SHADOWING")
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo? = {
            file, project -> com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo(file,project)
    },
    // for mocking purposes
    private val documentChanged: AtomicBoolean = AtomicBoolean(false),
    // for mocking purposes
    private val diff: ResourceDiff = ResourceDiff(project),
    // for mocking purposes
    protected val attributes: EditorResourceAttributes = EditorResourceAttributes(resourceModel)
): Disposable {

    init {
        Disposer.register(editor, this)
        attributes.resourceChangedListener = onResourceChanged()
        resourceModel.addListener(onNamespaceOrContextChanged())
    }

    companion object {
        val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)
        val KEY_TOOLBAR = Key<ActionToolbar>(ActionToolbar::class.java.name)
        const val ID_TOOLBAR = "Kubernetes.Editor.Toolbar"
    }

    /** mutex to exclude concurrent execution of push & watch notification **/
    private val resourceChangeMutex = ReentrantLock()
    private var onNamespaceContextChanged: IResourceModelListener = onNamespaceOrContextChanged()
    open val editorResources = mutableListOf<HasMetadata>()

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
                val states = resourceChangeMutex.withLock {
                    val resources = createEditorResources(getDocument(editor))
                    attributes.update(resources)
                    getResourceStates(resources)
                }
                if (states.size == 1) {
                    // show notification for 1 resource
                    showNotification(true, states.firstOrNull())
                } else {
                    // show notification for multiple resources
                    showNotification(states)
                }
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

    protected fun createEditorResources(document: Document?): List<HasMetadata> {
        val resources = createResources.invoke(document?.text, editor.file?.fileType, resourceModel.getCurrentNamespace())
        this.editorResources.clear()
        this.editorResources.addAll(resources)
        return resources
    }

    private fun getResourceStates(resources: Collection<HasMetadata>): List<ResourceState> {
        return resources
            .map { resource -> getResourceState(resource) }
    }

    private fun getResourceState(resource: HasMetadata): ResourceState {
        val clusterResource = attributes.getClusterResource(resource)
        return when {
            clusterResource == null ->
                Error(resource, "Error contacting cluster. Make sure it's reachable, current context set, etc.", null as String?)
            !hasName(resource)
                    && !hasGenerateName(resource) ->
                Error(resource, "Resource has neither name nor generateName.", null as String?)
            clusterResource.isDeleted() ->
                DeletedOnCluster(resource)
            !clusterResource.exists()
                    || isModified(resource) ->
                Modified(
                    resource,
                    clusterResource.exists(),
                    clusterResource.isOutdatedVersion(attributes.getResourceVersion(resource)))
            clusterResource.isOutdatedVersion(attributes.getResourceVersion(resource)) ->
                Outdated(resource)
            else ->
                Identical(resource)
        }
    }

    private fun showNotification(showPull: Boolean, state: ResourceState?) {
        if (state == null) {
            return
        }
        when (state) {
            is Error ->
                showErrorNotification(state.title, state.message)
            is DeletedOnCluster ->
                showDeletedNotification(state.resource)
            is Outdated ->
                showPullNotification(state.resource)
            is Modified ->
                showPushNotification(showPull, listOf(state))
            is Identical ->
                runInUI { hideNotifications() }
            else ->
                Unit
        }
    }

    private fun showNotification(states: Collection<ResourceState>) {
        @Suppress("UNCHECKED_CAST")
        val toPush = states.filter { state ->
            state is Different
                    && state.isPush()
        } as? Collection<Different> ?: return
        showPushNotification(false, toPush)
    }

    private fun showErrorNotification(title: String, message: String?) {
        runInUI {
            hideNotifications()
            errorNotification.show(title, message)
        }
    }

    private fun showPushNotification(showPull: Boolean, states: Collection<Different>) {
        if (states.isEmpty()) {
            return
        }
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideNotifications()
            pushNotification.show(showPull, states)
        }
    }

    private fun showPushedNotification(states: Collection<Different>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideNotifications()
            pushedNotification.show(states)
        }
    }

    private fun showPullNotification(resource: HasMetadata) {
        runInUI {
            hideNotifications()
            pullNotification.show(resource)
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
        pushedNotification.hide()
        pulledNotification.hide()
    }

    /**
     * Returns `true` if the resource in the editor has changes that don't exist in the resource
     * that was last pulled/pushed from/to the cluster.
     * The following properties are not taken into account:
     * * [io.fabric8.kubernetes.api.model.ObjectMeta.resourceVersion]
     * * [io.fabric8.kubernetes.api.model.ObjectMeta.uid]
     *
     * @return true if the resource is not equal to the same resource on the cluster
     */
    private fun isModified(resource: HasMetadata): Boolean {
        val pushedPulled = attributes.getLastPulledPushed(resource)
        return !areEqual(resource, pushedPulled)
    }

    /**
     * Pulls the resource from the cluster and replaces the content of this editor.
     * Does nothing if it doesn't exist.
     */
    fun pull() {
        val resource = resourceChangeMutex.withLock {
            // do not pull for multi-resource
            editorResources.firstOrNull() ?: return
        }

        val cluster = attributes.getAllClusterResources().firstOrNull() ?: return

        runAsync {
            try {
                val pulled = pullAndReplace(resource, cluster) ?: return@runAsync
                updateAttributes(pulled, pulled.metadata.resourceVersion)
                runInUI {
                    replaceDocument()
                    hideNotifications()
                    pulledNotification.show(pulled)
                }
            } catch (e: Exception) {
                logger<ResourceEditor>().warn(e)
                showErrorNotification(
                    "Could not pull ${resource.kind} ${resource.metadata?.name ?: ""}",
                    toMessage(e.cause))
            }
        }
    }

    private fun pullAndReplace(resource: HasMetadata, cluster: ClusterResource): HasMetadata? {
        return resourceChangeMutex.withLock {
            val pulled = cluster.pull() ?: return null
            val index = editorResources.indexOf(resource)
            if (index >= 0) {
                /**
                 * set editor resource now,
                 * watch change modification notification can get in before document was replaced
                 */
                editorResources[index] = pulled
                pulled
            } else {
                null
            }
        }
    }

    private fun replaceDocument() {
        val manager = getPsiDocumentManager.invoke(project)
        val document = getDocument.invoke(editor) ?: return
        val jsonYaml = resourceChangeMutex.withLock {
            serialize.invoke(editorResources, getFileType(document, manager)) ?: return
        }
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

    /**
     * Pushes the editor content to the cluster.
     */
    fun push() {
        runInUI {
            // hide before running push. Push may take quite some time on remote cluster
            hideNotifications()
        }
        runAsync {
            val states = getResourceStates(editorResources).filterIsInstance<Modified>()
            val toPush = getResourcesToPush(states)
            val exceptions = push(toPush)
            if (exceptions.isEmpty()) {
                showPushedNotification(states)
            } else {
                val e = createMultiException(exceptions)
                logger<ResourceEditor>().warn(e)
                showErrorNotification(
                    e.message ?: "Could not push resources to cluster.",
                    exceptions.joinToString(
                        "\n${EditorResourceSerialization.RESOURCE_SEPARATOR_YAML}\n") { e ->
                            toMessage(e.cause)
                    }
                )
            }
        }
    }

    private fun getResourcesToPush(states: List<Modified>): List<HasMetadata> {
        return states
            .filter { state -> state.isPush() }
            .map { state -> state.resource }
    }

    private fun push(editorResources: Collection<HasMetadata>): List<ResourceException> {
        return resourceChangeMutex.withLock {
            editorResources.mapNotNull { resource ->
                push(resource)
            }
        }
    }

    private fun push(resource: HasMetadata): ResourceException? {
        return try {
            val updated = attributes.getClusterResource(resource)?.push(resource)
            /**
             * set editor resource now,
             * resource change notification can get in before document was replaced
             */
            if (updated != null) {
                updateAttributes(resource, updated.metadata.resourceVersion)
            }
            null
        } catch (e: Exception) {
            if (e is ResourceException) {
                e
            } else {
                ResourceException(e.message, e)
            }
        }
    }

    private fun updateAttributes(resource: HasMetadata, version: String?) {
        attributes.setResourceVersion(resource, version)
        attributes.setLastPushedPulled(resource)
    }

    private fun createMultiException(errors: List<ResourceException>): MultiResourceException {
        val message = errors.mapNotNull { error ->
             toNames(error.resources)
        }.joinToString()
        return MultiResourceException("Could not push $message", errors)
    }

    fun diff() {
        val manager = getPsiDocumentManager.invoke(project)
        val file = editor.file ?: return
        runInUI {
            val document = getDocument.invoke(editor) ?: return@runInUI
            val resourcesOnCluster = attributes.getAllClusterResources()
                .mapNotNull { clusterResource -> clusterResource.pull() }
            val serialized = serialize(resourcesOnCluster, getFileType(document, manager)) ?: return@runInUI
            val documentBeforeDiff = document.text
            diff.open(file, serialized) { onDiffClosed(documentBeforeDiff) }
        }
    }

    /*
     * Protected visibility for testing purposes:
     * Tried capturing callback parameter in #diff and running it, but it didn't work.
     * Document mock didn't return multiple return values (1.doc before diff, 2. doc after diff).
     * Made callback protected to be able to override it with public visibility in TestableResourceEditor instead
     * so that tests can call it directly.
     */
    protected open fun onDiffClosed(documentBeforeDiff: String?) {
        val afterDiff = getDocument.invoke(editor)?.text
        val modified = (documentBeforeDiff != afterDiff)
        if (modified) {
            update()
        }
    }

    fun startWatch(): ResourceEditor {
        attributes.getAllClusterResources().forEach { clusterResource -> clusterResource.watch() }
        return this
    }

    fun stopWatch() {
        attributes.getAllClusterResources()
            .forEach { clusterResource -> clusterResource.stopWatch() }
    }

    fun removeClutter() {
        resourceChangeMutex.withLock {
            editorResources.forEach { resource -> MetadataClutter.remove(resource.metadata) }
        }
        replaceDocument()
        runInUI {
            hideNotifications()
        }
    }

    /**
     * Returns `true` if this editor is editing the given resource.
     * Returns `false` otherwise.
     *
     * @param resource the resource to check if it's being edited by this editor
     * @return true if this editor is editing the given resource
     */
    fun isEditing(resource: HasMetadata): Boolean {
        return editorResources.find { editorResource ->
            resource.isSameResource(editorResource)
        } != null
    }

    private fun onResourceChanged(): IResourceModelListener {
        return object : IResourceModelListener {
            override fun added(added: Any) {
                // ignore
            }

            override fun removed(removed: Any) {
                update()
            }

            override fun modified(modified: Any) {
                update()
            }
        }
    }

    private fun onNamespaceOrContextChanged(): IResourceModelListener {
        return object : IResourceModelListener {
            override fun modified(modified: Any) {
                if (modified is IResourceModel) {
                    // active context changed, recreate cluster resource
                    attributes.disposeAll()
                    update()
                }
            }

            override fun currentNamespaceChanged(new: IActiveContext<*,*>?, old: IActiveContext<*,*>?) {
                // current namespace in same context has changed, recreate cluster resource
                attributes.disposeAll()
                update()
            }
        }
    }
    /**
     * Closes this instance and cleans up references to it.
     * - Removes the resource model listener,
     * - closes all [attributes],
     * - removes the references in editor- and editor file-userdata
     * - saves the resource version
     */
    fun close() {
        resourceModel.removeListener(onNamespaceContextChanged)
        // use backing variable to prevent accidental creation
        attributes.dispose()
        editor.putUserData(KEY_RESOURCE_EDITOR, null)
        editor.file?.putUserData(KEY_RESOURCE_EDITOR, null)
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
        resourceModel.removeListener(onNamespaceContextChanged)
        attributes.dispose()
    }
}
