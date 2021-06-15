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

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient

class ResourceEditorTest {

    private val localCopy = POD2
    private val fileEditor: FileEditor = mock()
    private val resourceFile: ResourceFile = mock()
    private val project: Project = mock()
    private val resourceFactory: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata? =
        { editor, clients -> localCopy }
    private val resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile? = { resourceFile }
    private val resourceFileForResource: (resource: HasMetadata) -> ResourceFile? = { resourceFile }
    //private val resourceEditor = spy(TestableResourceEditor(localCopy, fileEditor, ))

    private class TestableResourceEditor(
        localCopy: HasMetadata?,
        editor: FileEditor,
        project: Project,
        clients: Clients<out KubernetesClient>,
        resourceFactory: (editor: FileEditor, clients: Clients<out KubernetesClient>) -> HasMetadata?,
        resourceFileForVirtual: (file: VirtualFile?) -> ResourceFile?,
        resourceFileForResource: (resource: HasMetadata) -> ResourceFile?
    ) : ResourceEditor(
        localCopy,
        editor,
        project,
        clients,
        resourceFactory,
        resourceFileForVirtual,
        resourceFileForResource
    ) {

        public override fun showDeletedNotification(editor: FileEditor, resource: HasMetadata, project: Project) {
            super.showDeletedNotification(editor, resource, project)
        }

        public override fun showReloadNotification(editor: FileEditor, resource: HasMetadata, project: Project) {
            super.showReloadNotification(editor, resource, project)
        }
    }

}