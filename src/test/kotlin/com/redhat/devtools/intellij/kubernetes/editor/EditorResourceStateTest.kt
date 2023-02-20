/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.PodBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class EditorResourceStateTest {

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

    // need real resources, not mocks - #equals used to track changes
    private val GARGAMEL_WITH_LABEL = PodBuilder(GARGAMEL)
        .editMetadata()
            .withLabels<String, String>(mapOf(Pair("hat", "none")))
        .endMetadata()
        .build()

    @Test
    fun `FILTER_ALL should select all resources`() {
        // given
        val resources = createEditorResources(
            Pair(GARGAMEL, Modified(true, true)),
            Pair(GARGAMEL_WITH_LABEL, Identical())
        )

        // when
        val filtered = resources.filter(FILTER_ALL)
        // then
        assertThat(toResources(filtered)).containsExactly(
            GARGAMEL,
            GARGAMEL_WITH_LABEL
        )
    }

    @Test
    fun `FILTER_TO_PUSH should select modified resources`() {
        // given
        val resources = createEditorResources(
            Pair(GARGAMEL_WITH_LABEL, Identical()),
            Pair(GARGAMEL, Modified(true, true))
        )

        // when
        val filtered = resources.filter(FILTER_TO_PUSH)
        // then
        assertThat(toResources(filtered)).containsExactly(
            GARGAMEL
        )
    }

    @Test
    fun `FILTER_TO_PUSH should select deleted resources`() {
        // given
        val resources = createEditorResources(
            Pair(GARGAMEL_WITH_LABEL, Identical()),
            Pair(GARGAMEL, DeletedOnCluster())
        )

        // when
        val filtered = resources.filter(FILTER_TO_PUSH)
        // then
        assertThat(toResources(filtered)).containsExactly(
            GARGAMEL
        )
    }

    @Test
    fun `FILTER_TO_PUSH should NOT select outdated resources`() {
        // given
        val resources = createEditorResources(
            Pair(GARGAMEL_WITH_LABEL, Identical()),
            Pair(GARGAMEL, Outdated())
        )

        // when
        val filtered = resources.filter(FILTER_TO_PUSH)
        // then
        assertThat(toResources(filtered)).isEmpty()
    }

    private fun createEditorResources(vararg resourceAndStates: Pair<HasMetadata, EditorResourceState>): Collection<EditorResource> {
        return resourceAndStates.map { resourceAndState ->
            createEditorResource(resourceAndState.first, resourceAndState.second)
        }
    }

    private fun createEditorResource(resource: HasMetadata, state: EditorResourceState): EditorResource {
        return mock<EditorResource> {
            on { getResource() } doReturn resource
            on { getState() } doReturn state
        }
    }

    private fun toResources(editorResources: Collection<EditorResource>): Collection<HasMetadata> {
        return editorResources.map { editorResource -> editorResource.getResource() }
    }
}