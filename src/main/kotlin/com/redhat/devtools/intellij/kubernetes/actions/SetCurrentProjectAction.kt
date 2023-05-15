/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
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
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.dialogs.ResourceNameDialog
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.tree.OpenShiftStructure.ProjectsFolder
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.tree.TreePath
import org.jetbrains.concurrency.runAsync

class SetCurrentProjectAction : StructureTreeAction(false) {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val project = event?.project ?: return
        val model = getResourceModel() ?: return

        openNameDialog(project, model, (event.inputEvent as? MouseEvent)?.locationOnScreen)
    }

    private fun openNameDialog(project: Project, model: IResourceModel, location: Point?) {
        runAsync {
            val projects = loadProjects(model)
            runInEdt {
                val dialog = ResourceNameDialog(project, "Project", projects, onOk(model), location)
                dialog.showAndGet()
            }
        }
    }

    private fun loadProjects(model: IResourceModel) = try {
        model.getCurrentContext()?.getAllResources(ProjectsOperator.KIND, ResourcesIn.NO_NAMESPACE)
    } catch (e: ResourceException) {
        logger<SetCurrentProjectAction>().warn(
            "Could not get all projects.", e
        )
        null
    } ?: emptyList()

    private fun onOk(model: IResourceModel): (projectName: String) -> Unit {
        return { name ->
            run("Setting current project $name...", true) {
                val telemetry = TelemetryService.instance
                    .action(TelemetryService.NAME_PREFIX_NAMESPACE + "switch_by_name")
                try {
                    model.setCurrentNamespace(name)
                    telemetry.success().send()
                } catch (e: Exception) {
                    Notification().error("Could not set current project $name", toMessage(e))
                    logger<SetCurrentProjectAction>().warn(
                        "Could not set current project ${name}.", e
                    )
                    telemetry.error(e).send()
                }
            }
        }
    }

    override fun isVisible(selected: Any?): Boolean {
        return selected?.getElement<ProjectsFolder>() != null
    }
}