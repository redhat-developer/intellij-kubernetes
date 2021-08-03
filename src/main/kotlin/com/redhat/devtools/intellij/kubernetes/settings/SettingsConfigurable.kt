/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc.
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import javax.swing.JComponent

class SettingsConfigurable : Configurable {
    private var view: SettingsView? = null

    override fun getDisplayName(): String {
        return "Kubernetes by Red Hat"
    }

    override fun createComponent(): JComponent? {
        val view = SettingsView()
        this.view = view
        return view.panel
    }

    override fun isModified(): Boolean {
        return false
    }

    @kotlin.jvm.Throws(ConfigurationException::class)
    override fun apply() {
        // no settings yet
    }

    override fun reset() {
        // no settings yet
    }

    override fun disposeUIResources() {
        this.view = null
    }
}