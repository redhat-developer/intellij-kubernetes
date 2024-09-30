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

import com.intellij.util.messages.Topic

interface SettingsChangeListener {

    companion object {
        @Topic.AppLevel
        val CHANGED = Topic.create("Kubernetes Settings Changed", SettingsChangeListener::class.java)
    }

    fun changed(property: String, value: String?)

}