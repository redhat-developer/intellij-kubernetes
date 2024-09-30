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

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.editor.Created
import com.redhat.devtools.intellij.kubernetes.editor.DeletedOnCluster
import com.redhat.devtools.intellij.kubernetes.editor.EditorResource
import com.redhat.devtools.intellij.kubernetes.editor.EditorResourceState
import com.redhat.devtools.intellij.kubernetes.editor.Error
import com.redhat.devtools.intellij.kubernetes.editor.Identical
import com.redhat.devtools.intellij.kubernetes.editor.Modified
import com.redhat.devtools.intellij.kubernetes.editor.Outdated
import com.redhat.devtools.intellij.kubernetes.editor.Updated
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import org.junit.Test

class NotificationsTest {
    private val pushNotification: PushNotification = mock()
    private val pushedNotification: PushedNotification = mock()
    private val pullNotification: PullNotification = mock()
    private val pulledNotification: PulledNotification = mock()
    private val deletedNotification: DeletedNotification = mock()
    private val errorNotification: ErrorNotification = mock()

    private val notifications = TestableNotifications(
        mock<FileEditor>(), mock<Project>(),
        pushNotification,
        pushedNotification,
        pullNotification,
        pulledNotification,
        deletedNotification,
        errorNotification
    )

    @Test
    fun `#show should hide all notifications if resource is outdated`() {
        // given
        val resource = editorResource(resource<Pod>("darth vader"), Outdated())
        // when
        notifications.show(resource)
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#show should hide all notifications if resource is identical`() {
        // given
        val resource = editorResource(resource<Pod>("obiwan"), Identical())
        // when
        notifications.show(resource)
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#show should hide all notifications if resource is modified`() {
        // given
        val resource = editorResource(resource<Pod>("luke"), Modified(true, true))
        // when
        notifications.show(resource)
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#show should show push notification if resource is modified`() {
        // given
        val darthVader = resource<Pod>("darth vader")
        val resources = listOf(
            editorResource(darthVader, Modified(true, true))
        )
        // when
        notifications.show(resources)
        // then
        verify(pushNotification).show(eq(false), containsAll(darthVader))
    }

    @Test
    fun `#show should NOT show push notification if resource is modified but showSyncNotifications is false`() {
        // given
        val darthVader = resource<Pod>("darth vader")
        val resources = listOf(
            editorResource(darthVader, Modified(true, true))
        )
        // when
        notifications.show(resources, false)
        // then
        verify(pushNotification, never()).show(any(), any())
    }

    @Test
    fun `#show should show push notification with modified resource if there are several resources and one is modified`() {
        // given
        val obiwan = resource<Pod>("obiwan")
        val resources = listOf(
            editorResource(resource<Pod>("luke"), Identical()),
            editorResource(obiwan, Modified(true, true))
        )
        // when
        notifications.show(resources)
        // then
        verify(pushNotification).show(eq(false), containsAll(obiwan))
    }

    @Test
    fun `#show should NOT show push notification if there are several resources and one is modified but showSyncNotifications is false`() {
        // given
        val obiwan = resource<Pod>("obiwan")
        val resources = listOf(
            editorResource(resource<Pod>("luke"), Identical()),
            editorResource(obiwan, Modified(true, true))
        )
        // when
        notifications.show(resources, false)
        // then
        verify(pushNotification, never()).show(any(), any())
    }

    @Test
    fun `#show should hide all notifications if resource is deleted`() {
        // given
        val resource = editorResource(resource<Pod>("darth vader"), DeletedOnCluster())
        // when
        notifications.show(resource)
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#show should show deleted notification if resource is deleted`() {
        // given
        val darthVader = resource<Pod>("darth vader")
        val resource = editorResource(darthVader, DeletedOnCluster())
        // when
        notifications.show(resource)
        // then
        verify(deletedNotification).show(darthVader)
    }

    @Test
    fun `#show should NOT show deleted notification if resource is deleted but showSyncNotifications is false`() {
        // given
        val darthVader = resource<Pod>("darth vader")
        val resource = editorResource(darthVader, DeletedOnCluster())
        // when
        notifications.show(resource, false)
        // then
        verify(deletedNotification, never()).show(any())
    }

    @Test
    fun `#show should NOT show push notification if resource is deleted but showSyncNotifications is false`() {
        // given
        val darthVader = resource<Pod>("darth vader")
        val resources = listOf(
            editorResource(darthVader, DeletedOnCluster())
        )
        // when
        notifications.show(resources, false)
        // then
        verify(pushNotification, never()).show(any(), any())
    }

    @Test
    fun `#show should show push notification with deleted resources if there are several resources and several are deleted`() {
        // given
        val darthVader = resource<Pod>("darth vader")
        val emperor = resource<Pod>("emperor")
        val resources = listOf(
            editorResource(emperor, DeletedOnCluster()),
            editorResource(resource<Pod>("leia"), Identical()),
            editorResource(darthVader, DeletedOnCluster())
        )
        // when
        notifications.show(resources)
        // then
        verify(pushNotification).show(eq(false), containsAll(emperor, darthVader))
    }

    @Test
    fun `#show should hide all notifications if resource is in error`() {
        // given
        val resource = editorResource(resource<Pod>("darth vader"), Error("disturbance in the force"))
        // when
        notifications.show(resource)
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#show should show error notification if resource is in error`() {
        // given
        val title = "disturbance in the force"
        val message = "need to meditate more"
        val darthVader = resource<Pod>("darth vader")
        val resources = listOf(
            editorResource(darthVader, Error(title, message))
        )
        // when
        notifications.show(resources)
        // then
        verify(errorNotification).show(title, message)
    }

    @Test
    fun `#show should show pushed notification if resource was created`() {
        // given
        val luke = resource<Pod>("luke")
        val resource = editorResource(luke, Created())
        // when
        notifications.show(resource, true)
        // then
        verify(pushedNotification).show(containsAll(luke))
    }

    @Test
    fun `#show should show pushed notification if resource was created even if showSyncNotifications is false`() {
        // given
        val luke = resource<Pod>("luke")
        val resource = editorResource(luke, Created())
        // when
        notifications.show(resource, false)
        // then
        verify(pushedNotification).show(containsAll(luke))
    }

    @Test
    fun `#show should show pushed notification if resource was updated`() {
        // given
        val r2d2 = resource<Pod>("r2d2")
        val resource = editorResource(r2d2, Updated())
        // when
        notifications.show(resource, true)
        // then
        verify(pushedNotification).show(containsAll(r2d2))
    }

    @Test
    fun `#show should show pushed notification if resource was updated even if showSyncNotifications is false`() {
        // given
        val r2d2 = resource<Pod>("r2d2")
        val resource = editorResource(r2d2, Updated())
        // when
        notifications.show(resource, false)
        // then
        verify(pushedNotification).show(containsAll(r2d2))
    }

    private fun editorResource(resource: HasMetadata, state: EditorResourceState): EditorResource {
        return mock<EditorResource> {
            on { getResource() } doReturn resource
            on { getState() } doReturn state
        }
    }

    private fun containsAll(vararg resources: HasMetadata): List<EditorResource> {
        return argWhere<List<EditorResource>> { editorResources ->
            editorResources.size == resources.size
                    && editorResources.map { it.getResource() }
                .containsAll(resources.toList())
        }
    }

    private fun verifyHideAllNotifications() {
        verify(errorNotification).hide()
        verify(pullNotification).hide()
        verify(deletedNotification).hide()
        verify(pushNotification).hide()
        verify(pushedNotification).hide()
        verify(pulledNotification).hide()
    }

    private class TestableNotifications(
        editor: FileEditor,
        project: Project,
        pushNotification: PushNotification,
        pushedNotification: PushedNotification,
        pullNotification: PullNotification,
        pulledNotification: PulledNotification,
        deletedNotification: DeletedNotification,
        errorNotification: ErrorNotification
    ) : Notifications(
        editor,
        project,
        pushNotification,
        pushedNotification,
        pullNotification,
        pulledNotification,
        deletedNotification,
        errorNotification
    ) {
        override fun runInUI(runnable: () -> Unit) {
            // run directly
            runnable.invoke()
        }
    }
}


