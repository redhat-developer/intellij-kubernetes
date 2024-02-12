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
package com.redhat.devtools.intellij.kubernetes.balloon

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ExpirableRunnable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.redhat.devtools.intellij.kubernetes.createExplanationLabel
import com.redhat.devtools.intellij.kubernetes.insertNewLineAtCaret
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.function.Supplier
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.text.JTextComponent
import kotlin.math.max

class StringInputBalloon(
	private val value: String,
	private val setValue: (String) -> Unit,
	private val editor: Editor
) {

	private var isValid = false

	fun show(event: MouseEvent) {
		val disposable = Disposer.newDisposable()
		val panel = JPanel(BorderLayout())

		val balloon = createBalloon(panel)
		Disposer.register(balloon, disposable)

		val view = TextAreaView(value, balloon, disposable)
		view.addTo(panel)

		balloon.addListener(view.onClosed())
		balloon.show(RelativePoint(event), Balloon.Position.above)

		val focusManager = IdeFocusManager.getInstance(editor.project)
		focusManager.doWhenFocusSettlesDown(view.onFocusSettled(focusManager))
	}

	private fun createBalloon(panel: JPanel): Balloon {
		return JBPopupFactory.getInstance()
			.createBalloonBuilder(panel)
			.setCloseButtonEnabled(true)
			.setBlockClicksThroughBalloon(true)
			.setAnimationCycle(0)
			.setHideOnKeyOutside(true)
			.setHideOnClickOutside(true)
			.setFillColor(panel.background)
			.setHideOnAction(false) // allow user to Ctrl+A & Ctrl+C
			.createBalloon()
	}

	private fun isMultiline(): Boolean {
		return value.contains('\n')
	}

	private fun setValue(balloon: Balloon, textComponent: JTextComponent): Boolean {
		return if (isValid) {
			balloon.hide()
			setValue.invoke(textComponent.text)
			true
		} else {
			false
		}
	}

	private inner class TextAreaView(
		private val value: String,
		private val balloon: Balloon,
		private val disposable: Disposable
	) {
		private val MIN_ROWS = 4
		private val MAX_COLUMNS = 64

		private lateinit var textArea: JTextArea
		private lateinit var applyButton: JButton
		private lateinit var keyListener: KeyListener

		fun addTo(panel: JPanel) {
			val label = JBLabel("Value:")
			label.border = JBUI.Borders.empty(0, 3, 4, 0)
			panel.add(label, BorderLayout.NORTH)
			val textArea = JBTextArea(
				value,
				max(MIN_ROWS, value.length.floorDiv(MAX_COLUMNS) + 1), // textarea has text lines + 1
				MAX_COLUMNS - 1
			)
			textArea.lineWrap = !isMultiline() // have text area line wrap if content is not manually wrapped
			textArea.wrapStyleWord = true
			val scrolled = ScrollPaneFactory.createScrollPane(textArea, true)
			panel.add(scrolled, BorderLayout.CENTER)
			this.keyListener = onKeyPressed(textArea, balloon)
			textArea.addKeyListener(keyListener)
			this.textArea = textArea

			val buttonPanel = JPanel(BorderLayout())
			buttonPanel.border = JBUI.Borders.empty(2, 0, 0, 0)
			panel.add(buttonPanel, BorderLayout.SOUTH)
			buttonPanel.add(
				createExplanationLabel("Shift & Return to insert a new line, Return to apply"),
				BorderLayout.CENTER
			)
			applyButton = JButton("Apply")
			applyButton.addMouseListener(onApply(textArea, balloon))
			buttonPanel.add(applyButton, BorderLayout.EAST)

			addValidation(textArea, disposable)
		}

		fun onFocusSettled(focusManager: IdeFocusManager): ExpirableRunnable {
			return object : ExpirableRunnable {

				override fun run() {
					focusManager.requestFocus(textArea, true)
					textArea.selectAll()
				}

				override fun isExpired(): Boolean {
					return false
				}
			}
		}

		private fun addValidation(textComponent: JTextComponent, disposable: Disposable) {
			ComponentValidator(disposable)
				.withValidator(ValueValidator(textComponent))
				.installOn(textComponent)
				.andRegisterOnDocumentListener(textComponent)
				.revalidate()
		}

		private fun onApply(textComponent: JTextComponent, balloon: Balloon): MouseListener {
			return object : MouseAdapter() {
				override fun mouseClicked(e: MouseEvent?) {
					setValue(balloon, textComponent)
				}
			}
		}

		private fun onKeyPressed(textComponent: JTextComponent, balloon: Balloon): KeyListener {
			return object : KeyAdapter() {
				override fun keyPressed(e: KeyEvent) {
					when {
						KeyEvent.VK_ESCAPE == e.keyCode ->
							balloon.hide()

						KeyEvent.VK_ENTER == e.keyCode
								&& (e.isShiftDown || e.isControlDown) -> {
							insertNewLineAtCaret(textComponent)
						}

						KeyEvent.VK_ENTER == e.keyCode
								&& (!e.isShiftDown && !e.isControlDown) ->
							if (setValue(balloon, textComponent)) {
								e.consume()
							}
					}
				}
			}
		}

		fun onClosed(): JBPopupListener {
			return object : JBPopupListener {
				override fun beforeShown(event: LightweightWindowEvent) {
					// do nothing
				}

				override fun onClosed(event: LightweightWindowEvent) {
					dispose()
				}
			}
		}

		private fun dispose() {
			textArea.removeKeyListener(keyListener)
		}

		private inner class ValueValidator(private val textComponent: JTextComponent) : Supplier<ValidationInfo?> {

			override fun get(): ValidationInfo? {
				if (!textComponent.isEnabled
					|| !textComponent.isVisible
				) {
					return null
				}
				return validate(textComponent.text)
			}

			private fun validate(newValue: String): ValidationInfo? {
				val validation = when {
					StringUtil.isEmptyOrSpaces(newValue) ->
						ValidationInfo("Provide a value", textComponent).asWarning()

					value == newValue ->
						ValidationInfo("Provide new value", textComponent).asWarning()

					else ->
						null
				}
				this@StringInputBalloon.isValid = (validation == null)
				applyButton.setEnabled(validation == null)
				return validation
			}
		}
	}
}
