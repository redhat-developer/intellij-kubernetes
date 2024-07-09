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
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.LIVENESS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.READINESS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.STARTUP
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.model.mocks.PodContainer.podWithContainer
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ExecActionBuilder
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder
import io.fabric8.kubernetes.api.model.ProbeBuilder
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerDescriberProbesTest {

	@Test
	fun `should NOT describe liveness if it's not provided`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LIVENESS), description)?.value)
			.isNull()
	}

	@Test
	fun `should describe liveness as unknown if exec, httpGet, tcpSocket, grpc are not defined`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withLivenessProbe(ProbeBuilder().build())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LIVENESS), description)?.value)
			.isEqualTo("unknown delay=0s timeout=0s period=0s #success=0 #failure=0")
	}

	@Test
	fun `should describe exec liveness`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withLivenessProbe(
				ProbeBuilder()
				.withExec(
					ExecActionBuilder()
					.withCommand("turn", "on", "the", "light", "saber")
					.build())
				.withPeriodSeconds(42)
				.withSuccessThreshold(84)
				.build())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LIVENESS), description)?.value)
			.isEqualTo("exec [turn on the light saber] delay=0s timeout=0s period=42s #success=84 #failure=0")
	}

	@Test
	fun `should describe http-get liveness`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withLivenessProbe(
				ProbeBuilder()
				.withHttpGet(
					HTTPGetActionBuilder()
					.withScheme("https")
					.withHost("abafar")
					.withNewPort(42)
					.withPath("void desert")
					.build()
				)
				.withInitialDelaySeconds(12)
				.withPeriodSeconds(42)
				.withFailureThreshold(84)
				.build())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LIVENESS), description)?.value)
			.isEqualTo("http-get https://abafar:42/void desert delay=12s timeout=0s period=42s #success=0 #failure=84")
	}

	@Test
	fun `should describe tcp-socket liveness`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withLivenessProbe(
				ProbeBuilder()
				.withTcpSocket(
					TCPSocketActionBuilder()
					.withHost("abafar")
					.withNewPort(42)
					.build()
				)
				.withTimeoutSeconds(42)
				.withSuccessThreshold(84)
				.build())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LIVENESS), description)?.value)
			.isEqualTo("tcp-socket abafar:42 delay=0s timeout=42s period=0s #success=84 #failure=0")
	}

	@Test
	fun `should describe exec startup probe`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withStartupProbe(
				ProbeBuilder()
				.withExec(
					ExecActionBuilder()
					.withCommand("turn", "on", "the", "light", "saber")
					.build())
				.withInitialDelaySeconds(4222)
				.withTimeoutSeconds(422)
				.withSuccessThreshold(42)
				.build())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", STARTUP), description)?.value)
			.isEqualTo("exec [turn on the light saber] delay=4222s timeout=422s period=0s #success=42 #failure=0")
	}

	@Test
	fun `should describe exec readiness probe`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withReadinessProbe(
				ProbeBuilder()
				.withExec(
					ExecActionBuilder()
					.withCommand("turn", "on", "the", "light", "saber")
					.build())
				.withTimeoutSeconds(42)
				.withSuccessThreshold(1)
				.withFailureThreshold(84)
				.build())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", READINESS), description)?.value)
			.isEqualTo("exec [turn on the light saber] delay=0s timeout=42s period=0s #success=1 #failure=84")
	}

}