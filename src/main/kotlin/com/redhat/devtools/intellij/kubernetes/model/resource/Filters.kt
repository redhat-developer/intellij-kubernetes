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
package com.redhat.devtools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.api.model.DeploymentConfig
import java.util.function.Predicate

class ReplicationControllerFor(private val dc: DeploymentConfig) : Predicate<ReplicationController> {

	private val deploymentConfigAnnotation = "openshift.io/deployment-config.name"

	override fun test(rc: ReplicationController): Boolean {
		return dc.metadata.name == rc.metadata.annotations[deploymentConfigAnnotation]
	}
}

class DeploymentConfigFor(rc: ReplicationController) : Predicate<DeploymentConfig> {

	private val dcConfigAnnotation = "openshift.io/deployment-config.name"
	private val dcName: String? = rc.metadata.annotations[dcConfigAnnotation]

	override fun test(dc: DeploymentConfig): Boolean {
		return dcName != null
				&& dcName == dc.metadata.annotations[dcConfigAnnotation]
	}
}

class BuildFor(private val bc: BuildConfig) : Predicate<Build> {

	private val buildConfigLabel = "buildconfig"

	override fun test(build: Build): Boolean {
		return build.metadata.labels[buildConfigLabel] == bc.metadata.name
	}
}

class BuildConfigFor(build: Build) : Predicate<BuildConfig> {

	private val bcKey = "openshift.io/build-config.name"
	private val bcName: String? = build.metadata.annotations[bcKey]
			?: build.metadata.labels[bcKey]

	override fun test(bc: BuildConfig): Boolean {
		return bcName != null
				&& bcName == bc.metadata.name
	}
}

class PodForService(service: Service)
	: PodForResource<Pod>(service.spec.selector)

class PodForDeployment(deployment: Deployment)
	: PodForResource<Pod>(deployment.spec.selector.matchLabels)

class PodForStatefulSet(statefulSet: StatefulSet)
	: PodForResource<Pod>(statefulSet.spec.selector.matchLabels)

open class PodForResource<R: HasMetadata>(private val selectorLabels: Map<String, String>?): Predicate<Pod> {

	override fun test(pod: Pod): Boolean {
		return selectorLabels?.all { pod.metadata.labels?.entries?.contains(it) ?: false } ?: false
	}
}

class PodForDaemonSet(private val resource: DaemonSet) : Predicate<Pod> {

	override fun test(pod: Pod): Boolean {
		return resource.spec.selector?.matchLabels?.all { pod.metadata.labels?.entries?.contains(it)  ?: false  } ?: return false
	}
}
