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
import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.DeploymentsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.StatefulSetsOperator
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.sendTelemetry
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import com.redhat.devtools.intellij.kubernetes.tree.util.getResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.tree.TreePath

class RolloutRestartAction : StructureTreeAction(Any::class.java) {
    companion object Instance {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'XXX")
    }

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val descriptor = selectedNode?.getDescriptor() ?: return
        val model = getResourceModel() ?: return
        val kind = getResourceKind(descriptor.element)
        run("Rollout restarting $selectedNode...", true) {
            val telemetry = TelemetryService.instance.action("restart " + kind?.kind)
            try {
                if (kind == DeploymentsOperator.KIND) {
                    val deployment = selectedNode.getElement<Deployment>()
                    if (null != deployment) {
                        deployment.spec.template.metadata.annotations["kubectl.kubernetes.io/restartedAt"] =
                            df.format(Date())
                        model.getCurrentContext()?.replace(deployment)
                    }
                } else if (kind == StatefulSetsOperator.KIND) {
                    val statefulSet = selectedNode.getElement<StatefulSet>()
                    if (null != statefulSet) {
                        statefulSet.spec.template.metadata.annotations["kubectl.kubernetes.io/restartedAt"] =
                            df.format(Date())
                        model.getCurrentContext()?.replace(statefulSet)
                    }
                } else {
                    logger<ResourceWatchController>().warn("Unsupported operation on $descriptor resources.")
                }
                sendTelemetry(kind, telemetry)
            } catch (e: Exception) {
                logger<ResourceWatchController>().warn("Could not refresh $descriptor resources.", e)
                telemetry.error(e).send()
            }
        }
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.any { isVisible(it) }
            ?: false
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        val kind = getResourceKind(element)
        return element != null && kind != null && (kind == DeploymentsOperator.KIND || kind == StatefulSetsOperator.KIND)
    }
}
