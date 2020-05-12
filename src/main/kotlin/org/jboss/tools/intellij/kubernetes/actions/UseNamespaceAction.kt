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
package org.jboss.tools.intellij.kubernetes.actions

import com.intellij.icons.AllIcons.Actions.Refresh
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace

class UseNamespaceAction: AnAction(Refresh) {

    override fun actionPerformed(event: AnActionEvent) {
        val selectedNode = event.getTree().getSelectedNode()
        val element = selectedNode?.getDescriptor()?.element
        if (element is Namespace) {
            getResourceModel(event.project)?.setCurrentNamespace(element.metadata.name)
        }
    }
}