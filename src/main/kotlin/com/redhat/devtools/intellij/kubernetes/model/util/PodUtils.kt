/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.SeccompProfile

object PodUtils {

	/*
	* PodPending means the pod has been accepted by the system, but one or more of the containers
	* has not been started. This includes time before being bound to a node, as well as time spent
	* pulling images onto the host.
	*/
	const val PHASE_PENDING = "Pending"

	/*
	 * PodRunning means the pod has been bound to a node and all of the containers have been started.
	 * At least one container is still running or is in the process of being restarted.
	 */
	const val PHASE_RUNNING = "Running"

	/*
	 * PodSucceeded means that all containers in the pod have voluntarily terminated
	 * with a container exit code of 0, and the system is not going to restart any of these containers.
	 */
	const val PHASE_SUCCEEDED = "Succeeded"

	/*
	 * PodFailed means that all containers in the pod have terminated, and at least one container has
	 * terminated in a failure (exited with a non-zero exit code or was stopped by the system).
	 */
	const val PHASE_FAILED = "Failed"

	/*
	 * SeccompProfileTypeUnconfined indicates no seccomp profile is applied (A.K.A. unconfined).
	 */
	const val SECCOMP_PROFILE_UNCONFINED = "Unconfined"
	/*
	 * SeccompProfileTypeRuntimeDefault represents the default container runtime seccomp profile.
	 */
	const val  SECCOMP_PROFILE_RUNTIME_DEFAULT = "RuntimeDefault"
	/*
	 * SeccompProfileTypeLocalhost indicates a profile defined in a file on the node should be used.
	 * The file's location relative to <kubelet-root-dir>/seccomp.
	 */
	const val  SECCOMP_PROFILE_LOCALHOST = "Localhost"

	fun Pod?.isTerminating(): Boolean {
		if (this == null
			|| status == null) {
			return false
		}
		return !metadata?.deletionTimestamp.isNullOrBlank()
				&& status.phase != PHASE_SUCCEEDED
				&& status.phase != PHASE_FAILED
	}

	fun SeccompProfile.isSeccompProfileLocalhost(): Boolean {
		return SECCOMP_PROFILE_LOCALHOST == this.type
	}
}