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
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.container
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.containerableResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.setContainers
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.dsl.PodResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class LogWatcherTest {

    private val watcher = LogWatcher<Pod>()
    private val container1 = container("luke-skywalker")
    private val container2 = container("darth-vader")
    private val pod = setContainers(POD2, container1, container2)
    private val op = containerableResource<PodResource<Pod>>()

    @Test
    fun `watch() should use first container if given container is null`() {
        // given
        // when
        watcher.watch(null, pod, mock(), op)
        // then
        verify(op).inContainer(container1.name)
    }

    @Test
    fun `watch() should return null if no container is given and pod has no containers`() {
        // given
        val pod = POD3
        assertThat(pod.spec?.containers).isNullOrEmpty()
        // when
        watcher.watch(null, pod, mock(),op)
        // then
        verify(op, never()).inContainer(any())
    }
}