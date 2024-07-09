/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.container
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.podSpec
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.setContainers
import com.redhat.devtools.intellij.kubernetes.model.mocks.containerStatus
import com.redhat.devtools.intellij.kubernetes.model.mocks.podStatus
import io.fabric8.kubernetes.api.model.batch.v1.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ContainerUtilsTest {

    @Test
    fun `Pod#getFirstContainer() returns 1st container if exists`() {
        // given
        val container1 = container("luke-skywalker")
        val container2 = container("darth-vader")
        val pod = setContainers(POD2, container1, container2)
        // when
        val container = pod.getFirstContainer()
        // then
        assertThat(container).isEqualTo(container1)
    }

    @Test
    fun `Pod#getFirstContainer() should return null if pod has no containers`() {
        // given
        val pod = POD3
        podSpec(pod) // empty pod spec, no containers
        // when
        val container = pod.getFirstContainer()
        // then
        assertThat(container).isNull()
    }

    @Test
    fun `Job#getFirstContainer() returns 1st container if exists`() {
        // given
        val job = resource<Job>("destroy-the-death-star")
        val container1 = container("princess-leia")
        val container2 = container("luke-skywalker")
        val pod = setContainers(job, container1, container2)
        // when
        val container = pod.getFirstContainer()
        // then
        assertThat(container).isEqualTo(container1)
    }

    @Test
    fun `Container#getStatus returns null if podStatus is null`() {
        // given
        val container = container("princess-leia")
        // when
        val containerStatus = container.getStatus(null)
        // then
        assertThat(containerStatus).isNull()
    }

    @Test
    fun `Container#getStatus returns null if there's no container status in podStatus`() {
        // given
        val container = container("princess-leia")
        val podStatus = podStatus(
            initContainerStatuses = emptyList(),
            containerStatuses = emptyList(),
            phase = "waiting for the rebel fleet"
        )
        // when
        val containerStatus = container.getStatus(podStatus)
        // then
        assertThat(containerStatus).isNull()
    }

    @Test
    fun `Container#getStatus returns init container status with the same name`() {
        // given
        val container = container("princess-leia")
        val containerStatus = containerStatus("princess-leia")
        val podStatus = podStatus(
            initContainerStatuses = listOf(containerStatus),
            containerStatuses = emptyList(),
            phase = "waiting for the rebel fleet"
        )
        // when
        val found = container.getStatus(podStatus)
        // then
        assertThat(found).isEqualTo(containerStatus)
    }

    @Test
    fun `Container#getStatus returns container status with the same name`() {
        // given
        val container = container("luke-skywalker")
        val containerStatus = containerStatus("luke-skywalker")
        val podStatus = podStatus(
            initContainerStatuses = emptyList(),
            containerStatuses = listOf(containerStatus),
            phase = "waiting for the rebel fleet"
        )
        // when
        val found = container.getStatus(podStatus)
        // then
        assertThat(found).isEqualTo(containerStatus)
    }

    @Test
    fun `Container#getStatus returns container status with the same name even if init container status exists`() {
        // given
        val container = container("luke-skywalker")
        val containerStatus = containerStatus("luke-skywalker")
        val initContainerStatus = containerStatus("luke-skywalker")
        val podStatus = podStatus(
            initContainerStatuses = listOf(initContainerStatus),
            containerStatuses = listOf(containerStatus),
            phase = "waiting for the rebel fleet"
        )
        // when
        val found = container.getStatus(podStatus)
        // then
        assertThat(found).isEqualTo(containerStatus)
    }
}