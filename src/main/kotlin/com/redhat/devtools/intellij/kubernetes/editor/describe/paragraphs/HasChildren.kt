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

open class HasChildren<T> protected constructor(title: String, children: List<T> = emptyList()): Paragraph(title) {

	val children = mutableListOf<T>()

	init {
		this.children.addAll(children)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is HasChildren<*>) return false
		if (!super.equals(other)) return false

		if (children != other.children) return false

		return true
	}

	override fun hashCode(): Int {
		var result = super.hashCode()
		result = 31 * result + children.hashCode()
		return result
	}
}