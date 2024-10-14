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

    /**
     * Shows the notification for the given [EditorResource].
     * Notifications are mutually exclusive, only a single notification is shown at a time.
     * There's a precedence in notifications:
     * * Error overrides everything else
     * * Pushed overrides Pulled, DeletedOnCluster, Push
     * * Pulled override DeletedOnCluster, Push
     * * DeletedOnCluster override Push
     *
     * @param editorResource the resource to show the notification for
     * @param showSyncNotifications whether to show a Push notification.
     *
     * @see EditorResourceState
     */
    fun show(editorResource: EditorResource, showSyncNotifications: Boolean) {
        val state = editorResource.getState()
        val resource = editorResource.getResource()
        when {
            state is Error ->
                hideAllAndRun {
                    errorNotification.show(state.title, state.message, { errorNotification.hide() })
                }

            state is Pushed ->
                hideAllAndRun {
                    pushedNotification.show(listOf(editorResource), { pushedNotification.hide() })
                }

            state is Pulled ->
                hideAllAndRun {
                    pulledNotification.show(resource, { pulledNotification.hide() })
                }

            state is DeletedOnCluster
                    && showSyncNotifications ->
                hideAllAndRun {
                    deletedNotification.show(resource, { deletedNotification.hide() })
                }

            /**
             * avoid too many notifications, don't notify outdated
             *
            state is Outdated && showSyncNotification ->
            showPullNotification(resource)
             */

            state is Modified
                    && showSyncNotifications ->
                hideAllAndRun {
                    pushNotification.show(true, listOf(editorResource), { pushNotification.hide() })
                }

            else ->
                hideAll()
        }
    }

    fun show(editorResources: Collection<EditorResource>) {
        show(editorResources, true)
    }

    /**
     * Shows notifications for the given [EditorResource]s.
     * Notifications are stacked vertically but only the first notification has a dismiss button/action.
     *
     * @param editorResources the resources to show notifications for
     * @param showSyncNotifications whether to show a push notification
     *
     * @see EditorResourceState
     */
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

        runInUI {
            hideAll()
            if (showError) {
                showError(
                    inError,
                    dismissAction
                )
            }
            if (showPushed) {
                showPushed(
                    pushed,
                    // dismiss action only if there's no error-notification
                    !showError,
                    dismissAction
                )
            }
            if (showToPush) {
                showPush(
                    toPush,
                    // dismiss action only if there's no error- nor pushed-notification
                    !showError && !showPushed,
                    dismissAction
                )
            }
        }
    }

    private fun showError(inError: Collection<EditorResource>, dismissAction: (() -> Unit)?) {
        val error = inError.firstOrNull()?.getState() as? Error ?: return
        errorNotification.show(error.title, error.message, dismissAction)
    }

    private fun showPushed(pushed: Collection<EditorResource>, showDismissAction: Boolean, dismissAction: () -> Unit) {
        pushedNotification.show(
            pushed,
            if (showDismissAction) dismissAction else null
        )
    }

    private fun showPush(toPush: Collection<EditorResource>, showDismissAction: Boolean, dismissAction: () -> Unit) {
        pushNotification.show(
            false,
            toPush,
            if (showDismissAction) dismissAction else null
        )
    }

    /**
     * Show an error notification for the given title and message.
     *
     * @param title the title of the notification
     * @param message the message of the notification
     *
     * @see EditorResourceState
     */
    fun showError(title: String, message: String?) {
        runInUI {
            hideAll()
            errorNotification.show(title, message, { errorNotification.hide() })
        }
    }

    /** for testing purposes **/
    protected open fun dismiss(resources: Collection<EditorResource>) {
        resources.forEach { resource ->
            dismissed[HasMetadataIdentifier(resource.getResource())] = resource.getState()
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

    /**
     * Hides the following notifications, leaving the other ones alone if they are shown.
     *
     * * Push
     * * Pull
     * * Deleted
     *
     * @see EditorResourceState
     */
    fun hideSyncNotifications() {
        runInUI {
            pushNotification.hide()
            pullNotification.hide()
            deletedNotification.hide()
        }
    }

    /**
     * Hides all notifications if they're shown.
     */
    fun hideAll() {
        runInUI {
            pushNotification.hide()
            pushedNotification.hide()
            pullNotification.hide()
            pulledNotification.hide()
            deletedNotification.hide()
            errorNotification.hide()
        }
    }

    private fun hideAllAndRun(runnable: () -> Unit) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideAll()
            runnable.invoke()
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