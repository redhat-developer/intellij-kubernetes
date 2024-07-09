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

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.CONTAINERS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.ENVIRONMENT
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.ENVIRONMENT_VARIABLES_FROM
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.model.mocks.PodContainer.podWithContainer
import io.fabric8.kubernetes.api.model.ConfigMapKeySelectorBuilder
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder
import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder
import io.fabric8.kubernetes.api.model.ResourceFieldSelectorBuilder
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerDescriberEnvironmentTest {

	@Test
	fun `should NOT describe environment variables from if there are none`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedSequence>(listOf(CONTAINERS, "leia", ENVIRONMENT_VARIABLES_FROM), description))
			.isNull()
	}

	@Test
	fun `should describe environment variables from`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.withEnvFrom(
				EnvFromSourceBuilder()
					.withPrefix("pilot of")
					.withNewConfigMapRef()
					.withName("rebel army")
					.withOptional(true)
					.endConfigMapRef()
					.build(),
				EnvFromSourceBuilder()
					.withNewSecretRef()
					.withName("jedi army")
					.withOptional(true)
					.endSecretRef()
					.build()
			)
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedSequence>(listOf(CONTAINERS, "leia", ENVIRONMENT_VARIABLES_FROM), description)?.children)
			.containsExactly(
				"rebel army ConfigMap with prefix \"pilot of\" Optional: true",
				"jedi army Secret Optional: true"
			)
	}

	@Test
	fun `should NOT describe env values if there are none`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedSequence>(listOf(CONTAINERS, "leia", ENVIRONMENT), description))
			.isNull()
	}

	@Test
	fun `should describe env values`() {
		// given
		val pod = podWithContainer(ContainerBuilder()
			.withName("leia")
			.withEnv(
				EnvVarBuilder()
					.withName("gun")
					.withValue("laser")
					.build(),
				EnvVarBuilder()
					.withName("dress")
					.withValue("jumpsuit")
					.build()
			)
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<Chapter>(listOf(CONTAINERS, "leia", ENVIRONMENT), description)?.children)
			.containsExactly(
				NamedValue("gun", "laser"),
				NamedValue("dress", "jumpsuit")
			)
	}

	@Test
	fun `should describe env values from`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("leia")
			.withEnv(
				EnvVarBuilder()
					.withName("blaster")
					.withValueFrom(EnvVarSourceBuilder()
						.withFieldRef(
							ObjectFieldSelectorBuilder()
							.withApiVersion("in belt")
							.withFieldPath("holster")
							.build())
						.build())
					.build(),
				EnvVarBuilder()
					.withName("light saber")
					.withValueFrom(EnvVarSourceBuilder()
						.withResourceFieldRef(
							ResourceFieldSelectorBuilder()
								.withContainerName("attached to belt")
								.withResource("double bladed")
								.build())
						.build())
					.build(),
				EnvVarBuilder()
					.withName("secret")
					.withValueFrom(EnvVarSourceBuilder()
						.withSecretKeyRef(
							SecretKeySelectorBuilder()
								.withKey("twin of")
								.withName("luke skywalker")
								.withOptional(false)
								.build())
						.build())
					.build(),
				EnvVarBuilder()
					.withName("task")
					.withValueFrom(
						EnvVarSourceBuilder()
						.withConfigMapKeyRef(
							ConfigMapKeySelectorBuilder()
								.withKey("restore")
								.withName("the republic")
								.withOptional(true)
								.build())
						.build())
					.build()
			)
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<Chapter>(listOf(CONTAINERS, "leia", ENVIRONMENT), description)?.children)
			.containsExactly(
				NamedValue("blaster", "(in belt:holster)"),
				NamedValue("light saber", "(attached to belt:double bladed)"),
				NamedValue("secret", "<set to the key twin of in secret luke skywalker> Optional: false"),
				NamedValue("task", "<set to the key restore of config map the republic> Optional: true")
			)
	}


}