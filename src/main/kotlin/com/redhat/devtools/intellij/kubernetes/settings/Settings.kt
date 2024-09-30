/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 */
package com.redhat.devtools.intellij.kubernetes.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.redhat.devtools.intellij.kubernetes.settings.Settings.SettingsState

@Service
@State(
    name = "com.redhat.devtools.intellij.kubernetes.Settings",
    storages = [Storage(value = "intellij-kubernetes-settings.xml")]
)
class Settings: SimplePersistentStateComponent<SettingsState>(SettingsState()) {

    companion object {
        const val PROP_EDITOR_SYNC_ENABLED: String = "com.redhat.devtools.intellij.kubernetes.settings.editor.notifications"

        private const val EDITOR_SYNC_ENABLED_DEFAULT = true

        fun getInstance(): Settings {
            return ApplicationManager.getApplication().service()
        }
    }

    fun setEditorSyncEnabled(enabled: Boolean) {
        val wasEnabled = state.editorSyncEnabled
        if (wasEnabled != enabled) {
            state.editorSyncEnabled = enabled
            notifyListeners(PROP_EDITOR_SYNC_ENABLED, enabled.toString())
        }
    }

    fun isEditorSyncEnabled(): Boolean {
        return state.editorSyncEnabled
    }

    private fun notifyListeners(property: String, value: String?) {
        val listener = ApplicationManager.getApplication().messageBus.syncPublisher(SettingsChangeListener.CHANGED)
        listener?.changed(property, value)
    }

    class SettingsState: BaseState() {
        var editorSyncEnabled: Boolean by property(EDITOR_SYNC_ENABLED_DEFAULT)
    }
}