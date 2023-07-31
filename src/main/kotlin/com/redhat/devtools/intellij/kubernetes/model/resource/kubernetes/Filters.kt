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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.Job
import java.util.function.Predicate


class PodForService(service: Service)
	: PodForResource(service.spec.selector)

class PodForDeployment(deployment: Deployment)
	: PodForResource(deployment.spec.selector.matchLabels)

class PodForStatefulSet(statefulSet: StatefulSet)
	: PodForResource(statefulSet.spec.selector.matchLabels)

class PodForDaemonSet(daemonSet: DaemonSet)
	: PodForResource(daemonSet.spec.selector?.matchLabels)

class PodForReplicaSet(replicaSet: ReplicaSet)
	: PodForResource(replicaSet.spec.selector.matchLabels)

class PodForReplicationController(replicationController: ReplicationController)
	: PodForResource(replicationController.spec.selector)

open class PodForResource(private val selectorLabels: Map<String, String>?): Predicate<Pod> {

	override fun test(pod: Pod): Boolean {
		return selectorLabels?.all {
			pod.metadata.labels?.entries?.contains(it) ?: false
		} ?: false
	}
}

class PodForJob(private val job: Job) : Predicate<Pod> {

	override fun test(pod: Pod): Boolean {
		return job.metadata.uid == pod.metadata?.labels?.get("controller-uid")
	}
}

class DeploymentForPod(pod: Pod) : ResourceForPod<Deployment>(pod) {

	override fun getSelectorLabels(resource: Deployment): Map<String, String> {
		return resource.spec?.selector?.matchLabels ?: emptyMap()
	}

}

class ReplicaSetForPod(pod: Pod) : ResourceForPod<ReplicaSet>(pod) {

	override fun getSelectorLabels(resource: ReplicaSet): Map<String, String> {
		return resource.spec?.selector?.matchLabels ?: emptyMap()
	}
}

class StatefulSetForPod(pod: Pod) : ResourceForPod<StatefulSet>(pod) {

	override fun getSelectorLabels(resource: StatefulSet): Map<String, String> {
		return resource.spec?.selector?.matchLabels ?: emptyMap()
	}
}

class ReplicationControllerForPod(pod: Pod) : ResourceForPod<ReplicationController>(pod) {

	override fun getSelectorLabels(resource: ReplicationController): Map<String, String> {
		return resource.spec?.selector ?: emptyMap()
	}
}

abstract class ResourceForPod<R: HasMetadata>(private val pod: Pod) : Predicate<R> {

	override fun test(resource: R): Boolean {
		val selectorLabels = getSelectorLabels(resource)
		return if (selectorLabels.isEmpty()) {
			false
		} else {
			selectorLabels.all {
				pod.metadata.labels?.entries?.contains(it) ?: false
			}
		}
	}

	abstract fun getSelectorLabels(resource: R): Map<String, String>
}
