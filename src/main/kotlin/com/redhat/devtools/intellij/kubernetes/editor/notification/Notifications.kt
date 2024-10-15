/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.notification

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.impl.nestedCompletions.letIf
import com.redhat.devtools.intellij.kubernetes.editor.DeletedOnCluster
import com.redhat.devtools.intellij.kubernetes.editor.EditorResource
import com.redhat.devtools.intellij.kubernetes.editor.EditorResourceState
import com.redhat.devtools.intellij.kubernetes.editor.Error
import com.redhat.devtools.intellij.kubernetes.editor.FILTER_ERROR
import com.redhat.devtools.intellij.kubernetes.editor.FILTER_PUSHED
import com.redhat.devtools.intellij.kubernetes.editor.FILTER_TO_PUSH
import com.redhat.devtools.intellij.kubernetes.editor.Modified
import com.redhat.devtools.intellij.kubernetes.editor.Pulled
import com.redhat.devtools.intellij.kubernetes.editor.Pushed
import com.redhat.devtools.intellij.kubernetes.model.util.HasMetadataIdentifier
import io.fabric8.kubernetes.api.model.HasMetadata

open class Notifications(
    editor: FileEditor,
    project: Project,
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
) {

    private val dismissed = mutableMapOf<HasMetadataIdentifier, EditorResourceState>()

    fun show(editorResource: EditorResource) {
        show(editorResource, true)
    }

    fun show(editorResource: EditorResource, showSyncNotifications: Boolean) {
        val state = editorResource.getState()
        val resource = editorResource.getResource()
        when  {
            state is Error ->
                showError(state)

            state is Pushed ->
                showPushed(listOf(editorResource))

            state is Pulled ->
                showPulled(resource)

            state is DeletedOnCluster
                    && showSyncNotifications ->
                showDeleted(resource)

            /**
             * avoid too many notifications, don't notify outdated
             *
                 state is Outdated && showSyncNotification ->
                     showPullNotification(resource)
             */

            state is Modified
                    && showSyncNotifications ->
                showPush(true, listOf(editorResource))

            else ->
                hideAll()
        }
    }

    fun show(editorResources: Collection<EditorResource>) {
        show(editorResources, true)
    }

    fun show(editorResources: Collection<EditorResource>, showSyncNotifications: Boolean) {
        removeUpdatedDismissed(editorResources)
        val inError = getNonDismissed(editorResources.filter(FILTER_ERROR))
        val showError = inError.isNotEmpty();
        val pushed = getNonDismissed(editorResources.filter(FILTER_PUSHED))
        val showPushed = pushed.isNotEmpty()
        val toPush = getNonDismissed(editorResources.filter(FILTER_TO_PUSH))
        val showToPush = toPush.isNotEmpty() && showSyncNotifications
        val dismissAction = {
            dismiss(editorResources)
            hideAll()
        }

        if (!showError
            && !showPushed
            && !showToPush) {
            return
        }
        runInUI {
            hideAll()
            val error = inError.firstOrNull()?.getState() as? Error
            if (error != null) {
                errorNotification.show(
                    error.title,
                    error.message,
                    dismissAction)
            }
            if (showPushed) {
                pushedNotification.show(
                    pushed,
                    // close action only if there was no error notification
                    dismissAction.letIf(showError) { null }
                )
            }
            if (showToPush) {
                pushNotification.show(
                    false,
                    toPush,
                    // close action only if there was no error nor pushed notification
                    dismissAction.letIf(showError || showPushed) { null })
            }
        }
    }

    fun showError(error: Error) {
        runInUI {
            hideAll()
            errorNotification.show(error.title, error.message) {
                errorNotification.hide()
            }
        }
    }

    fun showError(title: String, message: String?) {
        showError(Error(title, message))
    }

    private fun getError(editorResources: Collection<EditorResource>) {
        val inError = editorResources.filter(FILTER_ERROR)
        val toDisplay = inError.firstOrNull()?.getState() as? Error ?: return
        showError(toDisplay)
    }

    private fun showPush(showPull: Boolean, editorResources: Collection<EditorResource>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideAll()
            pushNotification.show(showPull, editorResources, {
                dismiss(editorResources)
                hideAll()
            })
        }
    }

    private fun showPushed(editorResources: Collection<EditorResource>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            pushedNotification.show(editorResources, { pushedNotification.hide() })
        }
    }

    private fun showPull(resource: HasMetadata) {
        runInUI {
            hideAll()
            pullNotification.show(resource, { pullNotification.hide() })
        }
    }

    private fun showPulled(resource: HasMetadata) {
        runInUI {
            hideAll()
            pulledNotification.show(resource, { pulledNotification.hide() })
        }
    }

    private fun showDeleted(resource: HasMetadata) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideAll()
            deletedNotification.show(resource, { deletedNotification.hide() })
        }
    }

    private fun dismiss(resources: Collection<EditorResource>) {
        resources.forEach { resource ->
            dismissed.put(HasMetadataIdentifier(resource.getResource()), resource.getState())
        }
    }

    private fun isDismissedButUpdated(editorResource: EditorResource): Boolean {
        val dismissed = dismissed[HasMetadataIdentifier(editorResource.getResource())] ?: return false
        return editorResource.getState() != dismissed
    }

    private fun isDismissed(editorResource: EditorResource): Boolean {
        return dismissed[HasMetadataIdentifier(editorResource.getResource())] != null
    }

    private fun getNonDismissed(editorResources: Collection<EditorResource>): Collection<EditorResource> {
        return editorResources.filter { editorResource ->
            !isDismissed(editorResource)
        }
    }

    private fun removeUpdatedDismissed(editorResources: Collection<EditorResource>) {
        editorResources.forEach { editorResource ->
            if (isDismissedButUpdated(editorResource)) {
                dismissed.remove(HasMetadataIdentifier(editorResource.getResource()))
            }
        }
    }

    fun hideSyncNotifications() {
        runInUI {
            pushNotification.hide()
            pullNotification.hide()
            deletedNotification.hide()
        }
    }

    fun hideAll() {
        runInUI {
            pushNotification.hide()
            pushedNotification.hide()
            pullNotification.hide()
            deletedNotification.hide()
            pulledNotification.hide()
            errorNotification.hide()
        }
    }

    protected open fun runInUI(runnable: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runnable.invoke()
        } else {
            ApplicationManager.getApplication().invokeLater(runnable)
        }
    }
}