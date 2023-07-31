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
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.dialogs.ScaleReplicaDialog
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.openshift.api.model.DeploymentConfig
import java.awt.event.MouseEvent
import javax.swing.tree.TreePath

class ScaleReplicaAction: StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val project = getEventProject(event) ?: return
        val toScale = selected?.firstOrNull()?.getElement<HasMetadata>() ?: return
        val model = getResourceModel() ?: return
        try {
            val replicator = model.getReplicas(toScale)
            val replicas = replicator?.replicas
            if (replicator == null
                || replicas == null) {
                Notification().error(
                    "Error Scaling",
                    "Could not scale ${toScale.kind} '${toScale.metadata.name}: unsupported resource"
                )
                return
            }
            val resourceLabel = "${replicator.resource.kind} ${replicator.resource.metadata.name}"
            ScaleReplicaDialog(
                project,
                resourceLabel,
                replicas,
                { replicas: Int -> model.setReplicas(replicas, replicator)},
                (event?.inputEvent as? MouseEvent)?.locationOnScreen
            ).show()
        } catch (e: RuntimeException) {
            Notification().error(
                "Error Scaling",
                "Could not scale ${toScale.kind} '${toScale.metadata.name}': ${toMessage(e)}"
            )
        }
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.size == 1
                && isVisible(selected.firstOrNull())
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        return element is Deployment
                || element is DeploymentConfig
                || element is ReplicationController
                || element is ReplicaSet
                || element is StatefulSet
                || element is Pod
    }
}