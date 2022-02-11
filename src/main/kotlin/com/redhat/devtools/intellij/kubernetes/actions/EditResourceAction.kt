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
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.hasDeletionTimestamp
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.TreePath

class EditResourceAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val descriptor = selected?.get(0)?.getDescriptor() ?: return
        val project = descriptor.project ?: return
        val toEdit: HasMetadata = descriptor.element as? HasMetadata? ?: return
        try {
            ResourceEditorFactory.instance.openEditor(toEdit, project)
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
    }
}