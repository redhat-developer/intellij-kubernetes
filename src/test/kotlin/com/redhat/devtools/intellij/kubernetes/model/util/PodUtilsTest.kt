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
package com.redhat.devtools.intellij.kubernetes.model.util

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.PodMockBuilder
import com.redhat.devtools.intellij.kubernetes.model.mocks.podStatus
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.PHASE_FAILED
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.PHASE_PENDING
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.PHASE_RUNNING
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.PHASE_SUCCEEDED
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.isTerminating
import io.fabric8.kubernetes.api.model.Pod
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class PodUtilsTest {

    private var pod: Pod? = null

    @Before
    fun before() {
        this.pod = resource<Pod>("luke", "jedis", "42", "v1", "1")
        val metadata = pod?.metadata
        doReturn("2024-07-15T14:59:19Z")
            .whenever(metadata)?.deletionTimestamp
    }

    @Test
    fun `Pod#isTerminating() should return false if pod has no deletionTimestamp`() {
        // given
        val pod = pod()
        PodMockBuilder(pod)
            .deletionTimestamp("42 of September 4242")
        // when
        val terminating = pod.isTerminating()
        // then
        assertThat(terminating).isFalse()
    }

    @Test
    fun `Pod#isTerminating() should return false if pod has deletionTimestamp but is in SUCCEEDED phase`() {
        // given
        val pod = pod()
        PodMockBuilder(pod)
            .status(podStatus(phase = PHASE_SUCCEEDED))
        // when
        val terminating = pod.isTerminating()
        // then
        assertThat(terminating).isFalse()
    }

    @Test
    fun `Pod#isTerminating() should return false if pod has deletionTimestamp but is in FAILED phase`() {
        // given
        val pod = pod()
        PodMockBuilder(pod)
            .status(podStatus(phase = PHASE_FAILED))
        // when
        val terminating = pod.isTerminating()
        // then
        assertThat(terminating).isFalse()
    }

    @Test
    fun `Pod#isTerminating() should return true if pod has deletionTimestamp and is in RUNNING phase`() {
        // given
        val pod = pod()
        PodMockBuilder(pod)
            .status(podStatus(phase = PHASE_RUNNING))
        // when
        val terminating = pod.isTerminating()
        // then
        assertThat(terminating).isTrue()
    }

    @Test
    fun `Pod#isTerminating() should return true if pod has deletionTimestamp and is in PENDING phase`() {
        // given
        val pod = pod()
        PodMockBuilder(pod)
            .status(podStatus(phase = PHASE_PENDING))
        // when
        val terminating = pod.isTerminating()
        // then
        assertThat(terminating).isTrue()
    }

    private fun pod(): Pod {
        return pod ?: throw RuntimeException("pod not initialized")
    }

}