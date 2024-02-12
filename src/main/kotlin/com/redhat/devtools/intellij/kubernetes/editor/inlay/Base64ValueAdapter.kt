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

import com.intellij.psi.PsiElement
import com.redhat.devtools.intellij.kubernetes.editor.util.decodeBase64
import com.redhat.devtools.intellij.kubernetes.editor.util.decodeBase64ToBytes
import com.redhat.devtools.intellij.kubernetes.editor.util.encodeBase64
import com.redhat.devtools.intellij.kubernetes.editor.util.getValue
import com.redhat.devtools.intellij.kubernetes.editor.util.setValue

class Base64ValueAdapter(private val element: PsiElement) {

	private companion object {
		private val CONTENT_REGEX = Regex("[^\"\n |]*", RegexOption.MULTILINE)
		private const val START_MULTILINE = "|\n"
		private const val QUOTE = "\""
	}

	fun set(value: String, wrapAt: Int = -1) {
		val possiblyMultiline = if (isMultiline()) {
			wrap(wrapAt, START_MULTILINE + encodeBase64(value))
		} else {
			encodeBase64(value)
		}
		?: return
		val possiblyQuoted =
		if (isQuoted()) {
			QUOTE + possiblyMultiline + QUOTE
		} else {
			possiblyMultiline
		}
		setValue(possiblyQuoted, element)
	}

	private fun wrap(at: Int, string: String?): String? {
		return when {
			string == null -> null
			at == -1 -> string
			else -> {
				string.chunked(at).joinToString("\n")
			}
		}
	}

	fun get(): String? {
		return getValue(element)
	}

	private fun isMultiline(): Boolean {
		return get()?.startsWith(START_MULTILINE) ?: false
	}

	private fun isQuoted(): Boolean {
		val value = get() ?: return false
		return value.startsWith(QUOTE)
				&& value.endsWith(QUOTE)
	}

	fun getDecoded(): String? {
		val value = get() ?: return null
		val content = CONTENT_REGEX
			.findAll(value)
			.filter { matchResult -> matchResult.value.isNotBlank() }
			.map { matchResult -> matchResult.value }
			.joinToString(separator = "")
		return decodeBase64(content)
	}

	fun getDecodedBytes(): ByteArray? {
		return decodeBase64ToBytes(get())
	}

	fun getStartOffset(): Int? {
		return com.redhat.devtools.intellij.kubernetes.editor.util.getStartOffset(element)
	}
}