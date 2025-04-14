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

    @Test
    fun `Error is not equal to all the other states`() {
        // given
        // when
        assertThat(Error("yoda", "is a jedi")).isEqualTo(Error("yoda", "is a jedi"))
        assertThat(Error("yoda", "is a green gnome")).isNotEqualTo(Error("yoda", "is a jedi"))
        assertThat(Error("obiwan", "is a jedi")).isNotEqualTo(Error("yoda", "is a jedi"))

        assertThat(Error("obiwan", "is a jedi")).isNotEqualTo(Disposed())

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Identical())

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Modified(true, true))
        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Modified(true, false))
        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Modified(false, false))

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(DeletedOnCluster())

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Outdated())

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Created(true))
        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Created(false))

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Updated(true))
        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Updated(false))

        assertThat(Error("yoda", "is a jedi")).isNotEqualTo(Pulled())
    }

    @Test
    fun `Disposed is not equal to all the other states`() {
        // given
        // when
        assertThat(Disposed()).isNotEqualTo(Error("yoda", "is a jedi"))

        assertThat(Disposed()).isEqualTo(Disposed())

        assertThat(Disposed()).isNotEqualTo(Identical())

        assertThat(Disposed()).isNotEqualTo(Modified(true, true))
        assertThat(Disposed()).isNotEqualTo(Modified(true, false))
        assertThat(Disposed()).isNotEqualTo(Modified(false, false))

        assertThat(Disposed()).isNotEqualTo(DeletedOnCluster())

        assertThat(Disposed()).isNotEqualTo(Outdated())

        assertThat(Disposed()).isNotEqualTo(Created(true))
        assertThat(Disposed()).isNotEqualTo(Created(false))

        assertThat(Disposed()).isNotEqualTo(Updated(true))
        assertThat(Disposed()).isNotEqualTo(Updated(false))

        assertThat(Disposed()).isNotEqualTo(Pulled())
    }

    @Test
    fun `Identical is not equal to all the other states`() {
        // given
        // when
        assertThat(Identical()).isNotEqualTo(Error("yoda", "is a jedi"))

        assertThat(Identical()).isNotEqualTo(Disposed())

        assertThat(Identical()).isEqualTo(Identical())

        assertThat(Identical()).isNotEqualTo(Modified(true, true))
        assertThat(Identical()).isNotEqualTo(Modified(true, false))
        assertThat(Identical()).isNotEqualTo(Modified(false, false))

        assertThat(Identical()).isNotEqualTo(DeletedOnCluster())

        assertThat(Identical()).isNotEqualTo(Outdated())

        assertThat(Identical()).isNotEqualTo(Created(true))
        assertThat(Identical()).isNotEqualTo(Created(false))

        assertThat(Identical()).isNotEqualTo(Updated(true))
        assertThat(Identical()).isNotEqualTo(Updated(false))

        assertThat(Identical()).isNotEqualTo(Pulled())
    }

    @Test
    fun `Modified is not equal to all the other states`() {
        // given
        // when
        assertThat(Modified(true, true)).isNotEqualTo(Error("yoda", "is a jedi"))
        assertThat(Modified(true, false)).isNotEqualTo(Error("yoda", "is a jedi"))
        assertThat(Modified(false, false)).isNotEqualTo(Error("yoda", "is a jedi"))
        assertThat(Modified(false, true)).isNotEqualTo(Error("yoda", "is a jedi"))

        assertThat(Modified(true, true)).isNotEqualTo(Disposed())
        assertThat(Modified(false, true)).isNotEqualTo(Disposed())
        assertThat(Modified(false, false)).isNotEqualTo(Disposed())

        assertThat(Modified(true, true)).isNotEqualTo(Identical())
        assertThat(Modified(true, false)).isNotEqualTo(Identical())
        assertThat(Modified(false, false)).isNotEqualTo(Identical())
        assertThat(Modified(false, true)).isNotEqualTo(Identical())

        assertThat(Modified(true, true)).isEqualTo(Modified(true, true))
        assertThat(Modified(true, true)).isNotEqualTo(Modified(true, false))
        assertThat(Modified(true, true)).isNotEqualTo(Modified(false, true))
        assertThat(Modified(true, true)).isNotEqualTo(Modified(false, false))

        assertThat(Modified(true, true)).isNotEqualTo(DeletedOnCluster())
        assertThat(Modified(true, false)).isNotEqualTo(DeletedOnCluster())
        assertThat(Modified(false, true)).isNotEqualTo(DeletedOnCluster())
        assertThat(Modified(false, false)).isNotEqualTo(DeletedOnCluster())

        assertThat(Modified(true, true)).isNotEqualTo(Outdated())
        assertThat(Modified(true, false)).isNotEqualTo(Outdated())
        assertThat(Modified(false, true)).isNotEqualTo(Outdated())
        assertThat(Modified(false, false)).isNotEqualTo(Outdated())

        assertThat(Modified(true, true)).isNotEqualTo(Created(true))
        assertThat(Modified(true, false)).isNotEqualTo(Created(true))
        assertThat(Modified(false, true)).isNotEqualTo(Created(true))
        assertThat(Modified(false, false)).isNotEqualTo(Created(true))
        assertThat(Modified(true, true)).isNotEqualTo(Created(false))
        assertThat(Modified(true, false)).isNotEqualTo(Created(false))
        assertThat(Modified(false, true)).isNotEqualTo(Created(false))
        assertThat(Modified(false, false)).isNotEqualTo(Created(false))

        assertThat(Modified(true, true)).isNotEqualTo(Updated(true))
        assertThat(Modified(true, false)).isNotEqualTo(Updated(true))
        assertThat(Modified(false, true)).isNotEqualTo(Updated(true))
        assertThat(Modified(false, false)).isNotEqualTo(Updated(true))
        assertThat(Modified(true, true)).isNotEqualTo(Updated(false))
        assertThat(Modified(true, false)).isNotEqualTo(Updated(false))
        assertThat(Modified(false, true)).isNotEqualTo(Updated(false))
        assertThat(Modified(false, false)).isNotEqualTo(Updated(false))

        assertThat(Modified(true, true)).isNotEqualTo(Pulled())
        assertThat(Modified(true, false)).isNotEqualTo(Pulled())
        assertThat(Modified(false, true)).isNotEqualTo(Pulled())
        assertThat(Modified(false, false)).isNotEqualTo(Pulled())
    }

    @Test
    fun `DeletedOnCluster is not equal to all the other states`() {
        // given
        // when
        assertThat(DeletedOnCluster()).isNotEqualTo(Error("darth vader", "is on the dark side"))

        assertThat(DeletedOnCluster()).isNotEqualTo(Disposed())

        assertThat(DeletedOnCluster()).isNotEqualTo(Identical())

        assertThat(DeletedOnCluster()).isNotEqualTo(Modified(true, true))
        assertThat(DeletedOnCluster()).isNotEqualTo(Modified(true, false))
        assertThat(DeletedOnCluster()).isNotEqualTo(Modified(false, false))
        assertThat(DeletedOnCluster()).isNotEqualTo(Modified(false, true))

        assertThat(DeletedOnCluster()).isEqualTo(DeletedOnCluster())

        assertThat(DeletedOnCluster()).isNotEqualTo(Outdated())

        assertThat(DeletedOnCluster()).isNotEqualTo(Created(true))
        assertThat(DeletedOnCluster()).isNotEqualTo(Created(false))

        assertThat(DeletedOnCluster()).isNotEqualTo(Updated(true))
        assertThat(DeletedOnCluster()).isNotEqualTo(Updated(false))

        assertThat(DeletedOnCluster()).isNotEqualTo(Pulled())
    }

    @Test
    fun `Outdated is not equal to all the other states`() {
        // given
        // when
        assertThat(Outdated()).isNotEqualTo(Error("obiwan", "is a jedi"))

        assertThat(Outdated()).isNotEqualTo(Disposed())

        assertThat(Outdated()).isNotEqualTo(Identical())

        assertThat(Outdated()).isNotEqualTo(Modified(true, true))
        assertThat(Outdated()).isNotEqualTo(Modified(true, false))
        assertThat(Outdated()).isNotEqualTo(Modified(false, true))
        assertThat(Outdated()).isNotEqualTo(Modified(false, false))

        assertThat(Outdated()).isNotEqualTo(DeletedOnCluster())

        assertThat(Outdated()).isEqualTo(Outdated())

        assertThat(Outdated()).isNotEqualTo(Created(true))
        assertThat(Outdated()).isNotEqualTo(Created(false))

        assertThat(Outdated()).isNotEqualTo(Updated(true))
        assertThat(Outdated()).isNotEqualTo(Updated(false))

        assertThat(Outdated()).isNotEqualTo(Pulled())
    }

    @Test
    fun `Created is not equal to all the other states`() {
        // given
        // when
        assertThat(Created(true)).isNotEqualTo(Error("darth vader", "is on the dark side"))
        assertThat(Created(false)).isNotEqualTo(Error("darth vader", "is on the dark side"))

        assertThat(Created(false)).isNotEqualTo(Disposed())

        assertThat(Created(true)).isNotEqualTo(Identical())
        assertThat(Created(false)).isNotEqualTo(Identical())

        assertThat(Created(true)).isNotEqualTo(Modified(true, true))
        assertThat(Created(true)).isNotEqualTo(Modified(true, false))
        assertThat(Created(true)).isNotEqualTo(Modified(false, false))
        assertThat(Created(true)).isNotEqualTo(Modified(false, true))
        assertThat(Created(false)).isNotEqualTo(Modified(true, true))
        assertThat(Created(false)).isNotEqualTo(Modified(true, false))
        assertThat(Created(false)).isNotEqualTo(Modified(false, false))
        assertThat(Created(false)).isNotEqualTo(Modified(false, true))

        assertThat(Created(true)).isNotEqualTo(DeletedOnCluster())
        assertThat(Created(false)).isNotEqualTo(DeletedOnCluster())

        assertThat(Created(true)).isNotEqualTo(Outdated())
        assertThat(Created(false)).isNotEqualTo(Outdated())

        assertThat(Created(true)).isEqualTo(Created(true))
        assertThat(Created(true)).isNotEqualTo(Created(false))
        assertThat(Created(false)).isNotEqualTo(Created(true))
        assertThat(Created(false)).isEqualTo(Created(false))

        assertThat(Created(true)).isNotEqualTo(Updated(true))
        assertThat(Created(true)).isNotEqualTo(Updated(false))
        assertThat(Created(false)).isNotEqualTo(Updated(true))
        assertThat(Created(false)).isNotEqualTo(Updated(false))

        assertThat(Created(true)).isNotEqualTo(Pulled())
        assertThat(Created(false)).isNotEqualTo(Pulled())
    }

    @Test
    fun `Updated is not equal to all the other states`() {
        // given
        // when
        assertThat(Updated(true)).isNotEqualTo(Error("darth vader", "is on the dark side"))
        assertThat(Updated(false)).isNotEqualTo(Error("darth vader", "is on the dark side"))

        assertThat(Updated(true)).isNotEqualTo(Disposed())
        assertThat(Updated(false)).isNotEqualTo(Disposed())

        assertThat(Updated(true)).isNotEqualTo(Identical())
        assertThat(Updated(false)).isNotEqualTo(Identical())

        assertThat(Updated(true)).isNotEqualTo(Modified(true, true))
        assertThat(Updated(true)).isNotEqualTo(Modified(true, false))
        assertThat(Updated(true)).isNotEqualTo(Modified(false, false))
        assertThat(Updated(true)).isNotEqualTo(Modified(false, true))
        assertThat(Updated(false)).isNotEqualTo(Modified(true, true))
        assertThat(Updated(false)).isNotEqualTo(Modified(true, false))
        assertThat(Updated(false)).isNotEqualTo(Modified(false, false))
        assertThat(Updated(false)).isNotEqualTo(Modified(false, true))

        assertThat(Updated(true)).isNotEqualTo(DeletedOnCluster())
        assertThat(Updated(false)).isNotEqualTo(DeletedOnCluster())

        assertThat(Updated(true)).isNotEqualTo(Outdated())
        assertThat(Updated(false)).isNotEqualTo(Outdated())

        assertThat(Updated(true)).isNotEqualTo(Created(true))
        assertThat(Updated(true)).isNotEqualTo(Created(false))
        assertThat(Updated(false)).isNotEqualTo(Created(true))
        assertThat(Updated(false)).isNotEqualTo(Created(false))

        assertThat(Updated(true)).isEqualTo(Updated(true))
        assertThat(Updated(true)).isNotEqualTo(Updated(false))
        assertThat(Updated(false)).isNotEqualTo(Updated(true))
        assertThat(Updated(false)).isEqualTo(Updated(false))

        assertThat(Updated(true)).isNotEqualTo(Pulled())
        assertThat(Updated(false)).isNotEqualTo(Pulled())
    }

    @Test
    fun `Pulled is not equal to all the other states`() {
        // given
        // when
        assertThat(Pulled()).isNotEqualTo(Error("darth vader", "is on the dark side"))

        assertThat(Pulled()).isNotEqualTo(Disposed())

        assertThat(Pulled()).isNotEqualTo(Identical())

        assertThat(Pulled()).isNotEqualTo(Modified(true, true))
        assertThat(Pulled()).isNotEqualTo(Modified(true, false))
        assertThat(Pulled()).isNotEqualTo(Modified(false, false))
        assertThat(Pulled()).isNotEqualTo(Modified(false, true))

        assertThat(Pulled()).isNotEqualTo(DeletedOnCluster())

        assertThat(Pulled()).isNotEqualTo(Outdated())

        assertThat(Pulled()).isNotEqualTo(Created(true))
        assertThat(Pulled()).isNotEqualTo(Created(false))

        assertThat(Pulled()).isNotEqualTo(Updated(true))
        assertThat(Pulled()).isNotEqualTo(Updated(false))

        assertThat(Pulled()).isEqualTo(Pulled())
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