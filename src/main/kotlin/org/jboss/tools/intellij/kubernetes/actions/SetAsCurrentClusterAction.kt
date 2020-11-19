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
import com.intellij.openapi.progress.Progressive
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import org.jboss.tools.intellij.kubernetes.tree.ResourceWatchController
import javax.swing.tree.TreePath

class SetAsCurrentClusterAction: StructureTreeAction(IContext::class.java) {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val context: IContext = selectedNode?.getElement() ?: return
        run("Setting $context as current cluster...", true,
            Progressive {
                try {
                    getResourceModel()?.setCurrentContext(context)
                } catch (e: Exception) {
                    logger<ResourceWatchController>().warn(
                        "Could not set current context to ${context.context.name}.", e
                    )
                }
            })
    }
}