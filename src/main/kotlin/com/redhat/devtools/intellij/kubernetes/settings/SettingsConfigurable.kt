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
package com.redhat.devtools.intellij.kubernetes.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.redhat.devtools.intellij.kubernetes.settings.Settings.Companion.EDITOR_SYNC_ENABLED_DEFAULT
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class SettingsConfigurable: SearchableConfigurable {

    companion object {
        /* plugin.xml > applicationConfigurable > id */
        const val ID: String = "tools.settings.redhat.kubernetes"
    }

    private lateinit var component: SettingsComponent

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = "Red Hat Kubernetes"

    override fun getPreferredFocusedComponent(): JComponent? {
        return component.preferredFocusedComponent
    }

    override fun createComponent(): JComponent? {
        this.component = SettingsComponent(isEditorSyncEnabled())
        return this.component.createPanel()
    }

    override fun isModified(): Boolean {
        return component.isEditorSyncDisabled() != isEditorSyncEnabled()
    }

    override fun apply() {
        val editorNotificationsDisabled = component.isEditorSyncDisabled()
        setEditorSyncEnabled(editorNotificationsDisabled)
    }

    override fun reset() {
        component.setEditorSyncDisabled(isEditorSyncEnabled())
    }

    override fun getId(): String = ID

    private fun isEditorSyncEnabled(): Boolean {
        return Settings.getInstance()?.isEditorSyncEnabled() ?: EDITOR_SYNC_ENABLED_DEFAULT
    }

    private fun setEditorSyncEnabled(enabled: Boolean) {
        Settings.getInstance()?.setEditorSyncEnabled(enabled)
    }

}