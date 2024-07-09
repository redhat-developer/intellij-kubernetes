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

class NamedSequence(title: String, children: List<Any> = emptyList()): HasChildren<Any>(title, children) {

	fun addIfExists(value: Any?): NamedSequence {
		if (value == null
			|| (value is String && value.isBlank())) {
			return this
		}
		children.add(value)
		return this
	}

	fun addIfExists(values: List<Any?>?): NamedSequence {
		if (values.isNullOrEmpty()) {
			return this
		}
		values.forEach { value -> addIfExists(value) }
		return this
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is NamedSequence) return false
		if (!super.equals(other)) return false
		return true
	}
}