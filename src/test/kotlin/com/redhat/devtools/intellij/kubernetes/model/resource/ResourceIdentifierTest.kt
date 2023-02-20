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
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResourceIdentifierTest {

    private val resource = PodBuilder(POD2)
        .editMetadata()
        .withGenerateName("generate")
        .endMetadata()
        .build()
    private val identifier = ResourceIdentifier(resource)

    @Test
    fun `#equals should return false if compared to identifier for same resource`() {
        // given
        val sameResource = PodBuilder(resource).build()
        val sameIdentifier = ResourceIdentifier(sameResource)
        // when
        val equals = (identifier == sameIdentifier)
        // then
        assertThat(equals).isTrue
    }

    @Test
    fun `#equals should return false if compared to identifier with different kind`() {
        // given
        val differentKindResource = DeploymentBuilder()
            .withNewMetadata()
                .withName("Atollon Rebel Base")
            .endMetadata()
            .build()
        val differentKindIdentifier = ResourceIdentifier(differentKindResource)
        // when
        val equals = (identifier == differentKindIdentifier)
        // then
        assertThat(equals).isFalse
    }

    @Test
    fun `#equals should return false if compared to identifier with different version`() {
        // given
        val differentVersionResource = PodBuilder(resource)
            .withApiVersion("512")
            .build()
        val differentVersionIdentifier = ResourceIdentifier(differentVersionResource)
        // when
        val equals = (identifier == differentVersionIdentifier)
        // then
        assertThat(equals).isFalse
    }

    @Test
    fun `#equals should return false if compared to identifier with different name`() {
        // given
        val differentNameResource = PodBuilder(resource)
            .editMetadata()
                .withName("yoda")
            .endMetadata()
            .build()
        val differentNameIdentifier = ResourceIdentifier(differentNameResource)
        // when
        val equals = (identifier == differentNameIdentifier)
        // then
        assertThat(equals).isFalse
    }

    @Test
    fun `#equals should return false if compared to identifier with different generateName`() {
        // given
        val differentGenerateNameResource = PodBuilder(resource)
            .editMetadata()
                .withGenerateName("clone")
            .endMetadata()
            .build()
        val differentGenerateNameIdentifier = ResourceIdentifier(differentGenerateNameResource)
        // when
        val equals = (identifier == differentGenerateNameIdentifier)
        // then
        assertThat(equals).isFalse
    }

    @Test
    fun `#equals should return false if compared to identifier with different namespace`() {
        // given
        val differentNamespace = PodBuilder(resource)
            .editMetadata()
            .withNamespace("rebellion")
            .endMetadata()
            .build()
        val differentNamespaceIdentifier = ResourceIdentifier(differentNamespace)
        // when
        val equals = (identifier == differentNamespaceIdentifier)
        // then
        assertThat(equals).isFalse
    }

    @Test
    fun `#equals should return false if namespace is null but compared identifier has namespace`() {
        // given
        val nullNamespace = PodBuilder(resource)
            .editMetadata()
                .withNamespace(null)
            .endMetadata()
            .build()
        val nullNamespaceIdentifier = ResourceIdentifier(nullNamespace)
        val nonNullNamespace = PodBuilder(resource)
            .editMetadata()
            .withNamespace("rebellion")
            .endMetadata()
            .build()
        val nonNullNamespaceIdentifier = ResourceIdentifier(nonNullNamespace)
        // when
        val equals = (nullNamespaceIdentifier == nonNullNamespaceIdentifier)
        // then
        assertThat(equals).isFalse
    }

}