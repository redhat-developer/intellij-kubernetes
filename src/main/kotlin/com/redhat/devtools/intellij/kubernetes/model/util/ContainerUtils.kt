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
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodStatus
import io.fabric8.kubernetes.api.model.batch.v1.Job

fun Pod.getFirstContainer(): Container? {
	return spec?.containers?.firstOrNull()
}

fun Job.getFirstContainer(): Container? {
	return spec?.template?.spec?.containers?.firstOrNull()
}

fun getFirstContainer(resource: HasMetadata): Container? {
	return when(resource) {
		is Pod -> resource.getFirstContainer()
		is Job -> resource.getFirstContainer()
		else -> null
	}

}

fun getStatus(container: Container, podStatus: PodStatus): ContainerStatus? {
	return getStatus(container, podStatus.containerStatuses)
		?: getStatus(container, podStatus.initContainerStatuses)
}

private fun getStatus(container: Container, containerStatus: List<ContainerStatus>): ContainerStatus? {
	return containerStatus.find { status ->
		status.name == container.name
	}
}

fun isRunning(status: ContainerStatus?): Boolean {
	return status?.ready != null
			&& status.state?.running != null
}
