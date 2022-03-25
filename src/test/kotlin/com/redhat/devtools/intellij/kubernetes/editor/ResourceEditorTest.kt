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
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.ClusterResource
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.model.util.ResettableLazyProperty
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResourceEditorTest {

    private val job: String = """
apiVersion: batch/v1
kind: Job
metadata:
  name: countdown
spec:
  template:
    metadata:
      name: countdown
    spec:
      containers:
      - name: counter
        image: centos:7
        command:
         - "bin/bash"
         - "-c"
         - "echo kube"
      restartPolicy: Never
"""
    private val allNamespaces = arrayOf(
        NAMESPACE1,
        NAMESPACE2,
        NAMESPACE3)
    private val currentNamespace = NAMESPACE2

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
            .withLabels<String, String>(mapOf(Pair("hat", "none")))
        .endMetadata()
        .build()

    // need real resources, not mocks - #equals used to track changes
    private val GARGAMELv2 = PodBuilder(GARGAMEL)
        .editMetadata()
            .withResourceVersion("2")
        .endMetadata()
        .build()
    private val AZRAEL = PodBuilder(GARGAMEL)
        .editMetadata()
            .withName("Azrael")
        .endMetadata()
        .build()
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
    private val project: Project = mock()
    private val clients: () -> Clients<KubernetesClient> = {
        Clients(client(currentNamespace.metadata.name, allNamespaces))
    }
    private val getCustomResourceDefinitions: (client: KubernetesClient) -> Collection<CustomResourceDefinition> =
        mock<(client: KubernetesClient) -> Collection<CustomResourceDefinition>>().apply {
        doReturn(emptyList<CustomResourceDefinition>())
            .whenever(this).invoke(any())
    }
    private val createResource: (editor: FileEditor, definitions: Collection<CustomResourceDefinition>?) -> HasMetadata? =
        mock<(editor: FileEditor, definitions: Collection<CustomResourceDefinition>?) -> HasMetadata?>().apply  {
            doReturn(GARGAMEL)
                .whenever(this).invoke(any(), any())
        }
    private val clusterResource: ClusterResource = mock {
        on { pull(any()) } doReturn GARGAMELv2
        on { isSameResource(any()) } doReturn true
    }
    private val createClusterResource: (HasMetadata, Clients<out KubernetesClient>, Collection<CustomResourceDefinition>?) -> ClusterResource =
        mock<(HasMetadata, Clients<out KubernetesClient>, Collection<CustomResourceDefinition>?) -> ClusterResource>().apply {
            doReturn(clusterResource)
                .whenever(this).invoke(any(), any(), any())
        }
    private val pushNotification: PushNotification = mock()
    private val pullNotification: PullNotification = mock()
    private val pulledNotification: PulledNotification = mock()
    private val deletedNotification: DeletedNotification = mock()
    private val errorNotification: ErrorNotification = mock()
    private val document: Document = mock<Document>().apply {
        doReturn(job)
            .whenever(this).getText()
    }
    private val getDocument: (FileEditor) -> Document? = { document }
    private val psiDocumentManager: PsiDocumentManager = mock()
    private val getPsiDocumentManager: (Project) -> PsiDocumentManager = { psiDocumentManager }
    private val kubernetesTypeInfo: KubernetesTypeInfo = kubernetesTypeInfo(GARGAMEL.kind, GARGAMEL.apiVersion)
    private val kubernetesResourceInfo: KubernetesResourceInfo =
        kubernetesResourceInfo(GARGAMEL.metadata.name, GARGAMEL.metadata.namespace, kubernetesTypeInfo)
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo = { file, project ->
        kubernetesResourceInfo
    }
    private val documentReplaced: AtomicBoolean = AtomicBoolean(false)
    private val resourceVersion: PersistentEditorValue = mock()

    private val editor =
        TestableResourceEditor(
            fileEditor,
            project,
            clients,
            getCustomResourceDefinitions,
            createResource,
            createClusterResource,
            createResourceFileForVirtual,
            pushNotification,
            pullNotification,
            pulledNotification,
            deletedNotification,
            errorNotification,
            getDocument,
            getPsiDocumentManager,
            getKubernetesResourceInfo,
            documentReplaced,
            resourceVersion
        )

    @Test
    fun `#isModified should return true if editor resource doesn't exist on cluster`() {
        // given
        doReturn(false)
            .whenever(clusterResource).exists()
        // when
        val modified = editor.isModified()
        // then
        assertThat(modified).isTrue()
    }

    @Test
    fun `#isModified should return false if editor resource exists on cluster`() {
        // given
        // lastPulledPushed is initialized with editorResource if resource exists on cluster
        doReturn(true)
            .whenever(clusterResource).exists()
        // when
        val modified = editor.isModified()
        // then
        assertThat(modified).isFalse()
    }

    @Test
    fun `#isModified should return true if editor resource is changed when compared to pushed resource`() {
        // given
        editor.editorResource.set(GARGAMEL_WITH_LABEL)
        editor.lastPushedPulled.set(GARGAMEL)
        // when
        val modified = editor.isModified()
        // then
        assertThat(modified).isTrue()
    }

    @Test
    fun `#isModified should return false after changed resource is pushed`() {
        // given
        editor.editorResource.set(GARGAMEL_WITH_LABEL)
        doReturn(GARGAMEL_WITH_LABEL)
            .whenever(createResource).invoke(any(), any()) // called after pushing
        editor.lastPushedPulled.set(GARGAMEL)
        assertThat(editor.isModified()).isTrue()
        // when
        editor.push()
        // then
        assertThat(editor.isModified()).isFalse()
    }

    @Test
    fun `#isModified should return false after changed resource is pulled`() {
        // given
        editor.editorResource.set(GARGAMEL_WITH_LABEL)
        editor.lastPushedPulled.set(GARGAMEL)
        assertThat(editor.isModified()).isTrue()
        // when
        editor.pull()
        // then
        assertThat(editor.isModified()).isFalse()
    }

    @Test
    fun `#update should trigger fetching crds`() {
        // given
        // when
        editor.update()
        // then
        verify(getCustomResourceDefinitions).invoke(any())
    }

    @Test
    fun `#update called 2x should only fetch crds 1x`() {
        // given
        // when
        editor.update()
        editor.update()
        // then
        verify(getCustomResourceDefinitions).invoke(any())
    }

    @Test
    fun `#update should hide all notifications when resource on cluster is deleted`() {
        // given
        givenClusterResourceIsDeleted(true)
        givenEditorResourceIsModified(false)
        givenEditorResourceIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when resource is modified`() {
        // given
        givenClusterResourceIsDeleted(false)
        givenEditorResourceIsModified(true)
        givenEditorResourceIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when resource is outdated`() {
        // given
        givenClusterResourceIsDeleted(false)
        givenEditorResourceIsModified(false)
        givenEditorResourceIsOutdated(true)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when editor resource is NOT deleted nor modified nor outdated`() {
        // given
        givenClusterResourceIsDeleted(false)
        givenEditorResourceIsModified(false)
        givenEditorResourceIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should show error notification and hide all notifications if creating editor resource throws ResourceException`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification).show(any(), argWhere<String> { it.contains("client error") })
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should show deleted notification if resource on cluster is deleted`() {
        // given
        givenClusterResourceIsDeleted(true)
        givenEditorResourceIsModified(false)
        givenEditorResourceIsOutdated(false)
        // when
        editor.update()
        // then
        verify(deletedNotification).show(any())
    }

    @Test
    fun `#update should show push notification if resource is modified`() {
        // given
        givenClusterResourceIsDeleted(false)
        givenEditorResourceIsModified(true)
        givenEditorResourceIsOutdated(false)
        // when
        editor.update()
        // then
        verify(pushNotification).show(any(), any())
    }

    @Test
    fun `#update should show pull notification if resource is outdated`() {
        // given
        givenClusterResourceIsDeleted(false)
        givenEditorResourceIsModified(false)
        givenEditorResourceIsOutdated(true)
        // when
        editor.update()
        // then
        verify(pullNotification).show(any(), any())
    }

    @Test
    fun `#update should save resource version of resource in editor`() {
        // given
        doReturn(GARGAMEL_WITH_LABEL)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(resourceVersion).set(GARGAMEL_WITH_LABEL.metadata.resourceVersion)
    }

    @Test
    fun `#update should NOT save resource version if resource in editor has no resource version`() {
        // given
        val resource = PodBuilder(GARGAMEL)
            .editMetadata()
                .withResourceVersion(null)
            .endMetadata()
            .build()
        doReturn(resource)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(resourceVersion, never()).set(any())
    }

    @Test
    fun `#update should NOT save resource version if resource in editor has smaller resource version than previously saved version`() {
        // given
        doReturn(GARGAMEL.metadata.resourceVersion)
            .whenever(resourceVersion).get()
        val smallerVersion = (GARGAMEL.metadata.resourceVersion.toInt() - 1).toString()
        val resource = PodBuilder(GARGAMEL)
            .editMetadata()
            .withResourceVersion(smallerVersion)
            .endMetadata()
            .build()
        doReturn(resource)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(resourceVersion, never()).set(any())
    }

    @Test
    fun `#update after a #pull should do nothing bcs it was triggered by #replaceDocument (replace triggers editor transaction listener and thus #update)`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).pull(any())
        editor.pull()
        clearAllNotificationInvocations()
        // when
        editor.update()
        // then
        verifyShowNoNotifications()
    }

    @Test
    fun `#update after change of resource should show push notification`() {
        // given
        givenEditorResourceIsModified(false)
        doReturn(AZRAEL)
            .whenever(createResource).invoke(any(), any())
        // when
        editor.update()
        // then
        verify(pushNotification).show(any(), any())
    }

    @Test
    fun `#pull should replace document`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).pull(any())
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
            .whenever(clusterResource).pull(any())
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
    fun `#pull should show error notification if pulling throws`() {
        // given
        doThrow(ResourceException::class)
            .whenever(clusterResource).pull()
        // when
        editor.pull()
        // then
        verify(errorNotification).show(any(), any<String>())
    }

    @Test
    fun `#pull should save resource version`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).pull()
        // when
        editor.pull()
        // then
        verify(resourceVersion).set(GARGAMELv2.metadata.resourceVersion)
    }

    @Test
    fun `#push should push resource to cluster`() {
        // given
        // when
        editor.push()
        // then
        verify(clusterResource).push(any())
    }

    @Test
    fun `#push should save version of resource that cluster responded with`() {
        // given
        val versionInResponse = GARGAMELv2.metadata.resourceVersion
        doReturn(GARGAMELv2)
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(resourceVersion).set(versionInResponse)
    }

    @Test
    fun `#push should show error notification if creating cluster resource throws`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createClusterResource).invoke(any(), any(), any())
        // when
        editor.push()
        // then
        verify(errorNotification).show(any(), argWhere<String> { it.contains("client error", false) })
    }

    @Test
    fun `#push should show error notification with message of exception if pushing to cluster throws ResourceException`() {
        // given
        doThrow(ResourceException("resource error"))
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(errorNotification).show(any(), argWhere<String> { it.contains("resource error") })
    }

    @Test
    fun `#push should show error notification with message of cause if pushing to cluster throws ResourceException`() {
        // given
        doThrow(ResourceException("client error", KubernetesClientException("client error")))
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(errorNotification).show(any(), argWhere<String> { it.contains("client error") })
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
    fun `#enableNonProjectFileEditing should NOT call #enableNonProjectFileEditing if editor has no kind`() {
        // given
        doReturn("apiGroup")
            .whenever(kubernetesTypeInfo).apiGroup
        doReturn(null)
            .whenever(kubernetesTypeInfo).kind
        // when
        editor.enableNonProjectFileEditing()
        // then
        verify(resourceFile, never()).enableNonProjectFileEditing()
    }

    @Test
    fun `#enableNonProjectFileEditing should NOT call #enableNonProjectFileEditing if editor has no apiGroup`() {
        // given
        doReturn(null)
            .whenever(kubernetesTypeInfo).apiGroup
        doReturn("kind")
            .whenever(kubernetesTypeInfo).kind
        // when
        editor.enableNonProjectFileEditing()
        // then
        verify(resourceFile, never()).enableNonProjectFileEditing()
    }

    @Test
    fun `#replaceContent should set text of document`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).pull(any())
        // when
        editor.pull()
        // then
        verify(document).replaceString(0, document.textLength - 1, Serialization.asYaml(GARGAMELv2))
    }

    @Test
    fun `#replaceContent should commit document`() {
        // given
        doReturn(GARGAMELv2)
            .whenever(clusterResource).pull(any())
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

    private fun verifyHideAllNotifications() {
        verify(errorNotification).hide()
        verify(pullNotification).hide()
        verify(deletedNotification).hide()
        verify(pushNotification).hide()
        verify(pulledNotification).hide()
    }

    private fun verifyShowNoNotifications() {
        verify(errorNotification, never()).show(any(), any<String>())
        verify(pullNotification, never()).show(any(), any())
        verify(deletedNotification, never()).show(any())
        verify(pushNotification, never()).show(any(), any())
        verify(pulledNotification, never()).show(any())
    }

    private fun clearAllNotificationInvocations() {
        clearInvocations(errorNotification)
        clearInvocations(pullNotification)
        clearInvocations(deletedNotification)
        clearInvocations(pushNotification)
        clearInvocations(pulledNotification)
    }

    @Test
    fun `#close should close cluster resource`() {
        // given
        // when
        editor.close()
        // then
        verify(clusterResource).close()
    }

    @Test
    fun `#close should delete temporary resource file`() {
        // given
        // when
        editor.close()
        // then
        verify(resourceFile).deleteTemporary()
    }

    @Test
    fun `#close should remove editor from virtual file user data`() {
        // given
        // when
        editor.close()
        // then
        verify(virtualFile).putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, null)
    }

    @Test
    fun `#close should remove editor user data`() {
        // given
        // when
        editor.close()
        // then
        verify(fileEditor).putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, null)
    }

    private fun givenClusterResourceIsDeleted(deleted: Boolean) {
        doReturn(deleted)
            .whenever(clusterResource).isDeleted()
    }

    private fun givenEditorResourceIsOutdated(outdated: Boolean) {
        doReturn(outdated)
            .whenever(clusterResource).isOutdated(any() as String?)
    }

    private fun givenEditorResourceIsModified(modified: Boolean) {
        val editorResource = if (modified) {
            GARGAMEL_WITH_LABEL
        } else {
            GARGAMEL
        }
        /**
         * Workaround: force [ResourceEditor.clusterResource] to be created
         * & reset [ResourceEditor.lastPushedPulled] so that no reset happens anymore.
         *
         * [ResourceEditor.lastPushedPulled.get] is causing initialization of [ResourceEditor.lastPushedPulled]
         * which is causing [ResourceEditor.clusterResource] to be created which then resets [ResourceEditor.lastPushedPulled]
         * */
        editor.lastPushedPulled.get() // WORKAROUND
        editor.lastPushedPulled.set(GARGAMEL)
        editor.editorResource.set(editorResource)
        doReturn(editorResource)
            .whenever(createResource).invoke(any(), any())
        doReturn(editorResource.metadata.resourceVersion)
            .whenever(resourceVersion).get()
    }

    private class TestableResourceEditor(
        editor: FileEditor,
        project: Project,
        clients: () -> Clients<out KubernetesClient>,
        getDefinitions: (client: KubernetesClient) -> Collection<CustomResourceDefinition>,
        resourceFactory: (editor: FileEditor, definitions: Collection<CustomResourceDefinition>?) -> HasMetadata?,
        createClusterResource: (HasMetadata, Clients<out KubernetesClient>, Collection<CustomResourceDefinition>?) -> ClusterResource,
        resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile?,
        pushNotification: PushNotification,
        pullNotification: PullNotification,
        pulledNotification: PulledNotification,
        deletedNotification: DeletedNotification,
        errorNotification: ErrorNotification,
        documentProvider: (FileEditor) -> Document?,
        psiDocumentManagerProvider: (Project) -> PsiDocumentManager,
        getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo,
        documentReplaced: AtomicBoolean,
        resourceVersion: PersistentEditorValue
    ) : ResourceEditor(
        editor,
        project,
        clients,
        getDefinitions,
        resourceFactory,
        createClusterResource,
        resourceFileForVirtual,
        pushNotification,
        pullNotification,
        pulledNotification,
        deletedNotification,
        errorNotification,
        documentProvider,
        psiDocumentManagerProvider,
        getKubernetesResourceInfo,
        documentReplaced,
        resourceVersion
    ) {
        public override var lastPushedPulled: ResettableLazyProperty<HasMetadata?> = super.lastPushedPulled

        public override fun isModified(): Boolean {
            return super.isModified()
        }

        override fun runAsync(runnable: () -> Unit) {
            // dont execute in UI thread
            runnable.invoke()
        }

        override fun runWriteCommand(runnable: () -> Unit) {
            // dont execute in application thread pool
            runnable.invoke()
        }

        override fun runInUI(runnable: () -> Unit) {
            // dont execute in application thread pool
            runnable.invoke()
        }
    }
}