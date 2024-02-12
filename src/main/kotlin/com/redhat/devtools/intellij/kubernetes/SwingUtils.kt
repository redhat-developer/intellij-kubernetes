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
package com.redhat.devtools.intellij.kubernetes

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.text.JTextComponent

fun createExplanationLabel(text: String): JBLabel {
	return JBLabel(text).apply {
		componentStyle = UIUtil.ComponentStyle.SMALL
		foreground = JBUI.CurrentTheme.Link.Foreground.DISABLED
	}
}

fun insertNewLineAtCaret(textComponent: JTextComponent) {
	val caretPosition = textComponent.caretPosition
	val newText = StringBuilder(textComponent.text).insert(caretPosition, '\n').toString()
	textComponent.text = newText
	textComponent.caretPosition = caretPosition + 1
}
