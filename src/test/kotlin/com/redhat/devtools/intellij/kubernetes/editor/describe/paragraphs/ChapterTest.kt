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
package com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ChapterTest {

	@Test
	fun `#add adds Boolean value if it is NOT null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("obiwan is a jedi", true)
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(true)
	}

	@Test
	fun `#add adds false if Boolean is null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("obiwan is a jedi", null as Boolean?)
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(false)
	}

	@Test
	fun `#addIfExists does NOT add if Boolean value is null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addIfExists("obiwan is a jedi", null as Boolean?)
		// then
		assertThat(chapter.children).isEmpty()
	}

	@Test
	fun `#add adds Int value if it is NOT null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("light sabers that obiwan owns", 42.toInt())
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(42)
	}

	@Test
	fun `#add adds NONE if Int is null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("light sabers that obiwan owns", null as Int?)
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(NONE)
	}


	@Test
	fun `#add adds Long value if it is NOT null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("capes that obiwan owns", 42.toLong())
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(42.toLong())
	}

	@Test
	fun `#add adds NONE if Long is null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("capes that obiwan owns", null as Long?)
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(NONE)
	}

	@Test
	fun `#add adds String value if it is NOT null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("name", "leia")
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo("leia")
	}

	@Test
	fun `#add adds NONE if String is null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.add("name", null as String?)
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(NONE)
	}

	@Test
	fun `#addSequence adds NONE if values are null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addSequence("dark side", null as List<String>?)
		// then
		assertThat(chapter.children).hasSize(1)
		assertThat(chapter.children.first()).isExactlyInstanceOf(NamedValue::class.java)
		val child = chapter.children.first() as NamedValue
		assertThat(child.value).isEqualTo(NONE)
	}

	@Test
	fun `#addSequence adds NONE if values are empty`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addSequence("dark side", emptyList())
		// then
		assertThat((chapter.children.first() as NamedValue).value).isEqualTo(NONE)
	}

	@Test
	fun `#addSequence adds sequence if values are NOT null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addSequence(
			"light side", listOf(
				"leia",
				"obiwan",
				"luke"
			)
		)
		// then
		val sequence = (chapter.children.first() as NamedSequence)
		assertThat(sequence.title).isEqualTo("light side")
		assertThat(sequence.children).containsOnly(
			"leia",
			"obiwan",
			"luke"
		)
	}

	@Test
	fun `#addChapter adds NONE if paragraphs are empty`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addChapter("dark side", emptyList())
		// then
		val child = chapter.children.first() as NamedValue
		assertThat(child.title).isEqualTo("dark side")
		assertThat(child.value).isEqualTo(NONE)
	}

	@Test
	fun `#addChapter adds NONE if chapters are null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addChapter("dark side", null as List<Paragraph>?)
		// then
		val child = chapter.children.first() as NamedValue
		assertThat(child.title).isEqualTo("dark side")
		assertThat(child.value).isEqualTo(NONE)
	}

	@Test
	fun `#addChapter adds chapters if chapters are NOT null`() {
		// given
		val chapter = Chapter("jedis")
		assertThat(chapter.children).isEmpty()
		// when
		chapter.addChapter(
			"light side", listOf(
				NamedValue("princess", "leia"),
				NamedValue("luke", "skywalker")
			)
		)
		// then
		val children = chapter.children.first() as Chapter
		assertThat(children.title).isEqualTo("light side")
		assertThat(children.children).containsOnly(
			NamedValue("princess", "leia"),
			NamedValue("luke", "skywalker")
		)
	}
}