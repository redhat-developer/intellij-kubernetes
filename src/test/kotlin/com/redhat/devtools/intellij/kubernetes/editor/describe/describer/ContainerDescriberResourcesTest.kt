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
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.LIMITS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.ContainersDescriber.Labels.REQUESTS
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getChildren
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.toMap
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.HasChildren
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph
import com.redhat.devtools.intellij.kubernetes.model.mocks.PodContainer.podWithContainer
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirements
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerDescriberResourcesTest {

	@Test
	fun `should NOT describe limits if none are provided`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
				.withName("leia")
				.withResources(ResourceRequirements())
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", LIMITS), description)?.value)
			.isNull()
	}

	@Test
	fun `should describe limits`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
				.withName("R2-D2")
				.withResources(
					ResourceRequirements(
					emptyList(),
					mapOf(
						"cpu" to Quantity("500", "m"),
						"ephemeral-storage" to Quantity("2", "Gi"),
						"memory" to Quantity("128", "Mi")
					),
					emptyMap()
				)
				)
				.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val r2d2 = getParagraph<HasChildren<Paragraph>>(listOf(CONTAINERS, "R2-D2"), description)
		val limits = getChildren<Chapter, NamedValue>(LIMITS, r2d2)
		assertThat(toMap(limits)).containsExactlyEntriesOf(
			mapOf(
				"cpu" to "500m",
				"ephemeral-storage" to "2Gi",
				"memory" to "128Mi"
			)
		)
	}

	@Test
	fun `should NOT describe requests if none are provided`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("R2-D2")
			.withResources(ResourceRequirements())
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		assertThat(getParagraph<NamedValue>(listOf(CONTAINERS, "leia", REQUESTS), description)?.value)
			.isNull()
	}

	@Test
	fun `should describe requests`() {
		// given
		val pod = podWithContainer(
			ContainerBuilder()
			.withName("R2-D2")
			.withResources(
				ResourceRequirements(
				emptyList(),
				emptyMap(),
				mapOf(
					"cpu" to Quantity("42", "m"),
					"ephemeral-storage" to Quantity("42", "Gi"),
					"memory" to Quantity("256", "Mi")
				)
			)
			)
			.build()
		)
		// when
		val description = ContainersDescriber(pod).addTo(Chapter(""))
		// then
		val r2d2 = getParagraph<HasChildren<Paragraph>>(listOf(CONTAINERS, "R2-D2"), description)
		val limits = getChildren<Chapter, NamedValue>(REQUESTS, r2d2)
		assertThat(toMap(limits)).containsExactlyEntriesOf(
			mapOf(
				"cpu" to "42m",
				"ephemeral-storage" to "42Gi",
				"memory" to "256Mi"
			)
		)
	}

}