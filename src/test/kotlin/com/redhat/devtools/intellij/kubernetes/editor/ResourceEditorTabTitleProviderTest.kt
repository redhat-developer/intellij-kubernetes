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

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.*
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorTabTitleProvider.Companion.TITLE_UNKNOWN_NAME
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResourceEditorTabTitleProviderTest {

    private val name = "LuckyLuke"
    private val namespace = "WildWest"
    private val kind = "Cowboy"
    private val apiGroup = "sillyLads/v1"

    @Test
    fun `#getTitle should return 'resourcename@namespace' if file is temporary and contains kubernetes resource with namespace`() {
        // given
        val isTemporary = true
        val resourceInfo = kubernetesResourceInfo(name, namespace, kubernetesTypeInfo(kind, apiGroup))
        val provider = createResourceEditorTabTitleProvider(isTemporary, resourceInfo)
        // when
        val title = provider.getEditorTabTitle(mock(), mock())
        // then
        assertThat(title).isEqualTo("$name@$namespace")
    }

    @Test
    fun `#getTitle should return 'name' if local file is temporary and has kubernetes resource without namespace - ex Namespace, Project`() {
        // given
        val name = "bogus name"
        val namespace = null
        val isTemporary = true
        val resourceInfo = kubernetesResourceInfo(name, namespace, kubernetesTypeInfo(kind, apiGroup))
        val provider = createResourceEditorTabTitleProvider(isTemporary, resourceInfo)
        // when
        val title = provider.getEditorTabTitle(mock(), mock())
        // then
        assertThat(title).isEqualTo(name)
    }

    @Test
    fun `#getTitle should return 'unknown@namespace' if local file is temporary, has kubernetes resource but name not recognized`() {
        // given
        val name = null
        val namespace = "bogus namespace"
        val isTemporary = true
        val resourceInfo = kubernetesResourceInfo(name, namespace, kubernetesTypeInfo(kind, apiGroup))
        val provider = createResourceEditorTabTitleProvider(isTemporary, resourceInfo)
        // when
        val title = provider.getEditorTabTitle(mock(), mock())
        // then
        assertThat(title).isEqualTo("$TITLE_UNKNOWN_NAME@$namespace")
    }

    @Test
    fun `#getTitle should ask fallback provider for title if file is NOT temporary`() {
        // given
        val isTemporary = false
        val file: VirtualFile = mock()
        val resourceInfo = kubernetesResourceInfo(
            "<none>",
            "<none>",
            kubernetesTypeInfo("<none>", "<none>"))
        val fallback: EditorTabTitleProvider = mock()
        val provider = createResourceEditorTabTitleProvider(isTemporary, resourceInfo, fallback)
        // when
        provider.getEditorTabTitle(mock(), file)
        // then
        verify(fallback).getEditorTabTitle(any(), eq(file))
    }

    private fun createResourceEditorTabTitleProvider(
        isTemporary: Boolean,
        info: KubernetesResourceInfo,
        fallback: EditorTabTitleProvider = mock()
    ): ResourceEditorTabTitleProvider {
        return object : ResourceEditorTabTitleProvider(fallback) {

            override fun getKubernetesResourceInfo(file: VirtualFile, project: Project): KubernetesResourceInfo {
                return info
            }

            override fun isTemporary(file: VirtualFile): Boolean {
                return isTemporary
            }
        }
    }
}