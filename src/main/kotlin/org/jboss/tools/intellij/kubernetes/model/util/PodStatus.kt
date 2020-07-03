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
package org.jboss.tools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.ContainerState
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.internal.readiness.Readiness

/**
 * Returns {@code true} if the given pod is running. Returns {@code false} otherwise.
 * <p>
 * The definition of when a pod is considered running can be found at https://github.com/openshift/origin/blob/master/vendor/k8s.io/kubernetes/staging/src/k8s.io/api/core/v1/types.go#L2425
 * which states:
 * "PodRunning means the pod has been bound to a node and all of the containers have been started.
 * At least one container is still running or is in the process of being restarted."
 * <p>
 * The logic is taken from <a href="https://github.com/openshift/origin/blob/master/vendor/k8s.io/kubernetes/pkg/printers/internalversion/printers.go#L695-L781">(kubernetes/printers.go) printPod()</a>
 * and <a href="https://github.com/openshift/origin-web-console/blob/master/app/scripts/filters/resources.js#L1012-L1088">(openshift-web-console/resources.js) podStatus()</a>
 *
 * @param pod the pod
 */
fun Pod.isRunning(): Boolean {
	if (this.isInState("Running")) {
		return true
	}
	if (this.hasDeletionTimestamp()
			|| this.isInitializing()) {
		return false
	}
	if (this.hasRunningContainer()) {
		return if (this.hasCompletedContainer()) {
			Readiness.isPodReady(this)
		} else {
			true
		}
	}
	return false
}

private fun Pod.hasDeletionTimestamp(): Boolean {
	return this.metadata.deletionTimestamp != null
}

private fun Pod.isInState(state: String): Boolean {
	return state == this.status?.phase
			|| state == this.status?.reason
}

private fun Pod.hasRunningContainer(): Boolean {
	return this.status.containerStatuses.find { it.isRunning() } != null
}

fun Pod.getContainers(): List<ContainerStatus> {
	return this.status?.containerStatuses ?: emptyList()
}

private fun ContainerStatus.isRunning(): Boolean {
	return this.ready
			&& this.state.running != null
}

/**
 * Returns {@code true} if the given pod has at least 1 container that's initializing
 */
fun Pod.isInitializing(): Boolean {
	return this.status.initContainerStatuses.find { it.isInitializing() } != null
}

/**
 * Returns {@code true} if the given container status is terminated with an non-0 exit code or is waiting.
 * Returns {@code false} otherwise.
 */
private fun ContainerStatus.isInitializing(): Boolean {
	val state = this.state ?: return true
	return when {
		state.isTerminated() ->
			state.hasNonNullExitCode()
		state.isWaiting() ->
			return !state.isWaitingInitializing()
		else ->
			true
	}
}
private fun ContainerState.isTerminated(): Boolean {
	return this.terminated != null
}

private fun ContainerState.hasNonNullExitCode(): Boolean {
	return this.terminated != null
			&& this.terminated.exitCode != 0
}

private fun ContainerState.isWaiting(): Boolean {
	return this.waiting != null
}

private fun ContainerState.isWaitingInitializing(): Boolean {
	return "PodInitializing" == this.waiting?.reason
}

fun Pod.hasCompletedContainer(): Boolean {
	return this.status.containerStatuses.find(::isCompleted) != null
}

private fun isCompleted(container: ContainerStatus): Boolean {
	return "Completed" == container.state?.terminated?.reason
}
