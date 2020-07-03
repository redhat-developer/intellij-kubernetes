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
package org.jboss.tools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.openshift.api.model.DeploymentConfig
import java.util.function.Predicate

class ReplicationControllerFor(private val dc: DeploymentConfig) : Predicate<ReplicationController> {

	private val deploymentConfigAnnotation = "openshift.io/deployment-config.name"

	override fun test(rc: ReplicationController): Boolean {
		return dc.metadata.name == rc.metadata.annotations[deploymentConfigAnnotation]
	}
}

class DeploymentConfigFor(dc: ReplicationController) : Predicate<DeploymentConfig> {

	private val deploymentConfigAnnotation = "openshift.io/deployment-config.name"
	private val dcName: String? = dc.metadata.annotations[deploymentConfigAnnotation]

	override fun test(dc: DeploymentConfig): Boolean {
		return dcName != null
				&& dcName == dc.metadata.annotations[deploymentConfigAnnotation]
	}
}

class PodForService(service: Service)
	: PodForResource<Pod>(service.spec.selector)

class PodForDeployment(deployment: Deployment)
	: PodForResource<Pod>(deployment.spec.selector.matchLabels)

class PodForStatefulSet(statefulSet: StatefulSet)
	: PodForResource<Pod>(statefulSet.spec.selector.matchLabels)

open class PodForResource<R: HasMetadata>(private val selectorLabels: Map<String, String>): Predicate<Pod> {

	override fun test(pod: Pod): Boolean {
		return selectorLabels.all { pod.metadata.labels.entries.contains(it) }
	}
}

class PodForDaemonSet(private val resource: DaemonSet) : Predicate<Pod> {

	override fun test(pod: Pod): Boolean {
		return resource.spec.selector.matchLabels.all { pod.metadata.labels.entries.contains(it) }
	}
}
