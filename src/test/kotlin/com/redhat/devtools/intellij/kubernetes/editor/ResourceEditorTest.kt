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
import com.intellij.util.messages.MessageBusConnection
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.notification.Notifications
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.settings.Settings
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.Serialization
import java.util.concurrent.CompletionException
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.YAMLFileType
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.eq

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
class ResourceEditorTest {

    private val NAMESPACE = "jedis"

    private val GARGAMEL = PodBuilder()
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

    private val GARGAMEL_YAML = EditorResourceSerialization.serialize(listOf(GARGAMEL), YAMLFileType.YML)

    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL_WITH_LABEL = PodBuilder(GARGAMEL)
        .editMetadata()
            .withLabels<String, String>(mapOf(Pair("hat", "none")))
        .endMetadata()
        .build()

    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL_V2 = PodBuilder(GARGAMEL)
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
        mock<(string: String?, fileType: FileType?, currentNamespace: String?) -> List<HasMetadata>>()
    private val editorResources: EditorResources = mock()
    private val serialize: (resources: Collection<HasMetadata>, fileType: FileType?) -> String? =
        mock<(resources: Collection<HasMetadata>, fileType: FileType?) -> String?>().apply {
            doAnswer { invocation ->
                val resources = invocation.getArgument<Collection<HasMetadata>>(0)
                EditorResourceSerialization.serialize(resources, YAMLFileType.YML)
            }.whenever(this).invoke(any(), any())
        }
    private val notifications: Notifications = mock()
    private val document: Document = mock<Document>()
    private val getDocument: (FileEditor) -> Document? = { document }
    // using a mock of PsiFile made tests fail with a NoClassDefFoundError on github
    // https://github.com/redhat-developer/intellij-kubernetes/pull/364#issuecomment-1087628732
    private val psiFile: PsiFile = spy(PsiUtilCore.NULL_PSI_FILE)
    private val psiDocumentManager: PsiDocumentManager = mock()
    private val getPsiDocumentManager: (Project) -> PsiDocumentManager = { psiDocumentManager }
    private val kubernetesTypeInfo: KubernetesTypeInfo = kubernetesTypeInfo(GARGAMEL.kind, GARGAMEL.apiVersion)
    private val kubernetesResourceInfo: KubernetesResourceInfo =
        kubernetesResourceInfo(GARGAMEL.metadata.name, GARGAMEL.metadata.namespace, kubernetesTypeInfo)
    private val getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo = { file, project ->
        kubernetesResourceInfo
    }
    private val diff: ResourceDiff = mock()
    private val settings: Settings = mock {
        on { isEditorSyncEnabled() } doReturn true
    }
    private val connection: MessageBusConnection = mock()
    private val editor =
        TestableResourceEditor(
            fileEditor,
            resourceModel,
            project,
            createResources,
            serialize,
            createResourceFileForVirtual,
            notifications,
            getDocument,
            getPsiDocumentManager,
            getKubernetesResourceInfo,
            diff,
            editorResources,
            settings,
            connection
        )

    @Before
    fun before() {
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        doReturn(psiFile)
            .whenever(psiDocumentManager).getPsiFile(any())
    }

    @Test
    fun `#constructor should add listener to resourceModel`() {
        // given
        // when
        // then
        verify(resourceModel).addListener(any())
    }

    @Test
    fun `#constructor should enable editing for non-project files`() {
        // given
        // when
        // then
        verify(resourceFile).enableEditingNonProjectFile()
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
    fun `#dispose should dispose all editor resources`() {
        // given
        // when
        editor.dispose()
        // then
        verify(editorResources).dispose()
    }

    @Test
    fun `#dispose should dispose notifications`() {
        // given
        // when
        editor.dispose()
        // then
        verify(connection).dispose()
    }

    @Test
    fun `#update should show error notification and hide all notifications if creating editor resource throws ResourceException`() {
        // given
        doThrow(ResourceException("resource error", KubernetesClientException("client error")))
            .whenever(createResources).invoke(anyOrNull(), anyOrNull(), anyOrNull())
        // when
        editor.update()
        // then
        verify(notifications).showError(any(), argWhere<String> { it.contains("client error") })
        verify(notifications).hideAll()
    }

    @Test
    fun `#updateDeleted(removed) should set editorResource to deleted`() {
        // given
        givenResources(mapOf(GARGAMEL to Identical()))
        // when
        editor.updateDeleted(GARGAMEL)
        // then
        verify(editorResources).setDeleted(GARGAMEL)
    }

    @Test
    fun `#pull should NOT pull if there are several resources`() {
        // given
        givenResources(mapOf(
            GARGAMEL to NopEditorResourceState(),
            GARGAMEL_WITH_LABEL to NopEditorResourceState())) //
        // when
        editor.pull()
        // then
        verify(editorResources, never()).pull(any())
    }

    @Test
    fun `#pull should pull single resource that exists`() {
        // given
        doReturn(GARGAMEL_YAML)
            .whenever(document).text
        givenResources(mapOf(
            GARGAMEL to NopEditorResourceState())) //
        // when
        editor.pull()
        // then
        verify(editorResources).pull(GARGAMEL)
    }


    @Test
    fun `#pull should replace document if it is modified`() {
        // given
        doReturn(GARGAMEL_YAML)
            .whenever(document).text
        givenResources(mapOf(GARGAMEL_WITH_LABEL to NopEditorResourceState())) //
        // when
        editor.pull()
        // then
        verify(document).replaceString(
            0,
            document.textLength,
            EditorResourceSerialization.serialize(listOf(GARGAMEL_WITH_LABEL), YAMLFileType.YML)!!
        )
    }

    @Test
    fun `#pull should NOT replace document if pulled document is same`() {
        doReturn(GARGAMEL_YAML)
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        // when
        editor.pull()
        // then
        verify(document, never()).replaceString(any(), any(), any())
    }

    @Test
    fun `#pull should NOT replace document if document differs by a newline`() {
        // given
        doReturn(GARGAMEL_YAML + "\n")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        // when
        editor.pull()
        // then
        verify(document, never()).replaceString(any(), any(), any())
    }

    @Test
    fun `#pull should serialize to json if psiFile is json`() {
        // given
        doReturn("")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to object : EditorResourceState() {}))
        doReturn(JsonFileType.INSTANCE)
            .whenever(psiFile).fileType
        // when
        editor.pull()
        // then
        verify(serialize).invoke(any(), eq(JsonFileType.INSTANCE))
    }

    @Test
    fun `#pull should serialize to yaml if psiFile is yaml`() {
        // given
        doReturn("")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to object : EditorResourceState() {}))
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
        doReturn("")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to object : EditorResourceState() {}))
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        // then
        // when
        editor.pull()
        // then
        verify(psiDocumentManager).commitDocument(document)
    }

    @Test
    fun `#pull should hide all notifications`() {
        // given
        doReturn("")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to object : EditorResourceState() {}))
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        // then
        // when
        editor.pull()
        // then
        verify(notifications).hideAll()
    }

    @Test
    fun `#push should hide all notifications`() {
        // given
        givenResources(mapOf(GARGAMEL to object : EditorResourceState() {}))
        // when
        editor.push(true)
        // then
        // hides 1) before pushing, 2) after pushing by calling #update
        verify(notifications, times(2)).hideAll()
    }

    @Test
    fun `#push_not_all should push modified resources`() {
        // given
        givenResources(mapOf(
            GARGAMEL to object : EditorResourceState() {},
            GARGAMEL_WITH_LABEL to object : EditorResourceState() {}))
        // when
        editor.push(false)
        // then
        verify(editorResources).pushAll(FILTER_TO_PUSH)
    }

    @Test
    fun `#push_all should push all resources`() {
        // given
        givenResources(mapOf(
            GARGAMEL to Identical(),
            GARGAMEL_WITH_LABEL to DeletedOnCluster()))
        // when
        editor.push(true)
        // then
        verify(editorResources).pushAll(FILTER_ALL)
    }

    @Test
    fun `#enableEditingNonProjectFile should NOT call #enableNonProjectFileEditing if editor file is null`() {
        // given
        doReturn(null)
            .whenever(fileEditor).file
        clearInvocations(resourceFile) // don't count invocation in constructor
        // when
        editor.enableEditingNonProjectFile()
        // then
        verify(resourceFile, never()).enableEditingNonProjectFile()
    }

    @Test
    fun `#enableEditingNonProjectFile should NOT call #enableNonProjectFileEditing if editor has no kind`() {
        // given
        doReturn("apiGroup")
            .whenever(kubernetesTypeInfo).apiGroup
        doReturn(null)
            .whenever(kubernetesTypeInfo).kind
        clearInvocations(resourceFile) // don't count invocation in constructor
        // when
        editor.enableEditingNonProjectFile()
        // then
        verify(resourceFile, never()).enableEditingNonProjectFile()
    }

    @Test
    fun `#enableEditingNonProjectFile should NOT call #enableNonProjectFileEditing if editor has no apiGroup`() {
        // given
        doReturn(null)
            .whenever(kubernetesTypeInfo).apiGroup
        doReturn("kind")
            .whenever(kubernetesTypeInfo).kind
        clearInvocations(resourceFile) // don't count invocation in constructor
        // when
        editor.enableEditingNonProjectFile()
        // then
        verify(resourceFile, never()).enableEditingNonProjectFile()
    }

    @Test
    fun `#startWatch should start watching all editor resources`() {
        // given
        // when
        editor.startWatch()
        // then
        verify(editorResources).watchAll()
    }

    @Test
    fun `#startWatch should NOT start watching all editor resources if editorSync is NOT Enabled`() {
        // given
        whenever(settings.isEditorSyncEnabled())
            .thenReturn(false)
        // when
        editor.startWatch()
        // then
        verify(editorResources, never()).watchAll()
    }

    @Test
    fun `#stopWatch should stop watching all editor resources`() {
        // given
        // when
        editor.stopWatch()
        // then
        verify(editorResources).stopWatchAll()
    }

    @Test
    fun `#diff should serialize cluster resource to json if editor is json`() {
        // given
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        doReturn(GARGAMEL_YAML)
            .whenever(document).text
        doReturn(JsonFileType.INSTANCE)
            .whenever(psiFile).fileType
        // when
        editor.diff().join()
        // then
        verify(serialize).invoke(any(), eq(JsonFileType.INSTANCE))
    }

    @Test
    fun `#diff should serialize cluster resource to yml if editor is yml`() {
        // given
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        doReturn(GARGAMEL_YAML)
            .whenever(document).text
        doReturn(YAMLFileType.YML)
            .whenever(psiFile).fileType
        // when
        editor.diff().join()
        // then
        verify(serialize).invoke(any(), eq(YAMLFileType.YML))
    }

    @Test(expected = CompletionException::class)
    fun `#diff should throw if editor file type is null`() {
        // given
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        doReturn(GARGAMEL_YAML)
            .whenever(document).text
        doReturn(null)
            .whenever(psiFile).fileType
        // when
        editor.diff().join()
        // then
    }

    @Test
    fun `#onDiffClosed should update resource attributes if document has changed`() {
        // given
        doReturn("{ apiVersion: v1 }")
            .whenever(document).text
        // when
        editor.onDiffClosed("{ apiVersion: v2 }")
        // then
        verify(editorResources).setResources(anyOrNull())
    }

    @Test
    fun `#onDiffClosed should NOT update resource attributes if document has NOT changed`() {
        // given
        doReturn("{ apiVersion: v1 }")
            .whenever(document).text
        // when
        editor.onDiffClosed("{ apiVersion: v1 }")
        // then
        verify(editorResources, never()).setResources(anyOrNull())
    }

    @Test
    fun `#removeClutter should replace document`() {
        // given
        doReturn("")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        // when
        editor.removeClutter()
        // then
        verify(document).replaceString(0, document.textLength, Serialization.asYaml(GARGAMEL))
    }

    @Test
    fun `#removeClutter should hide all notifications`() {
        // given
        doReturn("")
            .whenever(document).text
        givenResources(mapOf(GARGAMEL to NopEditorResourceState()))
        // when
        editor.removeClutter()
        // then
        verify(notifications).hideAll()
    }
    @Test
    fun `#isEditing should return true if there is an EditorResource with the given resource`() {
        // given
        val resource = GARGAMEL
        doReturn(true)
            .whenever(editorResources).hasResource(resource)
        // when
        val isEditing = editor.isEditing(resource)
        // then
        assertThat(isEditing).isTrue
    }

    @Test
    fun `#isEditing should return false if there is no EditorResource with the given resource`() {
        // given
        val resource = GARGAMEL
        doReturn(true)
            .whenever(editorResources).hasResource(resource)
        // when
        val isEditing = editor.isEditing(mock())
        // then
        assertThat(isEditing).isFalse
    }

    @Test
    fun `#close should remove listener from resourceModel`() {
        // given
        // when
        editor.close()
        // then
        verify(resourceModel).removeListener(any())
    }

    @Test
    fun `#close should dispose editor resources`() {
        // given
        // when
        editor.close()
        // then
        verify(editorResources).dispose()
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

    private fun givenResources(editorResourceByResource: Map<HasMetadata, EditorResourceState>) {
        val resources = editorResourceByResource.keys.toList()
        doReturn(resources)
            .whenever(createResources).invoke(anyOrNull(), anyOrNull(), anyOrNull())
        doReturn(resources)
            .whenever(editorResources).getAllResources()
        val allEditorResources = editorResourceByResource.map { entry: Map.Entry<HasMetadata, EditorResourceState> ->
            val resource = entry.key
            val state = entry.value
            mock<EditorResource> {
                on { getResource() } doReturn resource
                on { getState() } doReturn state
            }
        }
        doReturn(allEditorResources)
            .whenever(editorResources).setResources(any())
    }

    private class TestableResourceEditor(
        editor: FileEditor,
        resourceModel: IResourceModel,
        project: Project,
        createResources: (string: String?, fileType: FileType?, currentNamespace: String?) -> List<HasMetadata>,
        serialize: (resources: Collection<HasMetadata>, fileType: FileType?) -> String?,
        resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile?,
        notifications: Notifications,
        documentProvider: (FileEditor) -> Document?,
        psiDocumentManagerProvider: (Project) -> PsiDocumentManager,
        getKubernetesResourceInfo: (VirtualFile?, Project) -> KubernetesResourceInfo,
        diff: ResourceDiff,
        editorResources: EditorResources,
        settings: Settings,
        connection: MessageBusConnection
    ) : ResourceEditor(
        editor,
        resourceModel,
        project,
        createResources,
        serialize,
        resourceFileForVirtual,
        notifications,
        documentProvider,
        psiDocumentManagerProvider,
        getKubernetesResourceInfo,
        diff,
        editorResources,
        settings,
        connection
    ) {

        public override fun updateDeleted(deleted: HasMetadata?) {
            super.updateDeleted(deleted)
        }

        public override fun enableEditingNonProjectFile() {
            super.enableEditingNonProjectFile()
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

    class NopEditorResourceState: EditorResourceState()
}


