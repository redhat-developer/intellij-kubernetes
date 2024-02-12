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
package com.redhat.devtools.intellij.kubernetes.editor.inlay

import com.nhaarman.mockitokotlin2.mock
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLValue
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test


class Base64PresentationsTest {

	private val secret = kubernetesResourceInfo(
		"yoda", "light side", kubernetesTypeInfo("Secret", "v1")
	)
	private val configMap = kubernetesResourceInfo(
		"skywalker", "light side", kubernetesTypeInfo("ConfigMap", "v1")
	)
	private val pod = kubernetesResourceInfo(
		"anakin", "dark side", kubernetesTypeInfo("Pod", "v1")
	)

	private val dataElement = createYAMLKeyValue("data")
	private val binaryDataElement = createYAMLKeyValue("binaryData")

	@Test
	fun `#create should create factory for Secret if has data`() {
		// given
		val content = createYAMLValue(arrayOf(dataElement))
		// when
		val factory = Base64Presentations.create(content, secret, mock(), mock())
		// then
		assertThat(factory).isNotNull()
	}

	@Test
	fun `#create should NOT create factory for Secret if has NO data`() {
		// given
		val content = createYAMLValue(emptyArray())
		// when
		val factory = Base64Presentations.create(content, secret, mock(), mock())
		// then
		assertThat(factory).isNull()
	}

	@Test
	fun `#create should create factory for ConfigMap if has binaryData`() {
		// given
		val content = createYAMLValue(arrayOf(binaryDataElement))
		// when
		val factory = Base64Presentations.create(content, configMap, mock(), mock())
		// then
		assertThat(factory).isNotNull()
	}

	@Test
	fun `#create NOT should create factory for ConfigMap if has NO binaryData`() {
		// given
		val content = createYAMLValue(emptyArray())
		// when
		val factory = Base64Presentations.create(content, configMap, mock(), mock())
		// then
		assertThat(factory).isNull()
	}

	@Test
	fun `#create should NOT create factory for Pod`() {
		// given
		// when
		val factory = Base64Presentations.create(mock(), pod, mock(), mock())
		// then
		assertThat(factory).isNull()
	}
}