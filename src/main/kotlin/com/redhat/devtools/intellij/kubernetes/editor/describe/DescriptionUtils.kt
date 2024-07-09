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

import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DescriptionConstants {

	object Labels {
		const val NAME = "Name"
		const val NAMESPACE = "Namespace"
	}

	object Values {
		const val NONE = "<none>"
		const val UNSET = "<unset>"
	}
}

fun createValueOrSequence(title: String, items: List<String>?): Paragraph? {
	return if (items.isNullOrEmpty()) {
		null
	} else if (items.size == 1) {
		NamedValue(title, items.first())
	} else {
		NamedSequence(title, items)
	}
}

fun createValues(map: Map<String, String>?): List<NamedValue> {
	if (map.isNullOrEmpty()) {
		return emptyList()
	}
	return map.entries.map { entry -> NamedValue(entry.key, entry.value) }
}

fun toString(items: List<String>?): String? {
	return items?.joinToString("\n")
}

fun toRFC1123Date(dateTime: String?): String? {
	if (dateTime == null) {
		return null
	}
	val parsed = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_ZONED_DATE_TIME)
	val zoned = parsed.atOffset(ZonedDateTime.now().offset)
	return DateTimeFormatter.RFC_1123_DATE_TIME.format(zoned)
}

fun toRFC1123DateOrUnrecognized(dateTime: String?): String? {
	return try {
		toRFC1123Date(dateTime)
	} catch (e: DateTimeException) {
		"Unrecognized Date: $dateTime"
	}
}

/**
 * Returns a human-readable form of the given date/time since the given date/time.
 * Returns `null` if the given dateTime is not understood.
 * The logic is copied from k8s.io/apimachinery/util/duration/	duration/HumanDuration.
 *
 * @see [k8s.io/apimachinery/util/duration/duration/HumanDuration](https://github.com/kubernetes/apimachinery/blob/d7e1c5311169d5ece2db0ae0118066859aa6f7d8/pkg/util/duration/duration.go#L48)
 * @see
 */
fun toHumanReadableDurationSince(dateTime: String?, since: LocalDateTime): String? {
	if (dateTime == null) {
		return null
	}
	return try {
		val parsed = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_ZONED_DATE_TIME)
		val difference = if (since.isBefore(parsed)) {
			Duration.between(since, parsed)
		} else {
			Duration.between(parsed, since)
		}
		val seconds = difference.toSeconds()
		return when {
			seconds < 60 * 2 ->
				// < 2 minutes
				"${seconds}s"

			seconds < 60 * 10 ->
				// < 10 minutes
				"${difference.toMinutesPart()}m${difference.toSecondsPart()}s"

			seconds < 60 * 60 * 3 ->
				// < 3 hours
				"${difference.toMinutes()}m"

			seconds < 60 * 60 * 8 ->
				// < 8 hours
				"${difference.toHoursPart()}h"

			seconds < 60 * 60 * 48 ->
				// < 48 hours
				"${difference.toHours()}h${difference.toMinutesPart()}m"

			seconds < 60 * 60 * 24 * 8 -> {
				// < 192 hours
				if (difference.toHoursPart() == 0) {
					"${difference.toDaysPart()}d"
				} else {
					"${difference.toDaysPart()}d${difference.toHoursPart()}h"
				}
			}

			seconds < 60 * 60 * 24 * 365 * 2 ->
				// < 2 years
				"${difference.toDaysPart()}d"

			seconds < 60 * 60 * 24 * 365 * 8 -> {
				// < 8 years
				val years = difference.toDaysPart() / 365
				"${years}y${difference.toDaysPart() % 365}d"
			}

			else ->
				"${difference.toDaysPart() / 365}y"
		}
	} catch (e: DateTimeException) {
		null
	}
}
