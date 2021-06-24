/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.redhat.devtools.intellij.kubernetes.actions.run
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import com.redhat.devtools.intellij.kubernetes.editor.hideNotification
import com.redhat.devtools.intellij.kubernetes.model.Notification
import javax.swing.JComponent

class ReloadAction(
    private val editor: FileEditor,
    private val project: Project,
    private val panelKey: Key<JComponent>
) : Runnable {

        companion object {
            const val label: String = "Reload from Cluster"
        }

        override fun run() {
            run("Reloading...", true,
                Progressive {
                    try {
                        ResourceEditor.get(editor, project)?.replaceContent()
                        editor.hideNotification(panelKey, project)
                    } catch (e: Exception) {
                        ResourceEditor.get(editor, project)
                        Notification().error("Error Loading", "Could not load resource from cluster: ${e.message}")
                    }
                })

        }
    }