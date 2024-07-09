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

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getChildren
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.ARGS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.COMMAND
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.CONTAINERS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.CONTAINER_ID
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.IMAGE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.IMAGE_ID
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.INIT_CONTAINERS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.MOUNTS
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.model.mocks.PodContainer.podWithContainer
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerDescriberTest {

	@Test
	fun `should NOT describe init containers if there are none`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val initContainers = getParagraph<NamedValue>(INIT_CONTAINERS, description)
		assertThat(initContainers).isNull()
	}

	@Test
	fun `should describe containers with NONE if there are none`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(CONTAINERS, description)?.value)
			.isEqualTo(NONE)
	}

	@Test
	fun `should describe container name`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withContainers(ContainerBuilder()
					.withName("leia")
					.build()
				)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getChildren<Chapter, Chapter>(CONTAINERS, description)?.first()?.title)
			.isEqualTo("leia")
	}

	@Test
	fun `should describe container ID`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withContainerID("planet of the republic")
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", CONTAINER_ID), description)?.value)
			.isEqualTo("planet of the republic")
	}

	@Test
	fun `should NOT describe container ID if there's none`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val containerId = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", CONTAINER_ID), description)
		assertThat(containerId).isNull()
	}

	@Test
	fun `should describe image`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withContainers(ContainerBuilder()
					.withName("leia")
					.withImage("princess")
					.build()
				)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", IMAGE), description)?.value)
			.isEqualTo("princess")
	}

	@Test
	fun `should NOT describe image if there's none`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", IMAGE), description))
			.isNull()
	}

	@Test
	fun `should describe image ID`() {
		// given
		val pod = podWithContainer(
			ContainerStatusBuilder()
				.withName("leia")
				.withImageID("alderaan")
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", IMAGE_ID), description)?.value)
			.isEqualTo("alderaan")
	}

	@Test
	fun `should NOT describe image ID if there's none`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", IMAGE_ID), description))
			.isNull()
	}

	@Test
	fun `should describe command`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withContainers(ContainerBuilder()
					.withName("leia")
					.withCommand("x-wings", "engage", "the", "death star")
					.build()
				)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", COMMAND), description)?.value)
			.isEqualTo("x-wings\nengage\nthe\ndeath star")
	}

	@Test
	fun `should NOT describe command if none is provided`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(ContainerBuilder()
				.withName("leia")
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", COMMAND), description))
			.isNull()

	}

	@Test
	fun `should describe args`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(ContainerBuilder()
				.withName("leia")
				.withArgs("argues", "a lot", "with", "Han Solo")
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", ARGS), description)?.value)
			.isEqualTo("argues\na lot\nwith\nHan Solo")
	}

	@Test
	fun `should NOT describe args if none are provided`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(ContainerBuilder()
				.withName("leia")
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", ARGS), description))
			.isNull()
	}

	@Test
	fun `should describe mounts with NONE if there are none`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedSequence>(listOf(CONTAINERS, "leia", MOUNTS), description))
			.isNull()
	}

	@Test
	fun `should describe mounts`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.withVolumeMounts(
				VolumeMountBuilder()
					.withName("Alderaan")
					.withMountPath("Cruiser Tantive IV")
					.withSubPath("Captured over Tatooine")
					.withReadOnly(true)
					.build(),
				VolumeMountBuilder()
					.withName("Geonosis orbit")
					.withMountPath("Death star")
					.withReadOnly(false)
					.build()
			)
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedSequence>(listOf(CONTAINERS, "leia", MOUNTS), description)?.children)
			.containsExactly(
				"Cruiser Tantive IV from Alderaan (ro, path = \"Captured over Tatooine\")",
				"Death star from Geonosis orbit (rw)"
			)
	}

}