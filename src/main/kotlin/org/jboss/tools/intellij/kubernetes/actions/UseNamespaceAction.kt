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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import io.fabric8.kubernetes.api.model.Namespace
import org.jboss.tools.intellij.kubernetes.tree.ResourceWatchController
import org.jetbrains.annotations.NotNull
import javax.swing.tree.TreePath

class UseNamespaceAction: StructureTreeAction(Namespace::class.java) {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val namespace: Namespace = selectedNode?.getElement() ?: return
        val model = getResourceModel() ?: return
        ProgressManager.getInstance().run(object :
            Task.Backgroundable(null, "Switching namespace...", true) {

            override fun run(@NotNull progress: ProgressIndicator) {
                try {
                    model.setCurrentNamespace(namespace.metadata.name)
                } catch(e: Exception) {
                    logger<ResourceWatchController>().warn(
                        "Could not use namespace ${namespace.metadata.name}.", e)
                }
            }
        })
    }
}