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
import com.redhat.devtools.intellij.kubernetes.editor.Error
import com.redhat.devtools.intellij.kubernetes.editor.FILTER_ERROR
import com.redhat.devtools.intellij.kubernetes.editor.FILTER_TO_PUSH
import com.redhat.devtools.intellij.kubernetes.editor.Modified
import com.redhat.devtools.intellij.kubernetes.editor.Pushed
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

    fun show(editorResource: EditorResource) {
        show(editorResource, true)
    }

    fun show(editorResource: EditorResource, showSyncNotifications: Boolean) {
        val state = editorResource.getState()
        val resource = editorResource.getResource()
        when  {
            state is Error ->
                showError(state.title, state.message)

            state is Pushed ->
                showPushed(listOf(editorResource))

            state is DeletedOnCluster
                    && showSyncNotifications ->
                showDeleted(resource)

            /**
             * avoid too many notifications, don't notify outdated
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
        val toPush = editorResources.filter(FILTER_TO_PUSH)
        if (toPush.isNotEmpty()
            && showSyncNotifications) {
            showPush(false, toPush)
            return
        }
        val inError = editorResources.filter(FILTER_ERROR)
        if (inError.isNotEmpty()) {
            showError(inError)
        } else {
            hideAll()
        }
    }

    fun showError(title: String, message: String?) {
        runInUI {
            hideAll()
            errorNotification.show(title, message)
        }
    }

    private fun showError(editorResources: Collection<EditorResource>) {
        val inError = editorResources.filter(FILTER_ERROR)
        val toDisplay = inError.firstOrNull()?.getState() as? Error ?: return
        showError(toDisplay.title, toDisplay.message)
    }

    private fun showPush(showPull: Boolean, editorResources: Collection<EditorResource>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideAll()
            pushNotification.show(showPull, editorResources)
        }
    }

    private fun showPushed(editorResources: Collection<EditorResource>) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideAll()
            pushedNotification.show(editorResources)
        }
    }

    private fun showPull(resource: HasMetadata) {
        runInUI {
            hideAll()
            pullNotification.show(resource)
        }
    }

    private fun showDeleted(resource: HasMetadata) {
        runInUI {
            // hide & show in the same UI thread runnable avoid flickering
            hideAll()
            deletedNotification.show(resource)
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