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
package com.redhat.devtools.intellij.kubernetes.model.mocks

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import io.fabric8.kubernetes.api.model.ContainerState
import io.fabric8.kubernetes.api.model.ContainerStateRunning
import io.fabric8.kubernetes.api.model.ContainerStateTerminated
import io.fabric8.kubernetes.api.model.ContainerStateWaiting
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodCondition
import io.fabric8.kubernetes.api.model.PodStatus

fun forMock(pod: Pod): PodMockBuilder {
	return PodMockBuilder(pod)
}

fun podStatus(phase: String, reason: String): PodStatus {
	return mock {
		on(mock.phase) doReturn phase
		on(mock.reason) doReturn reason
	}
}

fun podStatus(
		initContainerStatuses: List<ContainerStatus> = emptyList(),
		containerStatuses: List<ContainerStatus> = emptyList(),
		conditions: List<PodCondition> = emptyList(),
		phase: String = ""): PodStatus {
	return mock {
		on(mock.initContainerStatuses) doReturn initContainerStatuses
		on(mock.containerStatuses) doReturn containerStatuses
		on(mock.conditions) doReturn conditions
		on(mock.phase) doReturn phase
	}
}

fun condition(type: String? = null, status: String? = null): PodCondition {
	return mock {
		on(mock.type) doReturn type
		on(mock.status) doReturn status
	}
}

fun containerStatus(name: String, ready: Boolean = false, state: ContainerState? = null): ContainerStatus {
	return mock {
		on(mock.name) doReturn name
		on(mock.ready) doReturn ready
		on(mock.state) doReturn state
	}
}

fun containerState(terminated: ContainerStateTerminated? = null, waiting: ContainerStateWaiting? = null, running: ContainerStateRunning? = null)
		: ContainerState {
	return mock {
		on(mock.terminated) doReturn terminated
		on(mock.waiting) doReturn waiting
		on(mock.running) doReturn running
	}
}

fun containerStateTerminated(exitCode: Int? = null, reason: String? = null): ContainerStateTerminated {
	return mock {
		on(mock.exitCode) doReturn exitCode
		on(mock.reason) doReturn reason
	}
}

fun containerStateWaiting(reason: String? = null): ContainerStateWaiting {
	return mock {
		on(mock.reason) doReturn reason
	}
}

fun containerStateRunning(): ContainerStateRunning {
	return mock()
}

class PodMockBuilder(private val pod: Pod) {

	/**
	 * Sets the given pod to initializing. This is achieved by mocking an initContainerStatus that is terminated with an exit code that's not 0
	 */
	fun setInitializing(): PodMockBuilder {
		status(podStatus(
				initContainerStatuses = listOf(
						containerStatus(
							pod.metadata.name,
							state = containerState(
								containerStateTerminated(42))))))
		return this
	}

	fun status(status: PodStatus): PodMockBuilder {
		whenever(pod.status)
				.thenReturn(status)
		return this
	}

	fun deletionTimestamp(timestamp: String): PodMockBuilder {
		whenever(pod.metadata.deletionTimestamp)
				.thenReturn(timestamp)
		return this
	}
}