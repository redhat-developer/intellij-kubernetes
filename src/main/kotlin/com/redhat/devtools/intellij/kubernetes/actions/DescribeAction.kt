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
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorFactory
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.client.NativeKubectl
import com.redhat.devtools.intellij.kubernetes.model.helm.HelmRelease
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.TreePath

class DescribeAction : StructureTreeAction(Any::class.java) {
    override fun actionPerformed(anActionEvent: AnActionEvent?, path: TreePath?, selected: Any?) {
        val descriptor = selected?.getDescriptor() ?: return
        val project = descriptor.project ?: return
        val target: HasMetadata = descriptor.element as? HasMetadata? ?: return
        try {
            ResourceEditorFactory.instance.openEditor(target, project, ".txt", {
                NativeKubectl.describe(target)
            })
        } catch (e: Throwable) {
            Notification().error(
                "Error opening editor ${target.metadata.name}",
                "Could not open editor for ${target.kind} '${target.metadata.name}'."
            )
        }
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        if (selected?.size == 1) {
            val element = selected[0].getElement<HasMetadata>()
            return isVisible(element)
        }
        return false
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        return element != null
                && null != element.metadata
                && null != element.kind
                && NativeKubectl.isReady()
                && HelmRelease.KIND.kind != element.kind
    }
}