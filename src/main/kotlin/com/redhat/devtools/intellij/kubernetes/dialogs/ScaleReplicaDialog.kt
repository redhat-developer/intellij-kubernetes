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
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class ScaleReplicaDialog(
    project: Project,
    private val resourceLabel: String,
    private val currentReplicas: Int,
    private val onOk: (Int) -> Unit,
    private val location: Point?
    ) : DialogWrapper(project, false) {

        private val replicasSpinner = JBIntSpinner(
            currentReplicas,
            0,
            Int.MAX_VALUE,
            1
        )

        override fun createCenterPanel(): JComponent {
            return JPanel(BorderLayout()).apply {
                layout =  BorderLayout(10, 10)
                add(JBLabel(resourceLabel, SwingConstants.LEFT), BorderLayout.NORTH)
                add(JBLabel("Replicas:", SwingConstants.LEFT), BorderLayout.LINE_START)
                add(replicasSpinner, BorderLayout.CENTER)
            }
        }

        override fun init() {
            title = "Set Replicas"
            setResizable(false)
            setOKButtonText("Scale")
            isModal = false
            if (location != null) {
                setLocation(location.x, location.y)
            }
            super.init()
        }

        override fun getPreferredFocusedComponent(): JComponent {
            return replicasSpinner
        }

        override fun show() {
            init()
            super.show()
        }

        override fun doValidate(): ValidationInfo? {
            return if (replicasSpinner.number == currentReplicas) {
                ValidationInfo("Replicas unchanged", replicasSpinner)
            } else {
                null
            }
        }

        override fun doOKAction() {
            super.doOKAction()
            onOk.invoke(replicasSpinner.number)
        }
    }