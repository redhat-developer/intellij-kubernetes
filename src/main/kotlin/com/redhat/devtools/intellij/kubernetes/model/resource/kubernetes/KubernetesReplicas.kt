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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.resource.NonCachingSingleResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet

open class KubernetesReplicas(
    protected val resourceOperator: NonCachingSingleResourceOperator,
    protected val getAllResources: ResourcesRetrieval
) {

    open fun get(resource: HasMetadata): Replicator? {
        return when (resource) {
            is Pod ->
                getReplicator(resource)

            is Deployment,
            is ReplicationController,
            is ReplicaSet,
            is StatefulSet ->
                Replicator(resource)

            else ->
                null
        }
    }

    protected open fun getReplicator(pod: Pod): Replicator? {
        val deployment = getDeployment(pod)
        if (deployment != null) {
            return Replicator(deployment)
        }
        val replicationController = getReplicationController(pod)
        if (replicationController != null) {
            return Replicator(replicationController)
        }
        val replicaSet = getReplicaSet(pod)
        if (replicaSet != null) {
            return Replicator(replicaSet)
        }
        val statefulSet = getStatefulSet(pod)
        if (statefulSet != null) {
            return Replicator(statefulSet)
        }
        return null
    }

    open fun set(replicas: Int, replicator: Replicator) {
        replicator.replicas = replicas
        resourceOperator.replace(replicator.resource)
    }

    private fun getDeployment(pod: Pod): Deployment? {
        return getAllResources.getAll(DeploymentsOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
            .firstOrNull { deployment -> DeploymentForPod(pod).test(deployment) }
    }

    private fun getReplicationController(pod: Pod): ReplicationController? {
        return getAllResources.getAll(ReplicationControllersOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
            .firstOrNull { replicationController -> ReplicationControllerForPod(pod).test(replicationController) }
    }

    private fun getReplicaSet(pod: Pod): ReplicaSet? {
        return getAllResources.getAll(ReplicaSetsOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
            .firstOrNull { replicaSet -> ReplicaSetForPod(pod).test(replicaSet) }
    }

    private fun getStatefulSet(pod: Pod): StatefulSet? {
        return getAllResources.getAll(StatefulSetsOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
            .firstOrNull { replicaSet -> StatefulSetForPod(pod).test(replicaSet) }
    }

    interface ResourcesRetrieval {
        fun <T: HasMetadata> getAll(kind: ResourceKind<T>, resourcesIn: ResourcesIn): Collection<T>
    }

    open class Replicator(val resource: HasMetadata) {

        open var replicas: Int?
            get() {
                return when (resource) {
                    is Deployment ->
                        resource.spec.replicas

                    is ReplicationController ->
                        resource.spec.replicas

                    is ReplicaSet ->
                        resource.spec.replicas

                    is StatefulSet ->
                        resource.spec.replicas

                    else ->
                        null
                }
            }
            set(replicas) {
                when (resource) {
                    is Deployment ->
                        resource.spec.replicas = replicas

                    is ReplicationController ->
                        resource.spec.replicas = replicas

                    is ReplicaSet ->
                        resource.spec.replicas = replicas

                    is StatefulSet ->
                        resource.spec.replicas = replicas
                }
            }
    }
}