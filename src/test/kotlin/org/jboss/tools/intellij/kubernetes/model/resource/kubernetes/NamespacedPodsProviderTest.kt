/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
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
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resource
import org.junit.Before
import org.junit.Test

class NamespacedPodsProviderTest {

    private val currentNamespace = NAMESPACE2.metadata.name
    private val client = client(currentNamespace, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3))
    private val provider = spy(TestablePodsProvider(client))

    @Before
    fun before() {
        items(list(inNamespace(pods(client))), POD1, POD2, POD3)
        provider.namespace = currentNamespace
    }

    @Test
    fun `#getAllResources() returns cached pods, won't load a 2nd time`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        provider.namespace =  namespace
        provider.getAllResources()
        // when
        provider.getAllResources()
        // then
        verify(provider, times(1)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() wont return cached but load pods if #invalidate() is called`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        provider.namespace =  namespace
        provider.getAllResources()
        verify(provider, times(1)).loadAllResources(namespace)
        provider.invalidate()
        // when
        provider.getAllResources()
        // then
        verify(provider, times(2)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() won't load resources if namespace is null`() {
        // given
        provider.namespace =  null
        // when
        provider.getAllResources()
        // then
        verify(provider, never()).loadAllResources(any())
    }

    @Test
    fun `#getRetrieveOperation() won't return operations if namespace is null`() {
        // given
        provider.namespace =  null
        // when
        provider.getWatchable()
        // then
        verify(provider, never()).getLoadOperation(any())
    }

    @Test
    fun `#setNamespace(namespace) sets namespace that's used in #loadAllResources(namespace)`() {
        // given
        val namespace = "darth vader"
        provider.namespace =  namespace
        val namespaceCaptor = argumentCaptor<String>()
        // when
        provider.getAllResources()
        // then
        verify(provider).loadAllResources(namespaceCaptor.capture())
        assertThat(namespaceCaptor.firstValue).isEqualTo(namespace)
    }

    @Test
    fun `#setNamespace(namespace) sets namespace that's used in #getRetrieveOperation(namespace)`() {
        // given
        val namespace = "darth vader"
        provider.namespace =  namespace
        val namespaceCaptor = argumentCaptor<String>()
        // when
        provider.getWatchable()
        // then
        verify(provider).getLoadOperation(namespaceCaptor.capture())
        assertThat(namespaceCaptor.firstValue).isEqualTo(namespace)
    }

    @Test
    fun `#setNamespace(namespace) invalidates cache`() {
        // given
        clearInvocations(provider)
        // when
        provider.namespace =  "skywalker"
        // then
        verify(provider).invalidate()
    }

    @Test
    fun `#add(pod) adds pod if not contained yet`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(provider.getAllResources()).doesNotContain(pod)
        // when
        provider.add(pod)
        // then
        assertThat(provider.getAllResources()).contains(pod)
    }

    @Test
    fun `#add(pod) does not add if pod is already contained`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        val size = provider.getAllResources().size
        provider.add(pod)
        // then
        assertThat(provider.getAllResources()).contains(pod)
        assertThat(provider.getAllResources().size).isEqualTo(size)
    }

    @Test
    fun `#add(pod) returns true if pod was added`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        // when
        val added = provider.add(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#add(pod) returns false if pod was not added`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        val added = provider.add(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#remove(pod) removes the given pod`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        provider.remove(pod)
        // then
        assertThat(provider.getAllResources()).doesNotContain(pod)
    }

    @Test
    fun `#remove(pod) removes the given pod if it isn't the same instance but matches in name and namespace`() {
        // given
        val pod1 = provider.getAllResources().elementAt(0)
        val pod2 = resource<Pod>(pod1.metadata.name, pod1.metadata.namespace)
        // when
        provider.remove(pod2)
        // then
        assertThat(provider.getAllResources()).doesNotContain(pod1)
    }

    @Test
    fun `#remove(pod) returns true if pod was removed`() {
        // given
        val pod = provider.getAllResources().elementAt(0)
        // when
        val removed = provider.remove(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#remove(pod) does not remove if pod is not contained`() {
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
    fun `#remove(pod) returns false if pod was not removed`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        // when
        val removed = provider.remove(pod)
        // then
        assertThat(removed).isFalse()
    }

    class TestablePodsProvider(client: KubernetesClient): NamespacedPodsProvider(client) {

        public override fun loadAllResources(namespace: String): List<Pod> {
            return super.loadAllResources(namespace)
        }

        public override fun getLoadOperation(namespace: String): () -> Watchable<Watch, Watcher<Pod>>? {
            return super.getLoadOperation(namespace)
        }
    }
}