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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.CompletableFutureUtils.PLATFORM_EXECUTOR
import com.redhat.devtools.intellij.kubernetes.CompletableFutureUtils.UI_EXECUTOR
import com.redhat.devtools.intellij.kubernetes.dialogs.ResourceNameDialog
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.NamespacesFolder
import io.fabric8.kubernetes.api.model.Namespace
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.tree.TreePath

class SetCurrentNamespaceAction : StructureTreeAction(false) {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val project = event?.project ?: return
        val model = getResourceModel() ?: return

        openNameDialog(project, model, (event.inputEvent as? MouseEvent)?.locationOnScreen)
    }

    private fun openNameDialog(project: Project, model: IResourceModel, location: Point?) {
        CompletableFuture.supplyAsync(
            { loadNamespaces(model) },
            PLATFORM_EXECUTOR
        ).thenApplyAsync (
            { projects ->
                val dialog = ResourceNameDialog(project, "Namespace", projects, onOk(model), location)
                dialog.show()
            },
            UI_EXECUTOR
        )
    }

    private fun loadNamespaces(model: IResourceModel): Collection<Namespace> {
        return try {
            model.getCurrentContext()?.getAllResources(NamespacesOperator.KIND, ResourcesIn.NO_NAMESPACE)
        } catch (e: ResourceException) {
            logger<SetCurrentNamespaceAction>().warn(
                "Could not get all namespaces.", e
            )
            null
        } ?: emptyList()
    }

    private fun onOk(model: IResourceModel): (projectName: String) -> Unit {
        return { name ->
            run("Setting current namespace $name...", true) {
                val telemetry = TelemetryService.instance
                    .action(TelemetryService.NAME_PREFIX_NAMESPACE + "switch_by_name")
                try {
                    model.setCurrentNamespace(name)
                    telemetry.success().send()
                } catch (e: Exception) {
                    Notification().error("Could not set current namespace $name", toMessage(e))
                    logger<SetCurrentNamespaceAction>().warn(
                        "Could not set current namespace ${name}.", e
                    )
                    telemetry.error(e).send()
                }
            }
        }
    }

    override fun isVisible(selected: Any?): Boolean {
        return selected?.getElement<NamespacesFolder>() != null
    }
}