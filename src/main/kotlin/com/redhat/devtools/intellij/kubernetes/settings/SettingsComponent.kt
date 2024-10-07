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

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls

class SettingsComponent(editorSyncEnabled: Boolean):  BoundConfigurable("Editor"), SearchableConfigurable {

    private var editorSyncEnabled = AtomicBooleanProperty(editorSyncEnabled)

    override fun createPanel(): DialogPanel {
        return panel {
            row {
                checkBox("Sync editor with cluster")
                    .bindSelected(editorSyncEnabled)
                    .comment("If unchecked, no local or remote changes are notified in the editor.")
            }
        }
    }

    fun setEditorSyncDisabled(enabled: Boolean) {
        editorSyncEnabled.set(enabled)
    }

    fun isEditorSyncDisabled(): Boolean {
        return editorSyncEnabled.get()
    }

    override fun getId(): String {
        return "kubernetes.editor"
    }
}

