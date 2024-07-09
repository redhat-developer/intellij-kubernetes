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

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.toMap
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDateTime

class DescriptionUtilsTest {

	@Test
	fun `toString returns items joined using CR`() {
		// given
		val items = listOf("use", "the", "force", "luke")
		// when
		val joined = toString(items)
		// then
		assertThat(joined).isEqualTo("use\nthe\nforce\nluke")
	}

	@Test
	fun `toString returns null if items is null`() {
		// given
		// when
		val joined = toString(null)
		// then
		assertThat(joined).isNull()
	}

	@Test
	fun `toHumanReadableDurationSince returns null if date is null`() {
		// given
		// when
		val since = toHumanReadableDurationSince(null, LocalDateTime.now())
		// then
		assertThat(since).isNull()
	}

	@Test
	fun `toHumanReadableDurationSince returns positive difference if since is after date`() {
		// given
		val deletionTimestamp = "2024-07-14T14:59:19Z"
		// when "since" is after deletionTimeStamp
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 14, 14, 59))
		// then
		assertThat(since).isEqualTo("19s")
	}

	@Test
	fun `toHumanReadableDurationSince returns null if date string is illegal format`() {
		// given
		val deletionTimestamp = "bogus"
		// when "since" is after deletionTimeStamp
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.now())
		// then
		assertThat(since).isNull()
	}

	@Test
	fun `toHumanReadableDurationSince returns days if difference is less than 2y`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2023, 6, 14, 13, 49))
		// then
		assertThat(since).isEqualTo("397d")
	}

	@Test
	fun `toHumanReadableDurationSince returns years and days if difference is less than 8y but more than 2y`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2020, 6, 14, 13, 49))
		// then
		assertThat(since).isEqualTo("4y32d")
	}

	@Test
	fun `toHumanReadableDurationSince returns years if difference is more than 8y`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2015, 6, 14, 13, 49))
		// then
		assertThat(since).isEqualTo("9y")
	}

	@Test
	fun `toHumanReadableDurationSince returns days and hours if difference is less than 8d and more than 2d`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 11, 13, 49))
		// then
		assertThat(since).isEqualTo("4d1h")
	}

	@Test
	fun `toHumanReadableDurationSince returns only days if difference is less than 8d and more than 2d and does not differ in hours`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 11, 14, 59))
		// then
		assertThat(since).isEqualTo("4d")
	}

	@Test
	fun `toHumanReadableDurationSince returns hours and minutes if difference is less than 2d but more than 8h`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 14, 13, 49))
		// then
		assertThat(since).isEqualTo("25h10m")
	}

	@Test
	fun `toHumanReadableDurationSince returns hours if difference is less than 8h but more than 3h`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 15, 11, 49))
		// then
		assertThat(since).isEqualTo("3h")
	}

	@Test
	fun `toHumanReadableDurationSince returns minutes if difference is less than 3h but more than 10m`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 15, 13, 49))
		// then
		assertThat(since).isEqualTo("70m")
	}

	@Test
	fun `toHumanReadableDurationSince returns seconds if difference is less than 2m`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 15, 14, 58))
		// then
		assertThat(since).isEqualTo("79s")
	}

	@Test
	fun `toHumanReadableDurationSince returns minutes and seconds if difference is less 10m but more than 2m`() {
		// given
		val deletionTimestamp = "2024-07-15T14:59:19Z"
		// when
		val since = toHumanReadableDurationSince(deletionTimestamp, LocalDateTime.of(2024, 7, 15, 14, 51))
		// then
		assertThat(since).isEqualTo("8m19s")
	}

	@Test
	fun `toRFC1123Date returns the RFC1123 formatted date string`() {
		// given
		val timestamp = "2024-07-15T14:59:19Z"
		// when
		val localDateTime = toRFC1123DateOrUnrecognized(timestamp)
		// then
		assertThat(localDateTime).startsWith("Mon, 15 Jul 2024 14:59:19") // ends with +0200 in GMT+2
	}

	@Test
	fun `toRFC1123DateOrUnrecognized returns null if given date is null`() {
		// given
		// when
		val localDateTime = toRFC1123DateOrUnrecognized(null)
		// then
		assertThat(localDateTime).isNull()
	}

	@Test
	fun `toRFC1123DateOrUnrecognized returns 'Unrecognized' if given date is not parseable`() {
		// given
		// when
		val localDateTime = toRFC1123DateOrUnrecognized("bogus")
		// then
		assertThat(localDateTime).isEqualTo("Unrecognized Date: bogus")
	}

	@Test
	fun `createValueOrSequence returns null if given list is empty`() {
		// given
		val items = emptyList<String>()
		// when
		val paragraph = createValueOrSequence("Items", items)
		// then
		assertThat(paragraph).isNull()
	}

	@Test
	fun `createValueOrSequence returns value if given list has only 1 entry`() {
		// given
		val items = listOf("leia")
		// when
		val paragraph = createValueOrSequence("Items", items)
		// then
		assertThat(paragraph).isExactlyInstanceOf(NamedValue::class.java)
		assertThat((paragraph as NamedValue).value).isEqualTo("leia")
	}

	@Test
	fun `createValueOrSequence returns sequence if given list has several items`() {
		// given
		val items = listOf("leia", "luke", "obiwan")
		// when
		val paragraph = createValueOrSequence("Items", items)
		// then
		assertThat(paragraph).isExactlyInstanceOf(NamedSequence::class.java)
		assertThat((paragraph as NamedSequence).children).containsOnly("leia", "luke", "obiwan")
	}

	@Test
	fun `createValues returns empty list if items are null`() {
		// given
		// when
		val paragraphs = createValues(null)
		// then
		assertThat(paragraphs).isEmpty()
	}

	@Test
	fun `createValues returns list of NamedValue(s)`() {
		// given
		val items = mapOf(
			"princess" to "leia",
			"luke" to "skywalker",
			"obiwan" to "kenobi")
		// when
		val paragraphs = createValues(items)
		// then
		assertThat(toMap(paragraphs)).containsExactlyEntriesOf(
			mapOf(
				"princess" to "leia",
				"luke" to "skywalker",
				"obiwan" to "kenobi"
			)
		)
	}
}