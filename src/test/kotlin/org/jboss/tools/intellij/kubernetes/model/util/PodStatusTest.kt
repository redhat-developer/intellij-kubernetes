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

import io.fabric8.kubernetes.api.model.Pod
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resource
import org.junit.Test
import org.jboss.tools.intellij.kubernetes.model.mocks.*

class PodStatusTest {

	private val pod = resource<Pod>("some pod")
	private val runningReady = containerStatus(
			ready = true,
			state = containerState(
					running = containerStateRunning()))
	private val waitingReady = containerStatus(
			ready = true,
			state = containerState(
					waiting = containerStateWaiting()))
	private val terminatedNonReady = containerStatus(
			ready = false,
			state = containerState(
					terminated = containerStateTerminated()))

	@Test
	fun `#isRunning should return true if pod is in phase "Running"`() {
		// given
		forMock(pod)
				.status(podStatus("Running", "<Darth Vader is silly>"))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isTrue()
	}

	@Test
	fun `#isRunning should return true if pod has reason "Running"`() {
		// given
		forMock(pod)
				.status(podStatus("<transition to the dark side>", "Running"))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isTrue()
	}

	@Test
	fun `#isRunning should return false if pod has deletion timestamp`() {
		// given
		forMock(pod)
				.status(podStatus("", ""))
				.deletion("2020-06-10")
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isFalse()
	}

	@Test
	fun `#isRunning should return false if pod is initializing`() {
		// given
		forMock(pod).setInitializing()
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isFalse()
	}

	@Test
	fun `#isInitializing should return false if pod has no initContainerStatus`() {
		// given
		forMock(pod)
				.status(podStatus(
						initContainerStatuses = emptyList()))
		// when
		val initializing = pod.isInitializing()
		// then
		assertThat(initializing).isFalse()
	}

	@Test
	fun `#isInitializing should return true if pod has initContainerStatus without state`() {
		// given
		forMock(pod)
				.status(podStatus(
						initContainerStatuses = listOf(containerStatus())))
		// when
		val initializing = pod.isInitializing()
		// then
		assertThat(initializing).isTrue()
	}

	@Test
	fun `#isInitializing should return true if pod has initContainerStatus without terminated value nor waiting value`() {
		// given
		forMock(pod)
				.status(podStatus(
					initContainerStatuses = listOf(
							containerStatus(
							state = containerState()))))
		// when
		val initializing = pod.isInitializing()
		// then
		assertThat(initializing).isTrue()
	}

	@Test
	fun `#isInitializing should return true if pod has initContainerStatus without terminated value but waiting value`() {
		// given
		forMock(pod)
				.status(podStatus(
						initContainerStatuses = listOf(
								containerStatus(
										state = containerState(
												waiting = containerStateWaiting("WaitingForTheBus"))))))
		// when
		val initializing = pod.isInitializing()
		// then
		assertThat(initializing).isTrue()
	}

	@Test
	fun `#isInitializing should return false if pod has initContainerStatus without terminated value but waiting value "PodInitializing"`() {
		// given
		forMock(pod)
				.status(podStatus(
						initContainerStatuses = listOf(
								containerStatus(
										state = containerState(
												waiting = containerStateWaiting("PodInitializing"))))))
		// when
		val initializing = pod.isInitializing()
		// then
		assertThat(initializing).isFalse()
	}

	@Test
		fun `#isInitializing should return false if pod has initContainerStatus with state with exit code 0`() {
		// given
		forMock(pod)
				.status(podStatus(
						initContainerStatuses = listOf(
								containerStatus(
										state = containerState(
												containerStateTerminated(0))))))
		// when
		val initializing = pod.isInitializing()
		// then
		assertThat(initializing).isFalse()
	}

	@Test
	fun `#isRunning should return true if pod has containerStatus ready & running`() {
		// given
		forMock(pod)
				.status(podStatus(
						containerStatuses = listOf(runningReady)))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isTrue()
	}

	@Test
	fun `#isRunning should return false if pod has containerStatus ready but not running`() {
		// given
		forMock(pod)
				.status(podStatus(
						containerStatuses = listOf(
								containerStatus(
										ready = true,
										state = containerState()))))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isFalse()
	}

	@Test
	fun `#isRunning should return true if pod has running container, another that's completed and ready condition is true`() {
		// given
		forMock(pod)
				.status(podStatus(
						containerStatuses = listOf(
								runningReady,
								containerStatus(
										ready = false,
										state = containerState(
												terminated = containerStateTerminated(
														reason = "Completed")))),
				conditions = listOf(condition("Ready", "true"))))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isTrue()
	}

	@Test
	fun `#isRunning should return false if pod has running container, another that's completed and ready condition is false`() {
		// given
		forMock(pod)
				.status(podStatus(
						containerStatuses = listOf(
								runningReady,
								containerStatus(
										ready = false,
										state = containerState(
												terminated = containerStateTerminated(
														reason = "Completed")))),
						conditions = listOf(condition("Ready", "false"))))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isFalse()
	}

	@Test
	fun `#isRunning should return true if pod has running container and another that's terminated for other reason than completed`() {
		// given
		forMock(pod)
				.status(podStatus(
						containerStatuses = listOf(
								runningReady,
								containerStatus(
										ready = false,
										state = containerState(
												terminated = containerStateTerminated(
														reason = "I was bored"))))))
		// when
		val running = pod.isRunning()
		// then
		assertThat(running).isTrue()
	}

	@Test
	fun `#getContainers should return all containers`() {
		// given
		forMock(pod)
				.status(podStatus(
						containerStatuses = listOf(
								runningReady,
								waitingReady,
								terminatedNonReady)))
		// when
		val containers = pod.getContainers()
		// then
		assertThat(containers).containsExactlyInAnyOrder(
				runningReady,
				waitingReady,
				terminatedNonReady)
	}
}