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

import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.HasChildren
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph

@Suppress("UNCHECKED_CAST")
object DescriberTestUtils {

	inline fun <reified T : Paragraph> getParagraph(title: String, parent: HasChildren<Paragraph>): T? {
		return parent.children.find { paragraph ->
			paragraph is T
					&& paragraph.title == title
		} as T?
	}

	inline fun <reified T : Paragraph> getParagraph(path: List<String>, parent: HasChildren<Paragraph>): T? {
		val iterator = path.iterator()
		var paragraph: Paragraph? = parent
		while (iterator.hasNext()) {
			if (paragraph is HasChildren<*>) {
				paragraph = getParagraph(iterator.next(), paragraph as HasChildren<Paragraph>)
			} else {
				return null
			}
		}
		return paragraph as? T?
	}

	inline fun <reified TYPE : Paragraph, reified CHILDREN : Paragraph> getChildren(
		title: String,
		parent: HasChildren<Paragraph>?
	): List<CHILDREN>? {
		if (parent == null) {
			return null
		}
		val chapter = getParagraph<TYPE>(title, parent) ?: return null
		if (chapter !is HasChildren<*>) {
			return null
		}
		return chapter.children as List<CHILDREN>?
	}

	fun toMap(children: List<NamedValue>?): Map<String, Any?>? {
		return children?.associate { value ->
			value.title to value.value
		}
	}
}
