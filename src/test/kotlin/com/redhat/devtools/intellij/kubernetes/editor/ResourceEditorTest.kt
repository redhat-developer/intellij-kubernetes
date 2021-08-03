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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.resourceFile
import com.redhat.devtools.intellij.kubernetes.model.Clients
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.verification.VerificationMode

class ResourceEditorTest {

    private val allNamespaces = arrayOf(ClientMocks.NAMESPACE1, ClientMocks.NAMESPACE2, ClientMocks.NAMESPACE3)
    private val currentNamespace = ClientMocks.NAMESPACE2
    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL = PodBuilder(POD2)
        .editMetadata()
            .withName("Gargamel")
            .withNamespace("namespace2")
            .withResourceVersion("1")
        .endMetadata()
        .withApiVersion("v1")
        .build()
    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL_WITH_LABEL = PodBuilder(GARGAMEL)
        .editMetadata()
        .withLabels(mapOf(Pair("hat", "none")))
        .endMetadata()
        .build()
    // need real resources, not mocks - #equals used to track changes
    private val GARGAMELv2 = PodBuilder(GARGAMEL)
        .editMetadata()
        .withResourceVersion("2")
        .endMetadata()
        .build()
    // need real resources, not mocks - #equals used to track changes
    private val AZRAEL = PodBuilder(POD3)
        .editMetadata()
            .withName("Azrael")
            .withNamespace("namespace2")
            .withResourceVersion("1")
        .endMetadata()
        .withApiVersion("v1")
        .build()
    private val localCopy = GARGAMEL
    private val virtualFile: VirtualFile = mock()
    private val fileEditor: FileEditor = mock<FileEditor>().apply {
        doReturn(virtualFile)
            .whenever(this).file
    }
    private val resourceFile: ResourceFile = mock<ResourceFile>().apply {
        doReturn(virtualFile)
            .whenever(this).write(any())
    }
    private val createResourceFileForVirtual: (file: VirtualFile?) -> ResourceFile? =
        mock<(file: VirtualFile?) -> ResourceFile?>().apply {
            doReturn(resourceFile)
                .whenever(this).invoke(any())
        }
    private val createResourceFileForResource: (resource: HasMetadata?) -> ResourceFile? =
        mock<(file: HasMetadata?) -> ResourceFile?>().apply {
            doReturn(resourceFile)
                .whenever(this).invoke(any())
        }

    private val project: Project = mock()
    private val clients: Clients<KubernetesClient> =
        Clients(client(currentNamespace.metadata.name, allNamespaces))
    private val createResource: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? =
        mock<(editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata?>().apply  {
            doReturn(localCopy)
                .whenever(this).invoke(any(), any())
        }
    private val clusterResource: ClusterResource = mock<ClusterResource>().apply {
        doReturn(GARGAMELv2)
            .whenever(this).get(any())
    }
    private val createClusterResource: (HasMetadata, Clients<out KubernetesClient>) -> ClusterResource =
        mock<(HasMetadata, Clients<out KubernetesClient>) -> ClusterResource>().apply {
            doReturn(clusterResource)
                .whenever(this).invoke(any(), any())
        }
    private val pushNotification: PushNotification = mock()
    private val pullNotification: PullNotification = mock()
    private val pulledNotification: PulledNotification = mock()
    private val deletedNotification: DeletedNotification = mock()
    private val errorNotification: ErrorNotification = mock()
    private val document: Document = mock<Document>().apply {
        doReturn("")
            .whenever(this).getText()
    }
    private val documentProvider: (FileEditor) -> Document? = { document }
    private val psiDocumentManager: PsiDocumentManager = mock()
    private val psiDocumentManagerProvider: (Project) -> PsiDocumentManager = { psiDocumentManager }
    private val ideNotification: Notification = mock()

    private val editor = spy(
        TestableResourceEditor(
            localCopy,
            fileEditor,
            project,
            clients,
            createResource,
            createClusterResource,
            createResourceFileForVirtual,
            createResourceFileForResource,
            pushNotification,
            pullNotification,
            pulledNotification,
            deletedNotification,
            errorNotification,
            documentProvider,
            psiDocumentManagerProvider,
            ideNotification
        )
    )

    @Test
    fun `#update should hide all notifications when resource on cluster is deleted`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isDeleted()
        doReturn(false)
            .whenever(clusterResource).isModified(any())
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when resource on cluster is outdated`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when editor resource can be pushed`() {
        // given
        doReturn(true)
            .whenever(clusterResource).canPush(any())
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should show error notification and hide all notifications if creating resource throws ResourceException`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification).show("resource error", "client error")
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should rename file if resource in editor has different name`() {
        // given editor resource is AZRAEL
        doReturn(AZRAEL)
            .whenever(createResource).invoke(any(), any())
        // editor resource is GARGAMEL
        val editorFile: ResourceFile = resourceFile(GARGAMEL.metadata.name)
        doReturn(editorFile)
            .whenever(createResourceFileForVirtual).invoke(any())
        val resourceFile: ResourceFile = resourceFile(AZRAEL.metadata.name)
        doReturn(resourceFile)
            .whenever(createResourceFileForResource).invoke(any())
        // when
        editor.update()
        // then rename to AZRAEL
        verify(editorFile).rename(AZRAEL)
    }

    @Test
    fun `#update should NOT rename file if resource in editor and file have same name`() {
        // given editor resource is GARGAMEL
        doReturn(GARGAMEL)
            .whenever(createResource).invoke(any(), any())
        // editor resource is GARGAMEL
        val editorFile: ResourceFile = resourceFile(GARGAMEL.metadata.name)
        doReturn(editorFile)
            .whenever(createResourceFileForVirtual).invoke(any())
        val resourceFile: ResourceFile = resourceFile(GARGAMEL.metadata.name)
        doReturn(resourceFile)
            .whenever(createResourceFileForResource).invoke(any())
        // when
        editor.update()
        // then dont rename because same name
        verify(editorFile, never()).rename(any())
    }

    @Test
    fun `#update should show deleted notification if resource on cluster is deleted and editor resource is NOT modified`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isDeleted()
        doReturn(false)
            .whenever(clusterResource).isModified(any())
        // when
        editor.update()
        // then
        verify(deletedNotification).show(any())
    }

    @Test
    fun `#update should NOT show deleted notification if resource on cluster is deleted but editor resource is modified`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isDeleted()
        doReturn(true)
            .whenever(clusterResource).isModified(any())
        // when
        editor.update()
        // then
        verify(deletedNotification, never()).show(any())
    }

    @Test
    fun `#update should show modified notification if resource on cluster is modified and there are local changes to resource`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())
        doReturn(GARGAMEL)
            .whenever(clusterResource).get(any())
        doReturn(GARGAMEL_WITH_LABEL) // editor resource is modified
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(pullNotification).show(any())
    }

    @Test
    fun `#update should NOT show modified notification after editor is replaced`() {
        // given
        doReturn(false)
            .whenever(clusterResource).isDeleted() // dont show deleted notification
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())  // dont show modified notification
        doReturn(AZRAEL) // cluster resource is different -> editor is outdated
            .whenever(clusterResource).get(any())
        whenever(createResource.invoke(any(), any()))
            .doReturn(
                GARGAMELv2, // 1st call to factory in #update: editor is modified -> modified notification, no automatic reload
                AZRAEL) // 2nd call to factory in #update after replace: editor has resource from cluster -> no modified notification
        editor.update() // 1st call: local changes, shows modified notification
        editor.pull()
        // when
        editor.update() // 2nd call: no local changes (editor content was replaced), no modified notification
        // then modification notification only shown once even though #update called twice
        verify(pullNotification, times(1)).show(any())
    }

    @Test
    fun `#update should NOT show modified notification if resource on cluster is modified BUT there are NO local changes to resource`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())
        doReturn(GARGAMEL)
            .whenever(clusterResource).get(any())
        doReturn(GARGAMEL)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(pullNotification, never()).show(any())
    }

    @Test
    fun `#update should show reloaded notification if resource on cluster is modified BUT there are NO local changes to resource`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())
        doReturn(GARGAMEL)
            .whenever(clusterResource).get(any())
        doReturn(GARGAMEL)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(pulledNotification).show(any())
    }

    @Test
    fun `#update should set text of document if resource on cluster is modified and there are NO local changes to resource`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())
        doReturn(GARGAMEL)
            .whenever(clusterResource).get(any())
        doReturn(GARGAMEL)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(document).replaceString(0, document.textLength - 1, Serialization.asYaml(GARGAMEL))
    }

    @Test
    fun `#update should show push notification if resource is modified and can push resource to cluster`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isModified(any())
        doReturn(true)
            .whenever(clusterResource).canPush(any())
        // when
        editor.update()
        // then
        verify(pushNotification).show()
    }

    @Test
    fun `#update after a #pull should do nothing bcs it was triggered by #replaceDocument (replace triggers editor transaction listener and thus #update)`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).get(any())
        editor.pull()
        clearAllNotificationInvocations()
        // when
        editor.update()
        // then
        verifyShowAllNotifications(never())
    }

    @Test
    fun `#pull should replace document`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).get(any())
        // when
        editor.pull()
        // then
        verify(document).replaceString(0, document.textLength - 1, Serialization.asYaml(GARGAMELv2))
    }

    @Test
    fun `#pull should NOT replace document if resource is equal`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).push(any())
        doReturn(Serialization.asYaml(GARGAMELv2))
            .whenever(document).getText()
        // when
        editor.pull()
        // then
        verify(document, never()).replaceString(any(), any(), any())
    }

    @Test
    fun `#pull should NOT replace document if resource differs by a newline`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).push(any())
        doReturn(Serialization.asYaml(GARGAMELv2) + "\n")
            .whenever(document).getText()
        // when
        editor.pull()
        // then
        verify(document, never()).replaceString(any(), any(), any())
    }

    @Test
    fun `#pull should show pulled notification`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).get(any())
        // when
        editor.pull()
        // then
        verify(pulledNotification).show(GARGAMELv2)
    }

    @Test
    fun `#pull should hide all notifications`() {
        // given
        // when
        editor.pull()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#push should push resource to cluster`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isModified(any())
        doReturn(true)
            .whenever(clusterResource).canPush(any())
        // when
        editor.push()
        // then
        verify(clusterResource).push(any())
    }

    @Test
    fun `#push should replace text of document`() {
        // given
        doReturn(GARGAMEL)
            .whenever(createResource).invoke(any(), any())
        doReturn(GARGAMELv2)
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(document).replaceString(0, document.textLength - 1, Serialization.asYaml(GARGAMELv2))
    }

    @Test
    fun `#push should show pulled notification`() {
        // given
        doReturn(GARGAMEL)
            .whenever(createResource).invoke(any(), any())
        doReturn(GARGAMELv2)
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(pulledNotification).show(GARGAMELv2)
    }

    @Test
    fun `#push should show error notification if parsing editor content throws`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createResource).invoke(any(), any())
        // when
        editor.push()
        // then
        verify(errorNotification).show("resource error", "client error")
    }

    @Test
    fun `#push should show error notification if pushing to cluster throws ResourceException`() {
        // given
        doThrow(ResourceException("client error"))
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(ideNotification).error(any(), argWhere { it.contains("client error", false) })
    }

    @Test
    fun `#enableNonProjectFileEditing should call #enableNonProjectFileEditing on editor file`() {
        // given
        // when
        editor.enableNonProjectFileEditing()
        // then
        verify(resourceFile).enableNonProjectFileEditing()
    }

    @Test
    fun `#enableNonProjectFileEditing should NOT call #enableNonProjectFileEditing if editor file is null`() {
        // given
        doReturn(null)
            .whenever(fileEditor).file
        // when
        editor.enableNonProjectFileEditing()
        // then
        verify(resourceFile, never()).enableNonProjectFileEditing()
    }

    @Test
    fun `#existsOnCluster should return true if resource does not exist on cluster`() {
        // given
        doReturn(true)
            .whenever(clusterResource).exists()
        // when
        val exists = editor.existsOnCluster()
        // then
        assertThat(exists).isTrue()
    }

    @Test
    fun `#existsOnCluster should return false if resource does not exist on cluster`() {
        // given
        doReturn(false)
            .whenever(clusterResource).exists()
        // when
        val exists = editor.existsOnCluster()
        // then
        assertThat(exists).isFalse()
    }

    @Test
    fun `#isOutdated should return true if editor resource is outdated compared to the one on the cluster`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isOutdated(any())
        // when
        val exists = editor.isOutdated()
        // then
        assertThat(exists).isTrue()
    }

    @Test
    fun `#isOutdated should return false if editor resource is NOT outdated compared to the one on the cluster`() {
        // given
        doReturn(false)
            .whenever(clusterResource).isOutdated(any())
        // when
        val exists = editor.isOutdated()
        // then
        assertThat(exists).isFalse()
    }

    @Test
    fun `#replaceContent should set text of document`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).get(any())
        // when
        editor.pull()
        // then
        verify(document).replaceString(0, document.textLength - 1, Serialization.asYaml(GARGAMELv2))
    }

    @Test
    fun `#replaceContent should commit document`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).get(any())
        // when
        editor.pull()
        // then
        verify(psiDocumentManager).commitDocument(document)
    }

    @Test
    fun `#startWatch should start watching the cluster`() {
        // given
        // when
        editor.startWatch()
        // then
        verify(clusterResource, atLeastOnce()).watch() // creating cluster resource also starts watching
    }

    @Test
    fun `#stopWatch should stop watching the cluster`() {
        // given
        // when
        editor.stopWatch()
        // then
        verify(clusterResource, atLeastOnce()).stopWatch()
    }

    @Test
    fun `IResourceChangeListener#removed should show deleted notification`() {
        // given
        doReturn(true)
            .whenever(clusterResource).isDeleted()
        doReturn(false)
            .whenever(clusterResource).isModified(any())
        editor.startWatch() // create cluster
        val listener = captureClusterResourceListener(clusterResource) // listener added to cluster
        assertThat(listener).isNotNull()
        // when
        listener!!.removed(GARGAMEL)
        // then
        verify(deletedNotification).show(GARGAMEL)
    }

    @Test
    fun `IResourceChangeListener#modified should show pull notification if editor is modified`() {
        // given
        doReturn(GARGAMEL) // cluster has GARGAMEL
            .whenever(clusterResource).get(any())
        doReturn(false) // dont show deleted notification
            .whenever(clusterResource).isDeleted()
        doReturn(true) // local copy is outdated
            .whenever(clusterResource).isOutdated(any())
        editor.editorResource = GARGAMEL_WITH_LABEL // editor document is modified, is GARGAMEL_WITH_LABEL
        editor.startWatch() // create cluster
        val listener = captureClusterResourceListener(clusterResource) // listener added to cluster
        assertThat(listener).isNotNull()
        // when
        listener!!.modified(GARGAMEL)
        // then
        verify(pullNotification).show(GARGAMEL)
    }

    @Test
    fun `IResourceChangeListener#modified should replace document and show pulled notification if editor is NOT modified`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).get(any())
        doReturn(false) // dont show deleted notification
            .whenever(clusterResource).isDeleted()
        doReturn(true) // local copy is outdated
            .whenever(clusterResource).isOutdated(any())
        editor.startWatch() // create cluster
        val listener = captureClusterResourceListener(clusterResource) // listener added to cluster
        assertThat(listener).isNotNull()
        // when
        listener!!.modified(GARGAMEL)
        // then
        verify(document).replaceString(0, document.textLength - 1, Serialization.asYaml(GARGAMELv2))
        verify(pulledNotification).show(any())
    }

    /**
     * Captures the [ModelChangeObservable.IResourceChangeListener] that's added to the given [ClusterResource]
     * when [ClusterResource.addListener] is called.
     * Mockito fails to do so when using an [org.mockito.ArgumentCaptor] and fails with an [IllegalArgumentException]
     * instead because the listener argument is not nullable.
     * This method works around this by using an [org.mockito.ArgumentMatcher] instead
     *
     * @param clusterResource to capture the [ModelChangeObservable.IResourceChangeListener] from that's added via [ClusterResource.addListener]
     */
    private fun captureClusterResourceListener(clusterResource: ClusterResource): ModelChangeObservable.IResourceChangeListener? {
        var listener: ModelChangeObservable.IResourceChangeListener? = null
        verify(clusterResource).addListener(argWhere {
                toAdd: ModelChangeObservable.IResourceChangeListener ->
                listener = toAdd
                true // let it succeed so that it can capture
        })
        return listener
    }

    private fun verifyHideAllNotifications() {
        verify(errorNotification).hide()
        verify(pullNotification).hide()
        verify(deletedNotification).hide()
        verify(pushNotification).hide()
        verify(pulledNotification).hide()
    }

    private fun verifyShowAllNotifications(mode: VerificationMode = times(1)) {
        verify(errorNotification, mode).show(any(), any<String>())
        verify(pullNotification, mode).show(any())
        verify(deletedNotification, mode).show(any())
        verify(pushNotification, mode).show()
        verify(pulledNotification, mode).show(any())
    }

    private fun clearAllNotificationInvocations() {
        clearInvocations(errorNotification)
        clearInvocations(pullNotification)
        clearInvocations(deletedNotification)
        clearInvocations(pushNotification)
        clearInvocations(pulledNotification)
    }

    private class TestableResourceEditor(
        localCopy: HasMetadata?,
        editor: FileEditor,
        project: Project,
        clients: Clients<out KubernetesClient>,
        resourceFactory: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata?,
        createClusterResource: (HasMetadata, Clients<out KubernetesClient>) -> ClusterResource,
        resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile?,
        resourceFileForResource: (resource: HasMetadata) -> ResourceFile?,
        pushNotification: PushNotification,
        pullNotification: PullNotification,
        pulledNotification: PulledNotification,
        deletedNotification: DeletedNotification,
        errorNotification: ErrorNotification,
        documentProvider: (FileEditor) -> Document?,
        psiDocumentManagerProvider: (Project) -> PsiDocumentManager,
        ideNotification: Notification
    ) : ResourceEditor(
        localCopy,
        editor,
        project,
        clients,
        resourceFactory,
        createClusterResource,
        resourceFileForVirtual,
        resourceFileForResource,
        pushNotification,
        pullNotification,
        pulledNotification,
        deletedNotification,
        errorNotification,
        documentProvider,
        psiDocumentManagerProvider,
        ideNotification
    ) {
        public override var editorResource: HasMetadata? = super.editorResource

        override fun executeWriteAction(runnable: () -> Unit) {
            // dont execute in application thread pool
            runnable.invoke()
        }
    }

}