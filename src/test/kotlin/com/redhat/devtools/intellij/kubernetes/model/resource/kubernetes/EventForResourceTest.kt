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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.event
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.batch.v1.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class EventForResourceTest {

	private val pod1Event = event("${POD1.metadata.name}-0.17e47e1bbf7010bf", POD1)
	private val pod2Event = event("${POD2.metadata.name}-0.17e47e1bbf75027f", POD2)
	private val pod3Event = event("${POD3.metadata.name}-0.17e47e1c07d2256a", POD3)
	private val namespace1Event = event("${NAMESPACE1.metadata.name}-17e4eca904439043", NAMESPACE1)
	private val namespace2Event = event("${NAMESPACE2.metadata.name}-17e4814ead8405d5", NAMESPACE2)
	private val namespace3Event = event("${NAMESPACE3.metadata.name}-17e4eca905569146", NAMESPACE3)

	private val events = listOf(pod1Event, namespace1Event, namespace2Event, pod2Event, pod3Event, namespace3Event)

	@Test
	fun `#EventForResource returns events that match given resource`() {
		// given
		// when
		val filtered = events.filter { event ->
			EventForResource(POD2).test(event)
		}
		// then
		assertThat(filtered).hasSize(1)
		assertThat(filtered[0]).isEqualTo(pod2Event)
	}

	@Test
	fun `#EventForResource returns no events if given resource has different namespace`() {
		// given
		val pod = PodBuilder(POD2)
			.editMetadata()
				.withNamespace("death-star")
			.endMetadata()
			.build()
		// when
		val filtered = events.filter { event ->
			EventForResource(pod).test(event)
		}
		// then
		assertThat(filtered).isEmpty()
	}

	@Test
	fun `#EventForResource returns no events if given resource has different name`() {
		// given
		val pod = PodBuilder(POD2)
			.editMetadata()
				.withName("leia")
			.endMetadata()
			.build()
		// when
		val filtered = events.filter { event ->
			EventForResource(pod).test(event)
		}
		// then
		assertThat(filtered).isEmpty()
	}

	@Test
	fun `#EventForResource returns no events if given resource has different resource version`() {
		// given
		val pod = PodBuilder(POD2)
			.editMetadata()
				.withResourceVersion("therepublic")
			.endMetadata()
			.build()
		// when
		val filtered = events.filter { event ->
			EventForResource(pod).test(event)
		}
		// then
		assertThat(filtered).isEmpty()
	}

	@Test
	fun `#EventForResource returns no events if given resource has different apiVersion`() {
		// given
		val pod = PodBuilder(POD2)
			.withApiVersion("rebellion")
			.build()
		// when
		val filtered = events.filter { event ->
			EventForResource(pod).test(event)
		}
		// then
		assertThat(filtered).isEmpty()
	}

	@Test
	fun `#EventForResource returns no events if none matches given resource`() {
		// given
		// when
		val filtered = events.filter { event ->
			EventForResource(resource<Job>("luke should use the force")).test(event)
		}
		// then
		assertThat(filtered).isEmpty()
	}

	@Test
	fun `#EventForResourceKind returns no events if none matches given resource`() {
		// given
		// when
		val filtered = events.filter { event ->
			EventForResourceKind(POD2).test(event)
		}
		// then
		val numberOfPodEvents = events.filter { event -> event.involvedObject.kind == POD2.kind }.size
		assertThat(filtered).hasSize(numberOfPodEvents)
	}

}
