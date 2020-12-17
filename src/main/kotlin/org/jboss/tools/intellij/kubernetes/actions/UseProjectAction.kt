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
import io.fabric8.openshift.api.model.Project
import org.jboss.tools.intellij.kubernetes.tree.ResourceWatchController
import javax.swing.tree.TreePath

class UseProjectAction : UseResourceAction<Project>(Project::class.java) {

	override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
		val project: Project = selectedNode?.getElement() ?: return
		val name = project.metadata.name
		val model = getResourceModel() ?: return
		run("Using project $name...", true,
			Progressive {
				try {
					model.setCurrentNamespace(name)
				} catch (e: Exception) {
					logger<ResourceWatchController>().warn(
						"Could not use namespace $name.", e
					)
				}
			})
	}
}
