/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.nhaarman.mockitokotlin2.mock
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.Base64

class ResourceEditorUtilsTest {

	@Test
	fun `#isKubernetesResource should return true for info with apiGroup and kind`() {
		// given
		val kubernetesTypeInfo: KubernetesTypeInfo =
			kubernetesTypeInfo("jedi", "v1")
		val kubernetesResourceInfo: KubernetesResourceInfo =
			kubernetesResourceInfo("yoda", "light side", kubernetesTypeInfo)
		// when
		val isKubernetesResource = isKubernetesResource(kubernetesResourceInfo)
		// then
		assertThat(isKubernetesResource).isTrue()
	}

	@Test
	fun `#isKubernetesResource should return false for info without apiGroup`() {
		// given
		val kubernetesTypeInfo: KubernetesTypeInfo =
			kubernetesTypeInfo("jedi", null)
		val kubernetesResourceInfo: KubernetesResourceInfo = mock()
		kubernetesResourceInfo("yoda", "light side", kubernetesTypeInfo)
		// when
		val isKubernetesResource = isKubernetesResource(kubernetesResourceInfo)
		// then
		assertThat(isKubernetesResource).isFalse()
	}

	@Test
	fun `#isKubernetesResource should return false for info without kind`() {
		// given
		val kubernetesTypeInfo: KubernetesTypeInfo =
			kubernetesTypeInfo(null, "v1")
		val kubernetesResourceInfo: KubernetesResourceInfo = mock()
		kubernetesResourceInfo("yoda", "light side", kubernetesTypeInfo)
		// when
		val isKubernetesResource = isKubernetesResource(kubernetesResourceInfo)
		// then
		assertThat(isKubernetesResource).isFalse()
	}

	@Test
	fun `#isKubernetesResource should return true if info has the given kind`() {
		// given
		val kubernetesTypeInfo: KubernetesTypeInfo = kubernetesTypeInfo("jedi", "v1")
		// when
		val isKubernetesResource = isKubernetesResource("jedi", kubernetesTypeInfo)
		// then
		assertThat(isKubernetesResource).isTrue()
	}

	@Test
	fun `#isKubernetesResource should return false if info doesnt have the given kind`() {
		// given
		val kubernetesTypeInfo: KubernetesTypeInfo = kubernetesTypeInfo("jedi", "v1")
		// when
		val isKubernetesResource = isKubernetesResource("sith", kubernetesTypeInfo)
		// then
		assertThat(isKubernetesResource).isFalse()
	}

	@Test
	fun `#decodeBase64 should return base64 decoded value for given string`() {
		// given
		val encoded = String(Base64.getEncoder().encode("anakin".toByteArray()))
		// when
		val decoded = decodeBase64(encoded)
		// then
		assertThat(decoded).isEqualTo("anakin")
	}

	@Test
	fun `#decodeBase64 should return null if given string is NOT base64 encoded`() {
		// given
		val encoded = String(Base64.getEncoder().encode("anakin".toByteArray())) + "bogus"
		// when
		val decoded = decodeBase64(encoded)
		// then
		assertThat(decoded).isNull()
	}

	@Test
	fun `#decodeBase64 should return null if given string is null`() {
		// given
		// when
		val decoded = decodeBase64(null)
		// then
		assertThat(decoded).isNull()
	}

	@Test
	fun `#decodeBase64 should return blank if given string is blank`() {
		// given
		// when
		val decoded = decodeBase64("")
		// then
		assertThat(decoded).isEqualTo("")
	}
}