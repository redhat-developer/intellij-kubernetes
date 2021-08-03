/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc.
 */
package com.redhat.devtools.intellij.kubernetes.settings

import javax.swing.JPanel
import com.intellij.util.ui.FormBuilder
import com.redhat.devtools.intellij.telemetry.ui.preferences.TelemetryPreferencesUtils

class SettingsView {

    val panel: JPanel

    init {
        val builder = FormBuilder.createFormBuilder()
        this.panel = builder
            .addComponent(TelemetryPreferencesUtils.createTelemetryComponent("Kubernetes by Red Hat") { builder.panel }, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}