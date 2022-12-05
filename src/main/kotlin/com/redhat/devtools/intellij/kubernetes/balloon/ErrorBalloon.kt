/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.balloon

import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.min

object ErrorBalloon {

    private const val MAX_HEIGHT_ERROR_BALLOON = 200

    fun create(message: String?, parent: JComponent): Balloon {
        val requiredWidth = parent.graphics.fontMetrics.getStringBounds(message, parent.graphics).bounds.width
        val width = min(requiredWidth, parent.visibleRect.width) + 30 // safety margin
        val height = getBalloonHeight(width, requiredWidth, parent.graphics.fontMetrics)
        return create(message, width, height)
    }

    private fun create(message: String?, width: Int, height: Int): Balloon {
        val backgroundColor = MessageType.ERROR.popupBackground
        val foregroundColor = MessageType.ERROR.titleForeground
        val text = JEditorPane().apply {
            text = message
            isEditable = false
            background = backgroundColor
            foreground = foregroundColor
            alignmentX = 0.5f
        }
        val scrolled = ScrollPaneFactory.createScrollPane(text, true).apply {
            verticalScrollBar.width
            preferredSize = Dimension(width, height)
            background = backgroundColor
            viewport.background = backgroundColor
            text.caretPosition = 0
        }
        return JBPopupFactory.getInstance().createBalloonBuilder(scrolled)
            .setFillColor(backgroundColor)
            .setBorderColor(backgroundColor)
            .setBorderInsets(JBUI.insets(10))
            .setCloseButtonEnabled(true)
            .setHideOnClickOutside(true)
            .setHideOnAction(false) // allow user to Ctrl+A & Ctrl+C
            .createBalloon()
    }

    /**
     * Determines the height of the error details balloon.
     * The height that is returned is either the strictly required height or the maximum height if it exceeds it.
     *
     * @param availableWidth the width that's available to the balloon
     * @param requiredWidth the width that's required for the balloon
     * @param fontMetrics the font metrics to use
     */
    private fun getBalloonHeight(
        availableWidth: Int,
        requiredWidth: Int,
        fontMetrics: FontMetrics
    ): Int {
        val requiredLines = requiredWidth.toDouble() / availableWidth
        val neededHeight = ceil(requiredLines) * fontMetrics.height
        return min(neededHeight.toInt(), MAX_HEIGHT_ERROR_BALLOON)
    }

    fun showBelow(balloon: Balloon, component: JPanel) {
        val below = RelativePoint(component, Point(component.bounds.width / 2, component.bounds.height))
        balloon.show(below, Balloon.Position.below)
    }

    fun showAbove(balloon: Balloon, component: JComponent) {
        val above = RelativePoint(component, Point(component.bounds.width / 2, 0))
        balloon.show(above, Balloon.Position.above)
    }
}