/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.inNamespace
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.items
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.list
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.pods
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.withName
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString

class PodsProviderTest {

    private val client =
        client(NAMESPACE2.metadata.name, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3))
    private val provider = PodsProvider(client, NAMESPACE1)

    @Before
    fun before() {
        val pods = pods(inNamespace(client))
        items(list(pods), POD1, POD2, POD3)
        withName(pods, POD2)
    }

    @Test
    fun `allResources is only loading once, returned cached on every further call`() {
        // given
        provider.getAllResources()
        // when
        provider.getAllResources()
        // then
        verify(client.inNamespace(anyString()).pods().list(), times(1)).items
    }

    @Test
    fun `allResources should reload if invalidate() is called`() {
        // given
        provider.getAllResources()
        verify(client.inNamespace(anyString()).pods().list(), times(1)).items
        provider.invalidate()
        // when
        provider.getAllResources()
        // then
        verify(client.inNamespace(anyString()).pods().list(), times(2)).items
    }

    @Test
    fun `hasResource() loads resources if they're not present yet`() {
        // given
        verify(client.inNamespace(anyString()).pods().list(), never()).items
        // when
        provider.hasResource(POD2)
        // then
        verify(client.inNamespace(anyString()).pods().list(), times(1)).items
    }

    @Test
    fun `hasResource() returns true if queried with contained resource`() {
        // given
        // when
        val hasResource = provider.hasResource(POD2)
        // then
        Assertions.assertThat(hasResource).isTrue()
    }

    @Test
    fun `hasResource() returns false if queried with non-contained resource`() {
        // given
        // when
        val hasResource = provider.hasResource(mock<ReplicationController>())
        // then
        Assertions.assertThat(hasResource).isFalse()
    }

    @Test
    fun `add(pod) adds the given pod`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(provider.getAllResources()).doesNotContain(pod)
        // when
        provider.add(pod)
        // then
        assertThat(provider.getAllResources()).contains(pod)
    }

    @Test
    fun `add(pod) returns true if pod was added`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        // when
        val added = provider.add(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `add(pod) does not add if pod is already contained`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        assertThat(provider.getAllResources()).contains(pod)
        // when
        val size = provider.getAllResources().size
        provider.add(pod)
        // then
        assertThat(provider.getAllResources()).contains(pod)
        assertThat(provider.getAllResources().size).isEqualTo(size)
    }

    @Test
    fun `add(pod) returns false if pod was not added`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        val added = provider.add(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `remove(pod) removes the given pod`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        provider.remove(pod)
        // then
        assertThat(provider.getAllResources()).doesNotContain(pod)
    }

    @Test
    fun `remove(pod) returns true if pod was removed`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        val removed = provider.remove(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `remove(pod) does not remove if pod is not contained`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(provider.getAllResources()).doesNotContain(pod)
        // when
        val size = provider.getAllResources().size
        provider.remove(pod)
        // then
        assertThat(provider.getAllResources()).doesNotContain(pod)
        assertThat(provider.getAllResources().size).isEqualTo(size)
    }

    @Test
    fun `remove(pod) returns false if pod was not removed`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        // when
        val removed = provider.remove(pod)
        // then
        assertThat(removed).isFalse()
    }

}