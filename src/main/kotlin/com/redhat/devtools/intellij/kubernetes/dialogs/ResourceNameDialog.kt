/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.components.JBLabel
import com.redhat.devtools.intellij.kubernetes.registerEscapeShortcut
import com.redhat.devtools.intellij.kubernetes.setBold
import com.redhat.devtools.intellij.kubernetes.setRootPaneBorders
import com.redhat.devtools.intellij.kubernetes.setGlassPaneResizable
import com.redhat.devtools.intellij.kubernetes.setMovable
import io.fabric8.kubernetes.api.model.HasMetadata
import net.miginfocom.swing.MigLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.RootPaneContainer
import javax.swing.SwingConstants

class ResourceNameDialog<NAMESPACE: HasMetadata>(
    project: Project,
    private val kind: String,
    existingNamespaces: Collection<NAMESPACE>,
    private val onOk: (name: String) -> Unit,
    private val location: Point?
    ) : DialogWrapper(project, false) {

        private val title = JBLabel("Set current $kind").apply {
            setBold(this)
        }

        private val nameTextField = TextFieldWithAutoCompletion(
            project,
            onLookup(existingNamespaces),
            false,
            null
        )

        private fun onLookup(projects: Collection<NAMESPACE>): TextFieldWithAutoCompletionListProvider<NAMESPACE> {
            return object : TextFieldWithAutoCompletionListProvider<NAMESPACE>(projects) {
                override fun getLookupString(item: NAMESPACE): String {
                    return item.metadata.name
                }
            }
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(
                MigLayout("ins 4, gap 4, fillx, filly, hidemode 3")
            ).apply {
                add(title, "gapbottom 10, span 2, wrap")
                val label = JBLabel("Current $kind:", SwingConstants.LEFT)
                add(label)
                add(nameTextField, "growx, pushx, w min:200, wrap")
            }
        }

        override fun init() {
            super.init()
            setUndecorated(true)
            val dialogWindow = peer.window
            val rootPane = (dialogWindow as RootPaneContainer).rootPane
            registerEscapeShortcut(rootPane, ::closeImmediately, myDisposable)
            setRootPaneBorders(rootPane)
            setGlassPaneResizable(peer.rootPane, disposable)
            setMovable(getRootPane(), title)
            isResizable = false
            isModal = false
            if (location != null) {
                setLocation(location.x, location.y)
            }
            setOKButtonText("Set")
        }

        override fun getPreferredFocusedComponent(): JComponent {
            return nameTextField
        }

        override fun show() {
            init()
            super.show()
        }

        override fun doValidate(): ValidationInfo? {
            return if (nameTextField.text.isEmpty()) {
                ValidationInfo("Name mustn't be empty", nameTextField)
            } else {
                null
            }
        }

        override fun doOKAction() {
            super.doOKAction()
            onOk.invoke(nameTextField.text)
        }

        private fun closeImmediately() {
            if (isVisible) {
                doCancelAction()
            }
        }
}