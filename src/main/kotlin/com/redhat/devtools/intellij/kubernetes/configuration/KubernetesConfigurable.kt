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
package com.redhat.devtools.intellij.kubernetes.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import org.jdesktop.swingx.VerticalLayout
import javax.swing.JComponent

class KubernetesConfigurable : ConfigurableEP<KubernetesConfigurable>(), SearchableConfigurable {
    private val com: JBLayeredPane = JBLayeredPane()
    private val nodeShellImage: JBTextField = JBTextField()
    private val nodeShellImagePullSecrets: JBTextField = JBTextField()

    init {
        com.layout = VerticalLayout()
        com.add(JBLabel(TIP_KUBERNETES_NODE_SHELL_IMAGE), position(0, 0))
        com.add(nodeShellImage, position(0, 1))
        com.add(JBLabel(TIP_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS), position(1, 0))
        com.add(nodeShellImagePullSecrets, position(1, 1))
    }

    companion object Static {
        @JvmStatic
        val TIP_KUBERNETES_NODE_SHELL_IMAGE = "Kubernetes node shell image"

        @JvmStatic
        val TIP_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS =
            "Kubernetes node shell image pull secrets(separated by comma if multi)"

        @JvmStatic
        val CONFIGURATION_ID = "redhat.kubernetes"

        fun position(row: Int, column: Int): GridConstraints {
            val gc = GridConstraints()
            gc.row = row
            gc.column = column
            return gc
        }

        fun get(): KubernetesSettings {
            return ApplicationManager.getApplication().getService(KubernetesSettings::class.java)
        }
    }

    override fun createComponent(): JComponent {
        val settings = get()
        nodeShellImage.text = settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE
        nodeShellImagePullSecrets.text = settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS
        return com
    }

    override fun isModified(): Boolean {
        return get().isModified(buildSettings())
    }

    override fun apply() {
        return get().loadState(buildSettings())
    }

    override fun getId(): String {
        return CONFIGURATION_ID
    }

    override fun getDisplayName(): String {
        return "Redhat Kubernetes"
    }

    private fun buildSettings(): KubernetesSettings {
        val settings = KubernetesSettings()
        settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE =
            nodeShellImage.text ?: settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE
        settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS =
            nodeShellImagePullSecrets.text ?: settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS
        return settings
    }
}