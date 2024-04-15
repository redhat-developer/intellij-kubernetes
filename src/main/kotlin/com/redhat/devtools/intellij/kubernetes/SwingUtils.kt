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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.WindowResizeListener
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JRootPane
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

fun setGlassPaneResizable(rootPane: JRootPane, disposable: Disposable?) {
	val resizeListener = WindowResizeListener(rootPane, JBUI.insets(10), null)
	val glassPane = rootPane.glassPane as IdeGlassPaneEx
	glassPane.addMousePreprocessor(resizeListener, disposable!!)
	glassPane.addMouseMotionPreprocessor(resizeListener, disposable)
}

fun setMovable(rootPane: JRootPane, vararg movableComponents: JComponent) {
	val windowMoveListener = WindowMoveListener(rootPane)
	Stream.of(*movableComponents).forEach { component: JComponent ->
		component.addMouseListener(
			windowMoveListener
		)
	}
}

fun setBold(label: JLabel) {
	label.font = JBFont.create(label.font.deriveFont(Font.BOLD))
}

fun registerEscapeShortcut(rootPane: JRootPane, closeFunction: () -> Unit, disposable: Disposable) {
	val escape = ActionManager.getInstance().getAction("EditorEscape")
	DumbAwareAction.create { e: AnActionEvent? -> closeFunction.invoke() }
		.registerCustomShortcutSet(
			escape?.shortcutSet ?: CommonShortcuts.ESCAPE,
			rootPane,
			disposable
		)
}

fun setRootPaneBorders(rootPane: JRootPane) {
	rootPane.border = PopupBorder.Factory.create(true, true)
	rootPane.windowDecorationStyle = JRootPane.NONE
}


