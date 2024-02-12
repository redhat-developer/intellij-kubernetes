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

import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiElement
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonProperty
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonPsiFileFactory
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createProjectWithServices
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLGenerator
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.junit.Test
import java.util.Base64

class Base64ValueAdapterTest {

	private val yamlElementGenerator = yamlElementGenerator()
	private val project = createProjectWithServices(yamlElementGenerator)

	private fun yamlElementGenerator() = createYAMLGenerator()

	@Test
	fun `#get should return value of YAMLKeyValue`() {
		// given
		val element = createYAMLKeyValue(value = "yoda", project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.get()
		// then
		assertThat(text).isEqualTo("yoda")
	}

	@Test
	fun `#get should return value with quotes`() {
		// given
		val element = createYAMLKeyValue(value = "\"yoda\"", project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.get()
		// then
		assertThat(text).isEqualTo("\"yoda\"")
	}

	@Test
	fun `#get should return value of JsonProperty`() {
		// given
		val element = createJsonProperty(value = "yoda", project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.get()
		// then
		assertThat(text).isEqualTo("yoda")
	}

	@Test
	fun `#get should return null for unknown PsiElement`() {
		// given
		val element = mock<PsiElement>()
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.get()
		// then
		assertThat(text).isNull()
	}

	@Test
	fun `#getDecoded should return value decoded value`() {
		// given
		val element = createYAMLKeyValue(value = toBase64("skywalker"), project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.getDecoded()
		// then
		assertThat(text).isEqualTo("skywalker")
	}

	@Test
	fun `#getDecoded should return null if value isn't valid base64`() {
		// given
		val element = createYAMLKeyValue(value = toBase64("skywalker") + "bogus", project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.getDecoded()
		// then
		assertThat(text).isNull()
	}

	@Test
	fun `#getDecoded should return null if value is null`() {
		// given
		val element = createYAMLKeyValue(value = null, project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.getDecoded()
		// then
		assertThat(text).isNull()
	}

	@Test
	fun `#getDecoded should return value without quotes`() {
		// given
		val element = createYAMLKeyValue(value = "\"" + toBase64("yoda") + "\"", project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val text = adapter.getDecoded()
		// then
		assertThat(text).isEqualTo("yoda")
	}

	@Test
	fun `#getDecodedBytes should return decoded bytes`() {
		// given
		val element = createYAMLKeyValue(value = toBase64("skywalker"), project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val bytes = adapter.getDecodedBytes()
		// then
		assertThat(bytes).isEqualTo("skywalker".toByteArray())
	}

	@Test
	fun `#getDecodedBytes should return null for null value`() {
		// given
		val element = createYAMLKeyValue(value = null, project = project)
		val adapter = Base64ValueAdapter(element)
		// when
		val bytes = adapter.getDecodedBytes()
		// then
		assertThat(bytes).isNull()
	}

	@Test
	fun `#set should add new YAMKeyValue to parent and delete current element`() {
		// given
		val parent = createYAMLKeyValue("group", "jedis", project = project)
		val element = createYAMLKeyValue("jedi", "yoda", parent, project)
		val adapter = Base64ValueAdapter(element)
		// when
		adapter.set("luke")
		// then
		verify(parent).addAfter(any<YAMLKeyValue>(), eq(element))
		verify(element).delete()
	}

	@Test
	fun `#set should create new YAMKeyValue with same key and given base64 encoded value`() {
		// given
		val parent = createYAMLKeyValue("group", "jedis", project = project)
		val element = createYAMLKeyValue("jedi", "yoda", parent, project)
		val adapter = Base64ValueAdapter(element)
		// when
		adapter.set("obiwan")
		// then
		verify(yamlElementGenerator).createYamlKeyValue("jedi", toBase64("obiwan"))
	}

	@Test
	fun `#set should create new multiline value if existing value is multiline`() {
		// given
		val parent = createYAMLKeyValue("group", "jedis", project = project)
		val element = createYAMLKeyValue("jedi", "|\nyoda", parent, project)
		val adapter = Base64ValueAdapter(element)
		// when
		adapter.set("obiwan")
		// then
		verify(yamlElementGenerator).createYamlKeyValue(any(), eq("|\n" + toBase64("obiwan")))
	}

	@Test
	fun `#set should create new quoted value if existing value is quoted`() {
		// given
		val parent = createYAMLKeyValue("group", "jedis", project = project)
		val element = createYAMLKeyValue("jedi", "\"yoda\"", parent, project)
		val adapter = Base64ValueAdapter(element)
		// when
		adapter.set("anakin")
		// then
		verify(yamlElementGenerator).createYamlKeyValue(any(), eq("\"" + toBase64("anakin") + "\""))
	}

	@Test
	fun `#set should wrap new value at given position`() {
		// given
		val parent = createYAMLKeyValue("group", "jedis", project = project)
		val element = createYAMLKeyValue("jedi", "yoda", parent, project)
		val adapter = Base64ValueAdapter(element)
		// when
		adapter.set("|\njedi qui-gon", 4)
		// then
		verify(yamlElementGenerator).createYamlKeyValue(any(),
			eq(toBase64("|\njedi qui-gon").chunked(4).joinToString("")))
	}

	@Test
	fun `#set should add new JsonProperty to parent and delete current element`() {
		// given
		val properties: MutableList<JsonProperty> = mutableListOf()
		val psiFileFactory = createJsonPsiFileFactory(properties)
		val project = createProjectWithServices(psiFileFactory = psiFileFactory)
		val parent = createJsonProperty("group", "jedis", project = project)
		val property = createJsonProperty("jedi", "yoda", parent, project)
		properties.add(property)
		val adapter = Base64ValueAdapter(property)
		// when
		adapter.set("luke")
		// then
		verify(parent).addAfter(any<JsonProperty>(), eq(property))
		verify(property).delete()
	}

	private fun toBase64(value: String): String {
		return String(Base64.getEncoder().encode(value.toByteArray()))
	}
}