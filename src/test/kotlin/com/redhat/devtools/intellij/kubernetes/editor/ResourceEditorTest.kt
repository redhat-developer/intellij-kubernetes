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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.notification.DeletedNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.ErrorNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PullNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PulledNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushNotification
import com.redhat.devtools.intellij.kubernetes.editor.notification.PushedNotification
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.YAMLFileType
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatcher

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
class ResourceEditorTest {

    private val NAMESPACE = "jedis"

    private val GARGAMEL_INITIAL = PodBuilder()
        .withNewMetadata()
            .withName("Gargamel")
            .withNamespace("CastleBelvedere")
            .withResourceVersion("1")
        .endMetadata()
        .withNewSpec()
            .addNewContainer()
                .withImage("thesmurfs")
                .withName("thesmurfs")
                .addNewPort()
                    .withContainerPort(8080)
                .endPort()
            .endContainer()
        .endSpec()
        .build()

    private val GARGAMEL_INITIAL_YAML = EditorResourceSerialization.serialize(listOf(GARGAMEL_INITIAL), YAMLFileType.YML)

    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL_MODIFIED = PodBuilder(GARGAMEL_INITIAL)
        .editMetadata()
            .withLabels<String, String>(mapOf(Pair("hat", "none")))
        .endMetadata()
        .build()

    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL_PUSHED_PULLED = PodBuilder(GARGAMEL_INITIAL)
        .editMetadata()
            .withResourceVersion("2")
        .endMetadata()
        .build()
    private val virtualFile: VirtualFile = mock {
        on { fileType } doReturn YAMLFileType.YML
    }
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
    private val context: IActiveContext<out HasMetadata, out KubernetesClient> = mock()
    private val resourceModel: IResourceModel = mock {
        on { getCurrentContext() } doReturn context
        on { getCurrentNamespace() } doReturn NAMESPACE
    }
    private val project: Project = mock()
    private val createResources: (string: String?, fileType: FileType?, currentNamespace: String?) -> List<HasMetadata> =
        mock<(string: String?, fileType: FileType?, currentNamespace: String?) -> List<HasMetadata>>().apply {
            doReturn(listOf(GARGAMEL_INITIAL))
                .whenever(this).invoke(any(), any(), any())
        }
    private val clusterResource: ClusterResource = mock {
        on { pull(any()) } doReturn GARGAMEL_PUSHED_PULLED
        on { push(any()) } doReturn GARGAMEL_PUSHED_PULLED
        on { isSameResource(any()) } doReturn true
    }
    private val attributes: EditorResourceAttributes = mock {
        on { getClusterResource(any()) } doReturn clusterResource
        on { getAllClusterResources() } doReturn listOf(clusterResource)
    }
    private val serialize: (resources: Collection<HasMetadata>, fileType: FileType?) -> String? =
        mock<(resources: Collection<HasMetadata>, fileType: FileType?) -> String?>().apply {
            doReturn(Serialization.asYaml(GARGAMEL_MODIFIED))
                .whenever(this).invoke(any(), any())
        }
    private val pushNotification: PushNotification = mock()
    private val pushedNotification: PushedNotification = mock()
    private val pullNotification: PullNotification = mock()
    private val pulledNotification: PulledNotification = mock()
    private val deletedNotification: DeletedNotification = mock()
    private val errorNotification: ErrorNotification = mock()
    private val document: Document = mock<Document>().apply {
        doReturn(GARGAMEL_INITIAL_YAML)
            .whenever(this).text
    }
    private val getDocument: (FileEditor) -> Document? = { document }
    // using a mock of PsiFile made tests fail with a NoClassDefFoundError on github
    // https://github.com/redhat-developer/intellij-kubernetes/pull/364#issuecomment-1087628732
    private val psiFile: PsiFile = spy(PsiUtilCore.NULL_PSI_FILE)
    private val psiDocumentManager: PsiDocumentManager = mock()
    private val getPsiDocumentManager: (Project) -> PsiDocumentManager = { psiDocumentManager }
    private val kubernetesTypeInfo: KubernetesTypeInfo = kubernetesTypeInfo(GARGAMEL_INITIAL.kind, GARGAMEL_INITIAL.apiVersion)
    private val kubernetesResourceInfo: KubernetesResourceInfo =
        kubernetesResourceInfo(GARGAMEL_INITIAL.metadata.name, GARGAMEL_INITIAL.metadata.namespace, kubernetesTypeInfo)
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo = { file, project ->
        kubernetesResourceInfo
    }
    private val documentReplaced: AtomicBoolean = AtomicBoolean(false)
    private val diff: ResourceDiff = mock()

    private val editor =
        TestableResourceEditor(
            fileEditor,
            resourceModel,
            project,
            createResources,
            serialize,
            createResourceFileForVirtual,
            pushNotification,
            pushedNotification,
            pullNotification,
            pulledNotification,
            deletedNotification,
            errorNotification,
            getDocument,
            getPsiDocumentManager,
            getKubernetesResourceInfo,
            documentReplaced,
            diff,
            attributes
        )

    @Before
    fun before() {
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        doReturn(psiFile)
            .whenever(psiDocumentManager).getPsiFile(any())
        editor.initEditorResources(document)
    }

    @Test
    fun `should add listener to resourceModel when created`() {
        // given
        // when
        // then
        verify(resourceModel).addListener(any())
    }

    @Test
    fun `#dispose should stop listening to resource model`() {
        // given
        // when
        editor.dispose()
        // then
        verify(resourceModel).removeListener(any())
    }

    @Test
    fun `#update should hide all notifications when resource on cluster is deleted`() {
        // given
        givenClusterIsDeleted(true)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when resource is modified`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(true)
        givenEditorIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should hide all notifications when resource is outdated`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(true)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }


    @Test
    fun `#update should hide all notifications when editor resource is NOT modified NOR outdated, NOR`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should show error notification and hide all notifications if creating editor resource throws ResourceException`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification).show(any(), argWhere<String> { it.contains("client error") })
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should show deleted notification if resource on cluster is deleted`() {
        // given
        givenClusterIsDeleted(true)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(false)
        // when
        editor.update()
        // then
        verify(deletedNotification).show(any())
    }

    @Test
    fun `#update should show push notification if resource is modified`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(true)
        givenEditorIsOutdated(false)
        // when
        editor.update()
        // then
        verify(pushNotification).show(any(), any())
    }

    @Test
    fun `#update should show error notification if resource has neither name nor generateName`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(true)
        givenEditorIsOutdated(false)
        doReturn(listOf(PodBuilder(GARGAMEL_INITIAL)
            .editMetadata()
                .withName(null)
                .withGenerateName(null)
            .endMetadata()
            .build()))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification).show(any(), anyOrNull() as String?)
    }

    @Test
    fun `#update should NOT show error notification if resource has name`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(true)
        givenEditorIsOutdated(false)
        doReturn(listOf(PodBuilder(GARGAMEL_INITIAL)
            .editMetadata()
            .withName("gargantuan")
            .withGenerateName(null)
            .endMetadata()
            .build()))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification, never()).show(any(), anyOrNull() as String?)
    }

    @Test
    fun `#update should NOT show error notification if resource has generateName`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(true)
        givenEditorIsOutdated(false)
        doReturn(listOf(PodBuilder(GARGAMEL_INITIAL)
            .editMetadata()
            .withName(null)
            .withGenerateName("gargantuan")
            .endMetadata()
            .build()))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(errorNotification, never()).show(any(), anyOrNull() as String?)
    }

    @Test
    fun `#update should show pull notification if resource is outdated`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(true)
        // when
        editor.update()
        // then
        verify(pullNotification).show(any())
    }

    @Test
    fun `#update should show push notification when there are several resources and one is modified`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(true)
        givenEditorIsOutdated(false)
        doReturn(listOf(GARGAMEL_INITIAL, GARGAMEL_MODIFIED))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(pushNotification).show(any(), argThat(ArgumentMatcher { states ->
            states.size == 1
                    && states.first().resource == GARGAMEL_MODIFIED
        }))
    }

    @Test
    fun `#update should show push notification when there are several deleted resources`() {
        // given
        givenClusterIsDeleted(true)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(false)
        doReturn(listOf(GARGAMEL_INITIAL, GARGAMEL_MODIFIED))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(pushNotification).show(any(), argThat(ArgumentMatcher { states ->
            states.size == 2
                    && states.map { it.resource }
                        .containsAll(listOf(GARGAMEL_INITIAL, GARGAMEL_MODIFIED))
        }))
    }

    @Test
    fun `#update should NOT show push notification when there are several outdated resources`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(true)
        doReturn(listOf(GARGAMEL_INITIAL, GARGAMEL_PUSHED_PULLED))
            .whenever(createResources).invoke(any(), any(), any())
        // when
        editor.update()
        // then
        verify(pushNotification, never()).show(any(), any())
    }

    @Test
    fun `#update should hide all notifications if resource is identical`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(false)
        // when
        editor.update()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#update should do nothing when called after #pull (bcs it was triggered by #replaceDocument)`() {
        // given
        givenClusterIsDeleted(false)
        givenClusterExists(true)
        givenEditorIsModified(false)
        givenEditorIsOutdated(true)
        editor.initEditorResources(document)
        editor.pull()
        clearInvocations(pulledNotification) // ignore pulled notification triggered by #pull
        // when
        editor.update()
        // then
        verifyShowNoNotifications()
    }

    @Test
    fun `#pull should replace document`() {
        // given
        // when
        editor.pull()
        // then
        verify(document).replaceString(0, document.textLength, Serialization.asYaml(GARGAMEL_PUSHED_PULLED))
    }

    @Test
    fun `#pull should NOT replace document if pulled document is same`() {
        // given
        val existing = editor.editorResources
        reset(serialize)
        doReturn(EditorResourceSerialization.serialize(existing, YAMLFileType.YML))
            .whenever(serialize).invoke(any(), any())
        // when
        editor.pull()
        // then
        verify(document, never()).replaceString(any(), any(), any())
    }

    @Test
    fun `#pull should NOT replace document if document differs by a newline`() {
        // given
        val existing = editor.editorResources
        reset(serialize)
        val yaml = EditorResourceSerialization.serialize(existing, YAMLFileType.YML)
        doReturn(yaml)
            .whenever(serialize).invoke(any(), any())
        doReturn(yaml + "\n")
            .whenever(document).text
        // when
        editor.pull()
        // then
        verify(document, never()).replaceString(any(), any(), any())
    }

    @Test
    fun `#pull should deserialize to json if psiFile is json`() {
        // given
        doReturn(JsonFileType.INSTANCE)
            .whenever(psiFile).fileType
        // when
        editor.pull()
        // then
        verify(serialize).invoke(any(), eq(JsonFileType.INSTANCE))
    }

    @Test
    fun `#pull should deserialize to json if psiFile is yaml`() {
        // given
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        // when
        editor.pull()
        // then
        verify(serialize).invoke(any(), eq(YAMLFileType.YML))
    }

    @Test
    fun `#pull should commit document`() {
        // given
        // then
        // when
        editor.pull()
        // then
        verify(psiDocumentManager).commitDocument(document)
    }

    @Test
    fun `#pull should show pulled notification`() {
        // given
        // when
        editor.pull()
        // then
        verify(pulledNotification).show(GARGAMEL_PUSHED_PULLED)
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
        doReturn(GARGAMEL_PUSHED_PULLED)
            .whenever(clusterResource).pull()
        // when
        editor.pull()
        // then
        verify(attributes).setResourceVersion(
            GARGAMEL_PUSHED_PULLED,
            GARGAMEL_PUSHED_PULLED.metadata.resourceVersion)
    }

    @Test
    fun `#push should push resource if editor resource is modified`() {
        // given
        givenClusterExists(false)
        givenEditorIsModified(true)
        // when
        editor.push()
        // then
        verify(clusterResource).push(GARGAMEL_INITIAL)
    }

    @Test
    fun `#push should show pushed notification`() {
        // given
        givenClusterExists(false)
        givenEditorIsModified(true)
        // when
        editor.push()
        // then
        verify(pushedNotification).show(any())
    }

    @Test
    fun `#push should push resource if editor resource is NOT modified but does not exist on cluster`() {
        // given
        givenClusterExists(false)
        givenEditorIsModified(false)
        // when
        editor.push()
        // then
        verify(clusterResource).push(GARGAMEL_INITIAL)
    }

    @Test
    fun `#push should NOT push resource if editor resource is NOT modified and exists on cluster`() {
        // given
        givenClusterExists(true)
        givenEditorIsModified(false)
        // when
        editor.push()
        // then
        verify(clusterResource, never()).push(any())
    }

    @Test
    fun `#push should save version of resource that cluster responded with`() {
        // given
        givenClusterExists(false)
        givenEditorIsModified(true)
        // when
        editor.push()
        // then
        verify(attributes).setResourceVersion(any(), eq(GARGAMEL_PUSHED_PULLED.metadata.resourceVersion))
    }

    @Test
    fun `#push should save editor resource that was pushed`() {
        // given
        givenClusterExists(false)
        givenEditorIsModified(true)
        // when
        editor.push()
        // then save local resource that was pushed
        verify(attributes).setLastPushedPulled(GARGAMEL_INITIAL)
    }

    @Test
    fun `#push should show error notification with message of cause if pushing to cluster throws ResourceException`() {
        // given
        doThrow(ResourceException("didnt work", RuntimeException("resource error")))
            .whenever(clusterResource).push(any())
        // when
        editor.push()
        // then
        verify(errorNotification).show(any(), argWhere<String> { it.contains("resource error") })
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
    fun `#diff should serialize cluster resource to json if editor is json`() {
        // given
        doReturn(JsonFileType.INSTANCE)
            .whenever(psiFile).fileType
        // when
        editor.diff()
        // then
        verify(serialize).invoke(any(), eq(JsonFileType.INSTANCE))
    }

    @Test
    fun `#diff should serialize cluster resource to yml if editor is yml`() {
        // given
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        // when
        editor.diff()
        // then
        verify(serialize).invoke(any(), eq(YAMLFileType.YML))
    }

    @Test
    fun `#diff should NOT open diff if editor file type is null`() {
        // given
        doReturn(null)
            .whenever(psiFile).fileType
        // when
        editor.diff()
        // then
        verify(diff, never()).open(any(), any(), any())
    }

    @Test
    fun `#onDiffClosed should update resource attributes if document has changed`() {
        // given
        doReturn("{ apiVersion: v1 }")
            .whenever(document).text
        // when
        editor.onDiffClosed("{ apiVersion: v2 }")
        // then
        verify(attributes).update(any())
    }

    @Test
    fun `#onDiffClosed should NOT update resource attributes if document has NOT changed`() {
        // given
        doReturn("{ apiVersion: v1 }")
            .whenever(document).text
        // when
        editor.onDiffClosed("{ apiVersion: v1 }")
        // then
        verify(attributes, never()).update(any())
    }

    @Test
    fun `#removeClutter should replace document`() {
        // given
        // when
        editor.removeClutter()
        // then
        verify(document).replaceString(0, document.textLength, Serialization.asYaml(GARGAMEL_INITIAL))
    }

    @Test
    fun `#removeClutter should hide all notifications`() {
        // given
        // when
        editor.removeClutter()
        // then
        verifyHideAllNotifications()
    }

    @Test
    fun `#isEditing should return true if is given resource is same as editor resource`() {
        // given
        val resource = editor.editorResources.first()
        // when
        val isEditing = editor.isEditing(resource)
        // then
        assertThat(isEditing).isTrue
    }

   @Test
   fun `#isEditing should return false if is given resource has different name`() {
        // given
        val resource = PodBuilder(editor.editorResources.first() as Pod)
            .editOrNewMetadata()
                .withName("azrael")
            .endMetadata()
            .build()
        // when
        val isEditing = editor.isEditing(resource)
        // then
        assertThat(isEditing).isFalse
    }

    @Test
    fun `#isEditing should return false if is given resource has different namespace`() {
        // given
        val resource = PodBuilder(editor.editorResources.first() as Pod)
            .editOrNewMetadata()
                .withNamespace("smurf village")
            .endMetadata()
            .build()
        // when
        val isEditing = editor.isEditing(resource)
        // then
        assertThat(isEditing).isFalse
    }

    @Test
    fun `#isEditing should return false if is given resource has different apiVersion`() {
        // given
        val resource = PodBuilder(editor.editorResources.first() as Pod)
            .withApiVersion("purple smurf")
            .build()
        // when
        val isEditing = editor.isEditing(resource)
        // then
        assertThat(isEditing).isFalse
    }

    @Test
    fun `#isEditing should return false if is given resource is NOT same as editor resource`() {
        // given
        // when
        val isEditing = editor.isEditing(mock())
        // then
        assertThat(isEditing).isFalse
    }

    @Test
    fun `#close should dispose attributes`() {
        // given
        // when
        editor.close()
        // then
        verify(attributes).dispose()
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

    @Test
    fun `#close should remove listener from resourceModel`() {
        // given
        // when
        editor.close()
        // then
        verify(resourceModel).removeListener(any())
    }

    private fun verifyHideAllNotifications() {
        verify(errorNotification).hide()
        verify(pullNotification).hide()
        verify(pulledNotification).hide()
        verify(deletedNotification).hide()
        verify(pushNotification).hide()
        verify(pushedNotification).hide()
    }

    private fun verifyShowNoNotifications() {
        verify(errorNotification, never()).show(any(), any<String>())
        verify(pullNotification, never()).show(any())
        verify(deletedNotification, never()).show(any())
        verify(pushNotification, never()).show(any(), any())
        verify(pulledNotification, never()).show(any())
    }

    private fun givenClusterIsDeleted(deleted: Boolean) {
        doReturn(deleted)
            .whenever(clusterResource).isDeleted()
    }

    private fun givenClusterExists(exists: Boolean) {
        doReturn(exists)
            .whenever(clusterResource).exists()
    }

    private fun givenEditorIsModified(modified: Boolean) {
        /**
         * toggles [ResourceEditor.isModified]
         */
        val editorResource = if (modified) {
            GARGAMEL_MODIFIED
        } else {
            GARGAMEL_INITIAL
        }
        doReturn(listOf(editorResource))
            .whenever(createResources).invoke(any(), any(), any())

        val pulledResource = GARGAMEL_INITIAL
        doReturn(pulledResource)
            .whenever(attributes).getLastPulledPushed(any())
    }

    private fun givenEditorIsOutdated(outdated: Boolean) {
        doReturn(outdated)
            .whenever(clusterResource).isOutdatedVersion(anyOrNull())
    }

}

private class TestableResourceEditor(
    editor: FileEditor,
    resourceModel: IResourceModel,
    project: Project,
    createResources: (string: String?, fileType: FileType?, currentNamespace: String?) -> List<HasMetadata>,
    serialize: (resources: Collection<HasMetadata>, fileType: FileType?) -> String?,
    resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile?,
    pushNotification: PushNotification,
    pushedNotification: PushedNotification,
    pullNotification: PullNotification,
    pulledNotification: PulledNotification,
    deletedNotification: DeletedNotification,
    errorNotification: ErrorNotification,
    documentProvider: (FileEditor) -> Document?,
    psiDocumentManagerProvider: (Project) -> PsiDocumentManager,
    getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo,
    documentReplaced: AtomicBoolean,
    diff: ResourceDiff,
    attributes: EditorResourceAttributes
) : ResourceEditor(
    editor,
    resourceModel,
    project,
    createResources,
    serialize,
    resourceFileForVirtual,
    pushNotification,
    pushedNotification,
    pullNotification,
    pulledNotification,
    deletedNotification,
    errorNotification,
    documentProvider,
    psiDocumentManagerProvider,
    getKubernetesResourceInfo,
    documentReplaced,
    diff,
    attributes
) {

    fun initEditorResources(document: Document) {
        super.createEditorResources(document)
    }

    public override fun onDiffClosed(documentBeforeDiff: String?) {
        // allow public visibility
        return super.onDiffClosed(documentBeforeDiff)
    }

    override fun runAsync(runnable: () -> Unit) {
        // don't execute in application thread pool
        runnable.invoke()
    }

    override fun runWriteCommand(runnable: () -> Unit) {
        // don't execute in IDE write context, which doesn't exist in unit test
        runnable.invoke()
    }

    override fun <R : Any> runReadCommand(runnable: () -> R?): R? {
        // don't execute in IDE read context, which doesn't exist in unit test
        return runnable.invoke()
    }

    override fun runInUI(runnable: () -> Unit) {
        // don't execute UI thread
        runnable.invoke()
    }
}