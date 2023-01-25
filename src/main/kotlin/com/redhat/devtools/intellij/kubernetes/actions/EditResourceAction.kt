/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorFactory
import com.redhat.devtools.intellij.kubernetes.editor.ResourceFile
import com.redhat.devtools.intellij.kubernetes.editor.actions.Action
import com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction
import com.redhat.devtools.intellij.kubernetes.editor.notification.CustomizableNotification
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.client.NativeHelm
import com.redhat.devtools.intellij.kubernetes.model.helm.HelmRelease
import com.redhat.devtools.intellij.kubernetes.model.util.hasDeletionTimestamp
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.TreePath

class EditResourceAction : StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val descriptor = selected?.get(0)?.getDescriptor() ?: return
        val project = descriptor.project ?: return
        val toEdit: HasMetadata = descriptor.element as? HasMetadata? ?: return
        try {
            if (toEdit.kind == HelmRelease.KIND.kind) {
                ResourceEditorFactory.instance.openEditor(toEdit, project, ".${ResourceFile.EXTENSION}", {
                    NativeHelm.values(it.metadata.name, it.metadata.namespace)
                }, { resource ->
                    "${HelmRelease.FILE_NAME_PREFIX}@${resource.metadata.namespace}@${resource.metadata.name}@"
                }) {
                    val helmNotification = CustomizableNotification(it, project) { _, panel ->
                        panel.text("Refresh or upgrade helm release")
                        panel.createActionLabel("Refresh", CustomizableAction.id.invoke(0))
                        panel.createActionLabel("Upgrade", CustomizableAction.id.invoke(1))
                        panel
                    }
                    CustomizableAction.register(it, { re, _, _, _, modified, _ ->
                        when {
                            modified -> re.runInUI {
                                helmNotification.show(arrayOf())
                            }

                            else -> re.runInUI {
                                helmNotification.hide()
                            }
                        }
                    }) {
                        arrayOf(
                            Action(
                                "Refresh", "refresh", "Unable to refresh values",
                                { e -> "Exception on refresh values ${e.message}" }, { fe, re, _ ->
                                    re.runWriteCommand {
                                        fe.file?.setBinaryContent(
                                            NativeHelm.values(
                                                toEdit.metadata.name,
                                                toEdit.metadata.namespace
                                            ).encodeToByteArray()
                                        )
                                    }
                                }),
                            Action(
                                "Upgrade",
                                "upgrade",
                                "Unable to upgrade helm release",
                                { e -> "Exception on upgrading release ${toEdit.metadata.name}: ${e.message}" },
                                { fe, re, _ ->
                                    if (null != fe.file && toEdit is HelmRelease)
                                        re.runReadCommand {
                                            NativeHelm.upgrade(
                                                toEdit, fe.file!!.contentsToByteArray()
                                            )
                                        }
                                })
                        )
                    }
                    it
                }
            } else {
                ResourceEditorFactory.instance.openEditor(toEdit, project)
            }
        } catch (e: RuntimeException) {
            Notification().error(
                "Error opening editor ${toEdit.metadata.name}",
                "Could not open editor for ${toEdit.kind} '${toEdit.metadata.name}'."
            )
        }
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.size == 1
                && isVisible(selected.firstOrNull())
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        return element != null
                && !hasDeletionTimestamp(element)
                && (HelmRelease.KIND.kind != element.kind || false == (element as? HelmRelease?)?.isHistory)
    }
}