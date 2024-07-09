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

open class Chapter(title: String, paragraphs: List<Paragraph> = emptyList()) : HasChildren<Paragraph>(title, paragraphs) {

	fun add(title: String, value: Boolean?): Chapter {
		return addIfExists(title, value ?: false)
	}

	fun add(title: String, value: Int?): Chapter {
		return if (value == null) {
			addIfExists(title, NONE)
		} else {
			addIfExists(title, value)
		}
	}

	fun add(title: String, value: Long?): Chapter {
		if (value == null) {
			addIfExists(title, NONE)
		} else {
			addIfExists(title, value)
		}
		return this
	}

	fun add(title: String, value: String?): Chapter {
		addIfExists(title, value ?: NONE)
		return this
	}

	fun addSequence(title: String, values: List<String>?): Chapter {
		if (values.isNullOrEmpty()) {
			addIfExists(title, NONE)
		} else {
			addIfExists(NamedSequence(title, values))
		}
		return this
	}

	fun addChapter(title: String, paragraphs: List<Paragraph>?): Chapter {
		if (paragraphs.isNullOrEmpty()) {
			addIfExists(title, NONE)
		} else {
			addIfExists(Chapter(title, paragraphs))
		}
		return this
	}

	fun addChapterIfExists(title: String, paragraphs: List<Paragraph>): Chapter {
		if (paragraphs.isNotEmpty()) {
			addIfExists(Chapter(title, paragraphs))
		}
		return this
	}

	fun addIfExists(title: String, value: String?): Chapter {
		if (value.isNullOrBlank()) {
			return this
		}
		return addIfExists(NamedValue(title, value))
	}

	fun addIfExists(label: String, valueProvider: () -> String?): Chapter {
		return addIfExists(label, valueProvider.invoke())
	}

	fun addIfExists(label: String, value: Int?): Chapter {
		if (value == null) {
			return this
		}
		return addIfExists(NamedValue(label, value))
	}

	fun addIfExists(label: String, value: Long?): Chapter {
		if (value == null) {
			return this
		}
		return addIfExists(NamedValue(label, value))
	}

	fun addIfExists(label: String, value: Boolean?): Chapter {
		if (value == null) {
			return this
		}
		return addIfExists(NamedValue(label, value))
	}

	fun addIfExists(title: String, paragraph: Paragraph): Chapter {
		return addIfExists(Chapter(title, listOf(paragraph)))
	}

	fun addIfExists(paragraph: Paragraph?): Chapter {
		if (paragraph != null) {
			children.add(paragraph)
		}
		return this
	}

	fun addIfExists(paragraphs: List<Paragraph>): Chapter {
		paragraphs.forEach { addIfExists(it) }
		return this
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Chapter) return false
		if (!super.equals(other)) return false
		return true
	}

}