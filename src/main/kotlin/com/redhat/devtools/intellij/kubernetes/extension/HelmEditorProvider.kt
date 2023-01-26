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
package com.redhat.devtools.intellij.kubernetes.extension

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.kubernetes.editor.actions.Action
import com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction
import com.redhat.devtools.intellij.kubernetes.editor.notification.CustomizableNotification
import com.redhat.devtools.intellij.kubernetes.model.client.NativeHelm
import com.redhat.devtools.intellij.kubernetes.model.helm.HelmRelease
import io.fabric8.kubernetes.api.model.HasMetadata

@Service
class HelmEditorProvider : CustomizableEditorProvider(HelmEditorProvider::class.java) {
    override val keying: (editor: FileEditor?, project: Project) -> String
        get() = { _, _ -> HelmRelease.FILE_NAME_PREFIX }

    override val acceptable: (editor: FileEditor?, project: Project) -> Boolean
        get() = { editor, _ -> editor?.file?.name?.startsWith(HelmRelease.FILE_NAME_PREFIX) ?: false }
    override val serializer: (res: HasMetadata) -> String
        get() = { NativeHelm.values(it.metadata.name, it.metadata.namespace) }
    override val customizer: (fe: FileEditor, project: Project) -> FileEditor
        get() = { it, project ->
            val helmNotification = CustomizableNotification(it, project) { _, panel ->
                panel.text("Refresh or upgrade helm release")
                panel.createActionLabel("Refresh", CustomizableAction.id.invoke(0))
                panel.createActionLabel("Upgrade", CustomizableAction.id.invoke(1))
                panel
            }
            CustomizableAction.register(it, { re, _, _, _, modified, _ ->
                when {
                    modified -> re.runInUI { helmNotification.show(arrayOf()) }
                    else -> re.runInUI { helmNotification.hide() }
                }
            }) {
                listOf(
                    Action(
                        "Refresh",
                        "refresh",
                        AllIcons.Actions.ForceRefresh,
                        "Refresh current helm release",
                        "Unable to refresh values",
                        { e -> "Exception on refresh values ${e.message}" },
                        { fe, re, _ ->
                            re.runWriteCommand {
                                val parts = fe.file?.name?.split("@") ?: listOf()
                                // helmrelease@namespace@name@...
                                if (parts.size >= 3) {
                                    re.replaceDocument(NativeHelm.values(parts[2], parts[1]))
                                }
                            }
                        }),
                    Action(
                        "Upgrade",
                        "upgrade",
                        AllIcons.Actions.Upload,
                        "Update current helm release",
                        "Unable to upgrade helm release",
                        { e -> "Exception on upgrading release: ${e.message}" },
                        { fe, re, _ ->
                            val parts = fe.file?.name?.split("@") ?: listOf()
                            if (parts.size >= 3) {
                                val hr = HelmRelease.from(parts[2], parts[1])
                                re.runReadCommand {
                                    NativeHelm.upgrade(hr, fe.file!!.contentsToByteArray())
                                }
                            }
                        })
                )
            }
            it
        }

    override fun getImplementationClassName(): String =
        "com.redhat.devtools.intellij.kubernetes.extension .HelmEditorProvider"
}