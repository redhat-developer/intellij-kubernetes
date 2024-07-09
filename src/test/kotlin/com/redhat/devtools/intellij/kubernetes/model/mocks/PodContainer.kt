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
package com.redhat.devtools.intellij.kubernetes.model.mocks

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder

object PodContainer {

	fun podWithContainer(status: ContainerStatus): Pod {
		return PodBuilder()
			.withNewSpec()
				.withContainers(
					ContainerBuilder()
					.withName(status.name)
					.build()
				)
			.endSpec()
			.withNewStatus()
				.withContainerStatuses(status)
			.endStatus()
			.build()
	}

	fun podWithContainer(container: Container): Pod {
		return PodBuilder()
			.withNewSpec()
				.withContainers(container)
			.endSpec()
			.build()
	}
}