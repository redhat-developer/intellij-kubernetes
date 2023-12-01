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
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushedNotification
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.model.util.toTitle
import io.fabric8.kubernetes.api.model.HasMetadata
import java.util.concurrent.CompletableFuture

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
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo? = { file, project ->
        com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesResourceInfo(file, project)
    },
    // for mocking purposes
    private val diff: ResourceDiff = ResourceDiff(project),
    // for mocking purposes
    protected val editorResources: EditorResources = EditorResources(resourceModel)
) : Disposable {

    init {
        Disposer.register(editor, this)
        editorResources.resourceChangedListener = onResourceChanged()
        resourceModel.addListener(onNamespaceOrContextChanged())
        runAsync { enableEditingNonProjectFile() }
    }

    companion object {
        val KEY_RESOURCE_EDITOR = Key<ResourceEditor>(ResourceEditor::class.java.name)
        val KEY_TOOLBAR = Key<ActionToolbar>(ActionToolbar::class.java.name)
        const val ID_TOOLBAR = "Kubernetes.Editor.Toolbar"
    }

    private var onNamespaceContextChanged: IResourceModelListener = onNamespaceOrContextChanged()

    /**
     * Updates this editor notifications and title.
     *
     * @see [replaceDocument]
     */
    fun update() {
        runAsync {
            try {
                val resources = createResources(
                    getDocument(editor),
                    editor.file?.fileType,
                    resourceModel.getCurrentNamespace()
                )
                val editorResources = editorResources.setResources(resources)

                if (editorResources.size == 1) {
                    // show notification for 1 resource
                    val editorResource = editorResources.firstOrNull() ?: return@runAsync
                    showNotification(editorResource)
                } else if (editorResources.size > 1) {
                    // show notification for multiple resources
                    showNotification(editorResources)
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

    open protected fun updateDeleted(deleted: HasMetadata?) {
        if (deleted == null) {
            return
        }
        editorResources.setDeleted(deleted)
        update()
    }

    private fun showNotification(editorResource: EditorResource) {
        val state = editorResource.getState()
        val resource = editorResource.getResource()
        when (state) {
            is Error ->
                showErrorNotification(state.title, state.message)
            is Pushed ->
                showPushedNotification(listOf(editorResource))
            is DeletedOnCluster ->
                showDeletedNotification(resource)
/**
 * avoid too many notifications, don't notify outdated
            is Outdated ->
                showPullNotification(resource)
 */
            is Modified ->
                showPushNotification(true, listOf(editorResource))

            else ->
                runInUI {
                    hideNotifications()
                }
        }
    }

    private fun showNotification(editorResources: Collection<EditorResource>) {
        val toPush = editorResources.filter(FILTER_TO_PUSH)
        if (toPush.isNotEmpty()) {
            showPushNotification(false, toPush)
            return
        }
        val inError = editorResources.filter(FILTER_ERROR)
        if (inError.isNotEmpty()) {
            showErrorNotification(inError)
        } else {
            runInUI {
                hideNotifications()
            }
        }
    }

    private fun showErrorNotification(title: String, message: String?) {
        runInUI {
            hideNotifications()
            errorNotification.show(title, message)
        }
    }

    private fun showErrorNotification(editorResources: Collection<EditorResource>) {
        val inError = editorResources.filter(FILTER_ERROR)
        val toDisplay = inError.firstOrNull()?.getState() as? Error ?: return
        showErrorNotification(toDisplay.title, toDisplay.message)
    }

    private fun showPushNotification(showPull: Boolean, editorResources: Collection<EditorResource>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideNotifications()
            pushNotification.show(showPull, editorResources)
        }
    }

    private fun showPushedNotification(editorResources: Collection<EditorResource>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideNotifications()
            pushedNotification.show(editorResources)
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
     * Pulls the resource from the cluster and replaces the content of this editor.
     * Does nothing if it doesn't exist.
     */
    fun pull() {
        val resources = editorResources.getAllResources()
        if (resources.size > 1) {
            // dont pull if multi-resource
            return
        }
        runInUI {
            // hide before running pull. Pull may take quite some time on remote cluster
            hideNotifications()
        }
        val resource = resources.firstOrNull() ?: return

        runAsync {
            editorResources.pull(resource)
            runInUI {
                if (!replaceDocument(editorResources.getAllResources())) {
                    update()
                }
            }
        }
    }

    private fun replaceDocument(resources: Collection<HasMetadata>): Boolean {
        val manager = getPsiDocumentManager.invoke(project)
        val document = getDocument.invoke(editor) ?: return false
        val jsonYaml = serialize.invoke(resources, getFileType(document, manager)) ?: return false
        return if (document.text.trim() != jsonYaml) {
            runWriteCommand {
                document.replaceString(0, document.textLength, jsonYaml)
                manager.commitDocument(document)
            }
            true
        } else {
            false
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
    fun push(all: Boolean) {
        runInUI {
            // hide before running push. Push may take quite some time on remote cluster
            hideNotifications()
        }
        runAsync {
            if (all) {
                editorResources.pushAll(FILTER_ALL)
            } else {
                editorResources.pushAll(FILTER_TO_PUSH)
            }
            update()
        }
    }

    open fun diff(): CompletableFuture<Unit> {
        return CompletableFuture
            .supplyAsync(
                // UI thread required
                {
                    val file = editor.file ?: throw ResourceException("Editor ${editor.name} has no file.")
                    val manager = getPsiDocumentManager.invoke(project)
                    val document = getDocument.invoke(editor) ?: throw ResourceException("Could not get document for editor ${editor.name}.")
                    val fileType = getFileType(document, manager) ?: throw ResourceException("Could not determine file type for editor ${editor.name}.")
                    DiffContext(file, fileType, document.text)
                },
                { runInUI { it.run() } }
            ).thenApplyAsync { context ->
                val resourcesOnCluster = editorResources.getAllResourcesOnCluster()
                val serialized = serialize(resourcesOnCluster, context.fileType) ?: throw ResourceException("Could not serialize cluster resources for editor ${editor.name}.")
                context.clusterResources = serialized
                context
            }.thenApplyAsync(
                // UI thread required
                { context ->
                    diff.open(context.file, context.clusterResources) { onDiffClosed(context.document) }
                },
                { runInUI { it.run() } }
            )
    }

    private class DiffContext(
        val file: VirtualFile,
        val fileType: FileType,
        val document: String
    ) {
        lateinit var clusterResources: String
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
        editorResources.watchAll()
        return this
    }

    fun stopWatch() {
        editorResources.stopWatchAll()
    }

    fun removeClutter() {
        val resources = editorResources.getAllResources().map { resource ->
            MetadataClutter.remove(resource.metadata)
            resource
        }
        runInUI {
            replaceDocument(resources)
            hideNotifications()
        }
    }

    private fun createResources(document: Document?, fileType: FileType?, namespace: String?): List<HasMetadata> {
        return createResources.invoke(
            document?.text,
            fileType,
            namespace
        )
    }

    /**
     * Returns `true` if this editor is editing the given resource.
     * Returns `false` otherwise.
     *
     * @param resource the resource to check if it's being edited by this editor
     * @return true if this editor is editing the given resource
     */
    fun isEditing(resource: HasMetadata): Boolean {
        return editorResources.hasResource(resource)
    }

    fun getResources(): List<HasMetadata> {
        return editorResources.getAllResources()
    }

    private fun onResourceChanged(): IResourceModelListener {
        return object : IResourceModelListener {
            override fun added(added: Any) {
                // ignore
            }

            override fun removed(removed: Any) {
                updateDeleted(removed as? HasMetadata)
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
                    editorResources.disposeAll()
                    update()
                }
            }

            override fun currentNamespaceChanged(new: IActiveContext<*, *>?, old: IActiveContext<*, *>?) {
                // current namespace in same context has changed, recreate cluster resource
                editorResources.disposeAll()
                update()
            }
        }
    }

    /**
     * Closes this instance and cleans up references to it.
     * - Removes the resource model listener,
     * - closes all [editorResources],
     * - removes the references in editor- and editor file-userdata
     * - saves the resource version
     */
    fun close() {
        resourceModel.removeListener(onNamespaceContextChanged)
        // use backing variable to prevent accidental creation
        editorResources.dispose()
        editor.putUserData(KEY_RESOURCE_EDITOR, null)
        editor.file?.putUserData(KEY_RESOURCE_EDITOR, null)
    }

    /**
     * Enables editing of non project files for the file in this editor. This prevents the IDE from presenting the
     * "edit non-project" files dialog.
     */
    protected open fun enableEditingNonProjectFile() {
        if (editor.file == null
            || !isKubernetesResource(
                getKubernetesResourceInfo.invoke(editor.file, project)
            )
        ) {
            return
        }
        createResourceFileForVirtual(editor.file)?.enableEditingNonProjectFile()
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
    protected open fun <R : Any> runReadCommand(runnable: () -> R?): R? {
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
        editorResources.dispose()
    }
}
