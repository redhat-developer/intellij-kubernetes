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
package com.redhat.devtools.intellij.kubernetes.editor.describe

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.Collections

class YAMLDescriptionTest {

	@Test
	fun `toText should return title and value if document has 1 paragraph`() {
		// given
		val description = YAMLDescription()
		description.addIfExists("jedi", "luke")
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo("jedi: luke\n")
	}

	@Test
	fun `toText should return title and integer value`() {
		// given
		val description = YAMLDescription()
		description.addIfExists("jedi", 42 as Int?)
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo("jedi: 42\n")
	}

	@Test
	fun `toText should return title and boolean value`() {
		// given
		val description = YAMLDescription()
		description.addIfExists("jedi", true)
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo("jedi: true\n")
	}

	@Test
	fun `toText should NOT return any text if label is present but value is empty`() {
		// given
		val description = YAMLDescription()
		description.addIfExists("jedi", "")
		// when
		val text = description.toText()
		// then
		assertThat(text).isEmpty()
	}

	@Test
	fun `toText should NOT return any text if label is present but value provider returns null`() {
		// given
		val description =YAMLDescription()
		description.addIfExists("jedi") { null }
		// when
		val text = description.toText()
		// then
		assertThat(text).isEmpty()
	}

	@Test
	fun `toText should NOT return any text if label is empty`() {
		// given
		val description = YAMLDescription()
		description.addIfExists("", "luke")
		// when
		val text = description.toText()
		// then
		assertThat(text).isEmpty()
	}

	@Test
	fun `toText should return title and 'none' is sub-paragraphs are empty`() {
		// given
		val description = YAMLDescription()
		description.addChapter("jedi", Collections.emptyList())
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo("jedi: $NONE\n")
	}

	@Test
	fun `toText should return title and 2 values if document has a chapter with 2 values`() {
		// given
		val description = YAMLDescription()
		val jedis = Chapter("jedis")
			.addIfExists(NamedValue("padawan", "luke"))
			.addIfExists(NamedValue("master", "yoda"))
		description.addIfExists(jedis)
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo(
			"""
			jedis:
			  padawan: luke
			  master: yoda

			""".trimIndent()
		)
	}

	@Test
	fun `toText should return 2 titles with values if document has 2 chapters with values`() {
		// given
		val description =YAMLDescription()
		val jedis = Chapter("jedis").addIfExists(
			NamedValue("padawan", "luke")
		)
		val siths = Chapter("sith").addIfExists(
			NamedValue("master", "darth vader")
		)
		description
			.addIfExists(jedis)
			.addIfExists(siths)
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo(
			"""
			jedis:
			  padawan: luke
			sith:
			  master: darth vader
			
			""".trimIndent()
		)
	}

	@Test
	fun `toText should return title and sequence of items`() {
		// given
		val description = YAMLDescription()
		description.addIfExists(
			NamedSequence(
			"jedis", listOf(
				"leia",
				"luke",
				"obiwan")
			)
		)
		// when
		val text = description.toText()
		// then
		assertThat(text).isEqualTo(
			"""
			jedis:
			- leia
			- luke
			- obiwan

			""".trimIndent()
		)
	}
}