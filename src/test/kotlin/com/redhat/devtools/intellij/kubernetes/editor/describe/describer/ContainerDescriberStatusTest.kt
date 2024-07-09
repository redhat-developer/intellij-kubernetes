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
package com.redhat.devtools.intellij.kubernetes.editor.describe.describer

import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.CONTAINERS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.EXIT_CODE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.FINISHED
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.LAST_STATE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.MESSAGE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.READY
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.REASON
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.RESTART_COUNT
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.RUNNING
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.SIGNAL
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.STARTED
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.STATE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.TERMINATED
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.WAITING
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.model.mocks.PodContainer.podWithContainer
import io.fabric8.kubernetes.api.model.ContainerState
import io.fabric8.kubernetes.api.model.ContainerStateRunningBuilder
import io.fabric8.kubernetes.api.model.ContainerStateTerminatedBuilder
import io.fabric8.kubernetes.api.model.ContainerStateWaitingBuilder
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerDescriberStatusTest {

	@Test
	fun `should NOT describe status if it's not provided`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE), description))
			.isNull()
	}

	@Test
	fun `should describe status with waiting if it's unknown`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withNewStateLike(
					ContainerState() // state that's no running, waiting nor terminated -> unknown
				)
				.endState()
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE), description)?.value)
			.isEqualTo(WAITING)
	}

	@Test
	fun `should describe running state`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withNewState()
				.withRunning(
					ContainerStateRunningBuilder()
						.withStartedAt("2024-07-15T14:59:19Z")
						.build()
				)
				.endState()
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, STATE), description)?.value)
			.isEqualTo(RUNNING)
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, STARTED), description)?.value.toString())
			.startsWith("Mon, 15 Jul 2024 14:59:19") // '+0200' in GMT+2, other value elsewhere
	}

	@Test
	fun `should describe waiting state`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withNewState()
				.withWaiting(
					ContainerStateWaitingBuilder()
						.withReason("death star shield is not lowered yet")
						.build()
				)
				.endState()
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val state = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, STATE), description)
		assertThat(state?.value)
			.isEqualTo(WAITING)
		val started = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, REASON), description)
		assertThat(started?.value)
			.isEqualTo("death star shield is not lowered yet")
	}

	@Test
	fun `should describe terminated state`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withNewState()
				.withTerminated(
					ContainerStateTerminatedBuilder()
						.withReason("death star did not lower shield")
						.withMessage("try later")
						.withExitCode(42)
						.withSignal(84)
						.withStartedAt("2024-06-15T14:59:19Z")
						.withFinishedAt("2024-07-15T14:59:19Z")
						.build()
				)
				.endState()
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, STATE), description)?.value)
			.isEqualTo(TERMINATED)
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, REASON), description)?.value)
			.isEqualTo("death star did not lower shield")
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, MESSAGE), description)?.value)
			.isEqualTo("try later")
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, EXIT_CODE), description)?.value)
			.isEqualTo(42)
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, SIGNAL), description)?.value)
			.isEqualTo(84)
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, STARTED), description)?.value.toString())
			.startsWith("Sat, 15 Jun 2024 14:59:19") // '+0200' in GMT+2, other value elsewhere
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STATE, FINISHED), description)?.value.toString())
			.startsWith("Mon, 15 Jul 2024 14:59:19") // '+0200' in GMT+2, other value elsewhere
	}

	@Test
	fun `should describe running last state`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withNewLastState()
				.withRunning(
					ContainerStateRunningBuilder()
						.withStartedAt("2024-07-15T14:59:19Z")
						.build()
				)
				.endLastState()
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val state = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LAST_STATE, STATE), description)
		assertThat(state?.value)
			.isEqualTo(RUNNING)
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LAST_STATE, STARTED), description)?.value.toString())
			.startsWith("Mon, 15 Jul 2024 14:59:19") // '+0200' in GMT+2, other value elsewhere
	}

	@Test
	fun `should describe ready`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withReady(true)
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", READY), description)?.value)
			.isEqualTo(true)
	}

	@Test
	fun `should describe restart count`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withRestartCount(42)
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", RESTART_COUNT), description)?.value)
			.isEqualTo(42)
	}

}