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
package com.redhat.devtools.intellij.kubernetes.model.resource.openshift

import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.NonCachingSingleResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig

class OpenShiftReplicas(resourceOperator: NonCachingSingleResourceOperator, getAllResources: ResourcesRetrieval)
    : KubernetesReplicas(resourceOperator, getAllResources) {

    override fun get(resource: HasMetadata): Replicator? {
        return when (resource) {
            is DeploymentConfig ->
                OpenShiftReplicator(resource)

            is ReplicationController ->
                getReplicator(resource)

            is Pod ->
                getReplicator(resource)

            else ->
                super.get(resource)
        }
    }

    private fun getReplicator(replicationController: ReplicationController): Replicator? {
        val deploymentConfig = getDeploymentConfig(replicationController)
        return if (deploymentConfig != null) {
            OpenShiftReplicator(deploymentConfig)
        } else {
            OpenShiftReplicator(replicationController)
        }
    }

    override fun getReplicator(pod: Pod): Replicator? {
        val deploymentConfig = getDeploymentConfig(pod)
        return if (deploymentConfig != null) {
            OpenShiftReplicator(deploymentConfig)
        } else {
            super.getReplicator(pod)
        }
    }

    private fun getDeploymentConfig(pod: Pod): DeploymentConfig? {
        return getAllResources
            .getAll(DeploymentConfigsOperator.KIND, IActiveContext.ResourcesIn.CURRENT_NAMESPACE)
            .firstOrNull { deployment -> DeploymentConfigForPod(pod).test(deployment) }
    }

    private fun getDeploymentConfig(replicationController: ReplicationController): DeploymentConfig? {
        return getAllResources.getAll(DeploymentConfigsOperator.KIND, IActiveContext.ResourcesIn.CURRENT_NAMESPACE)
            .firstOrNull { deploymentConfig ->
System.err.println("dc =" +
        "\nannotations: ${deploymentConfig.metadata.annotations.entries.joinToString { it.toString() } }" +
        "\nlabels: ${deploymentConfig.metadata.labels.entries.joinToString { it.toString() } }"
        )
                DeploymentConfigForReplicationController(replicationController).test(deploymentConfig)
            }
    }

    class OpenShiftReplicator(resource: HasMetadata): Replicator(resource) {
        override var replicas: Int?
            get() {
                return when (resource) {
                    is DeploymentConfig ->
                        resource.spec.replicas

                    else ->
                        super.replicas
                }
            }
            set(replicas) {
                when (resource) {
                    is DeploymentConfig ->
                        resource.spec.replicas = replicas

                    else ->
                        super.replicas = replicas
                }
            }

    }

}