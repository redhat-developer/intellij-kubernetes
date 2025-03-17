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
import com.redhat.devtools.intellij.kubernetes.editor.inlay.base64.Base64Presentations
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createKeyValueFor
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Before
import org.junit.Test


class Base64PresentationsTest {

	private val secret = kubernetesTypeInfo("Secret", "v1")
	private val configMap = kubernetesTypeInfo("ConfigMap", "v1")
	private val pod = kubernetesTypeInfo("Pod", "v1")

	private lateinit var yamlElement: YAMLMapping

	@Before
	fun before() {
		this.yamlElement = mock<YAMLMapping>()
	}

	@Test
	fun `#create should create factory for Secret if has data`() {
		// given
		val dataMapping = createYAMLMapping(
			listOf(
				createKeyValueFor("token-id", "NWVtaXRq"),
				createKeyValueFor("token-secret", "a3E0Z2lodnN6emduMXAwcg==")
			)
		)
		val data = createKeyValueFor("data", dataMapping, yamlElement)
		// when
		val factory = Base64Presentations.create(yamlElement, secret, mock(), mock())
		// then
		assertThat(factory).isNotNull()
	}

	@Test
	fun `#create should NOT create factory for Secret if has NO data`() {
		// given
		// when
		val factory = Base64Presentations.create(yamlElement, secret, mock(), mock())
		// then
		assertThat(factory).isNull()
	}

	@Test
	fun `#create should create factory for ConfigMap if has binaryData`() {
		// given
		val binaryDataMapping = createYAMLMapping(
			listOf(
				createKeyValueFor("my-binary-file.bin", "U29tZSBiYXNlNjQgZW5jb2RlZCBiaW5hcnkgZGF0YQ"),
				createKeyValueFor("another-binary.dat", "VGhpcyBpcyBhbm90aGVyIGV4YW1wbGUgb2YgYmluYXJ5IGRhdGE")
			)
		)
		val binaryData = createKeyValueFor("binaryData", binaryDataMapping, yamlElement)
		// when
		val factory = Base64Presentations.create(yamlElement, configMap, mock(), mock())
		// then
		assertThat(factory).isNotNull()
	}

	@Test
	fun `#create NOT should create factory for ConfigMap if has NO binaryData`() {
		// given
		// when
		val factory = Base64Presentations.create(yamlElement, configMap, mock(), mock())
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