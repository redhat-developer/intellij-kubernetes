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
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.HOST_PORT
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.HOST_PORTS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.PORT
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.PORTS
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ContainerPortBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerDescriberPortsTest {

	@Test
	fun `should describe port`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(
				ContainerBuilder()
				.withName("leia")
				.withPorts(
					ContainerPortBuilder()
					.withContainerPort(42)
					.withProtocol("Ubese")
					.build())
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", PORT), description)
		assertThat(paragraph?.value).isEqualTo("42/Ubese")
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", PORTS), description)).isNull()
	}

	@Test
	fun `should describe port with NONE if no port is provided`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(
				ContainerBuilder()
				.withName("leia")
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", PORTS), description)).isNull()
		val paragraph = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", PORT), description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe host port`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(
				ContainerBuilder()
				.withName("leia")
				.withPorts(
					ContainerPortBuilder()
						.withHostPort(42)
						.withProtocol("Ubese")
						.build()
				)
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORTS), description)).isNull()
		val paragraph = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORT), description)
		assertThat(paragraph?.value).isEqualTo("42/Ubese")
	}

	@Test
	fun `should describe host port with NONE if no port is provided`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(
				ContainerBuilder()
				.withName("leia")
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORTS), description)).isNull()
		val paragraph = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORT), description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe host port with 0 if no host port is provided`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(
				ContainerBuilder()
				.withName("leia")
				.withPorts(
					ContainerPortBuilder()
						.withHostPort(null) //  no host port
						.withProtocol("Ubese")
						.build()
				)
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORTS), description)).isNull()
		val paragraph = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORT), description)
		assertThat(paragraph?.value).isEqualTo("0/Ubese")
	}

	@Test
	fun `should describe host ports`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withContainers(
				ContainerBuilder()
				.withName("leia")
				.withPorts(
					ContainerPortBuilder()
						.withHostPort(42)
						.withProtocol("Ubese")
						.build(),
					ContainerPortBuilder()
						.withHostPort(84)
						.withProtocol("Droidspeak")
						.build()
				)
				.build()
			)
			.endSpec()
			.build()
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORT), description)).isNull()
		val paragraph = getParagraph<NamedValue>(listOf(CONTAINERS, "leia", HOST_PORTS), description)
		assertThat(paragraph?.value).isEqualTo("42/Ubese, 84/Droidspeak")
	}
}