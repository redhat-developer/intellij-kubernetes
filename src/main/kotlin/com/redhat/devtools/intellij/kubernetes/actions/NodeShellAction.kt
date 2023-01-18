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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.configuration.KubernetesConfigurable
import com.redhat.devtools.intellij.kubernetes.console.ConsolesToolWindow
import com.redhat.devtools.intellij.kubernetes.console.TerminalTab
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NodesOperator
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import com.redhat.devtools.intellij.kubernetes.tree.util.getResourceKind
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.dsl.internal.core.v1.PodOperationsImpl
import io.fabric8.kubernetes.client.extended.run.RunConfig
import io.fabric8.kubernetes.client.extended.run.RunConfigUtil
import javax.swing.tree.TreePath

class NodeShellAction : StructureTreeAction(Any::class.java) {

    private val client = ClientAdapter.create()

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
        val project = event?.project ?: return
        val descriptor = selectedNode?.getDescriptor() ?: return
        val node = descriptor.getElement<HasMetadata>() ?: return
        val model = getResourceModel() ?: return
        val kind = getResourceKind(descriptor.element)
        run("Starting node shell in $selectedNode...", true) {
            val telemetry = TelemetryService.instance.action("start shell on " + kind?.kind)
            try {
                val settings = KubernetesConfigurable.get()
                val podName = "terminal-" + node.metadata.name
                var pod: Pod? = client.get().pods().inNamespace("default").withName(podName).get()
                val imagePullSecrets = settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS
                val config = RunConfig(
                    "nsenter",
                    settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE,
                    "IfNotPresent",
                    "/nsenter",
                    listOf("--all", "--target=1", "--", "su", "-"),
                    "Never", "", mapOf(), mapOf(), mapOf(), mapOf(), 0
                )
                if (null == pod) {
                    pod = PodBuilder().withMetadata(RunConfigUtil.getObjectMetadataFromRunConfig(config))
                        .withSpec(RunConfigUtil.getPodSpecFromRunConfig(config)).build()
                    pod.metadata.name = podName
                    pod.spec.hostPID = true
                    pod.spec.hostNetwork = true
                    pod.spec.nodeSelector = mapOf(Pair<String, String>("kubernetes.io/hostname", node.metadata.name))
                    pod.spec.containers[0].stdin = true
                    pod.spec.containers[0].tty = true
                    pod.spec.containers[0].securityContext = SecurityContext()
                    pod.spec.containers[0].securityContext.privileged = true
                    if (imagePullSecrets.isNotEmpty()) {
                        pod.spec.imagePullSecrets = imagePullSecrets.split(",").map { LocalObjectReference(it) }
                    }
                    pod = PodOperationsImpl(client.get(), "default").create(pod)
                }
                if (null != pod)
                    createTerminalTabs(listOf(pod), model, project)
                TelemetryService.sendTelemetry(kind, telemetry)
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
        return element != null && kind != null && (kind == NodesOperator.KIND)
    }

    private fun createTerminalTabs(pods: List<Pod>, model: IResourceModel, project: Project) {
        pods.forEach { pod ->
            val tab = TerminalTab(pod, model, project)
            ConsolesToolWindow.add(tab, project)
        }
    }
}