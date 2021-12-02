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
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FileEditorUtilsTest {

    @Test
    fun `#isKubernetesResource should return true if has apiGroup and kind`() {
        // given
        val info = kubernetesResourceInfo("godzilla", "tokyo", kubernetesTypeInfo("lizard", "monster"))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isTrue()
    }

    @Test
    fun `#isKubernetesResource should return true if has apiGroup and kind but no name`() {
        // given
        val info = kubernetesResourceInfo(null, "tokyo", kubernetesTypeInfo("lizard", "monster"))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isTrue()
    }

    @Test
    fun `#isKubernetesResource should return true if has apiGroup and kind but no namespace`() {
        // given
        val info = kubernetesResourceInfo("godzilla", null, kubernetesTypeInfo("lizard", "monster"))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isTrue()
    }

    @Test
    fun `#isKubernetesResource should return false if has blank apiGroup`() {
        // given
        val info = kubernetesResourceInfo("godzilla", "tokyo", kubernetesTypeInfo("lizard", ""))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isFalse()
    }

    @Test
    fun `#isKubernetesResource should return false if has null apiGroup`() {
        // given
        val info = kubernetesResourceInfo("godzilla", "tokyo", kubernetesTypeInfo("lizard", null))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isFalse()
    }

    @Test
    fun `#isKubernetesResource should return false if has blank kind`() {
        // given
        val info = kubernetesResourceInfo("godzilla", "tokyo", kubernetesTypeInfo("", "monster"))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isFalse()
    }

    @Test
    fun `#isKubernetesResource should return false if has null kind`() {
        // given
        val info = kubernetesResourceInfo("godzilla", "tokyo", kubernetesTypeInfo(null, "monster"))
        // when
        val isKubernetesResource = isKubernetesResource(info)
        // then
        assertThat(isKubernetesResource).isFalse()
    }
}