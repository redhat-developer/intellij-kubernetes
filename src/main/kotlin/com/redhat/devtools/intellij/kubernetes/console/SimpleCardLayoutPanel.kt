/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * based on com.intellij.ui.CardLayoutPanel
 ******************************************************************************/

package com.redhat.devtools.intellij.kubernetes.console

import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBInsets
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

abstract class SimpleCardLayoutPanel<V : JComponent?>(private val cardLayout: CardLayout = CardLayout()) : JPanel(cardLayout), Disposable {

    @Volatile
    protected var isDisposed = false

    private val visibleComponent: Component?
        get() {
            for (component in components) {
                if (component.isVisible) return component
            }
            return null
        }

    fun show(name: String) {
        cardLayout.show(this, name)
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            removeAll()
        }
    }

    override fun doLayout() {
        val bounds = Rectangle(width, height)
        JBInsets.removeFrom(bounds, insets)
        for (component in components) {
            component.bounds = bounds
        }
    }

    override fun getPreferredSize(): Dimension {
        val component = (if (isPreferredSizeSet) null else visibleComponent) ?: return super.getPreferredSize()
        // preferred size of a visible component plus border insets of this panel
        val size = component.preferredSize
        JBInsets.addTo(size, insets) // add border of this panel
        return size
    }

    override fun getMinimumSize(): Dimension {
        val component = (if (isMinimumSizeSet) null else visibleComponent) ?: return super.getMinimumSize()
        // minimum size of a visible component plus border insets of this panel
        val size = component.minimumSize
        JBInsets.addTo(size, insets)
        return size
    }
}