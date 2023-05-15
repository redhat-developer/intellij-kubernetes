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
import com.intellij.util.ui.JBUI
import io.fabric8.kubernetes.api.model.HasMetadata
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class ResourceNameDialog<NAMESPACE: HasMetadata>(
    project: Project,
    private val kind: String,
    existingNamespaces: Collection<NAMESPACE>,
    private val onOk: (name: String) -> Unit,
    private val location: Point?
    ) : DialogWrapper(project, false) {

        companion object {
            private const val HEIGHT = 40
            private const val WIDTH = 300
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
            return JPanel(BorderLayout()).apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                val label = JBLabel("Current $kind:", SwingConstants.LEFT)
                label.border = JBUI.Borders.empty(0, 0, 10, 0)
                add(label)
                add(nameTextField)
                add(Box.createVerticalBox())
            }
        }

        override fun init() {
            title = "Set Current $kind"
            setResizable(false)
            setOKButtonText("Set")
            isModal = false
            setSize(WIDTH, HEIGHT)
            if (location != null) {
                setLocation(location.x, location.y)
            }
            super.init()
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
                ValidationInfo("Name musn't be empty", nameTextField)
            } else {
                null
            }
        }

        override fun doOKAction() {
            super.doOKAction()
            onOk.invoke(nameTextField.text)
        }
    }