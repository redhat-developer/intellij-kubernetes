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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.inNamespace
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.items
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.list
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.pods
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.resource.WatchableListableDeletable
import org.junit.Before
import org.junit.Test
import java.util.function.Supplier

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
        provider.allResources
        // when
        provider.allResources
        // then
        verify(provider, times(1)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() wont return cached but load pods if #invalidate() is called`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        provider.namespace =  namespace
        provider.allResources
        verify(provider, times(1)).loadAllResources(namespace)
        provider.invalidate()
        // when
        provider.allResources
        // then
        verify(provider, times(2)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() won't load resources if namespace is null`() {
        // given
        provider.namespace =  null
        clearInvocations(provider)
        // when
        provider.allResources
        // then
        verify(provider, never()).loadAllResources(any())
    }

    @Test
    fun `#getOperation() won't return operations if namespace is null`() {
        // given
        provider.namespace =  null
        clearInvocations(provider)
        // when
        provider.getWatchable()
        // then
        verify(provider, never()).getNamespacedOperation(any())
    }

    @Test
    fun `#delete() deletes given pods in client`() {
        // given
        clearInvocations(provider)
        // when
        val toDelete = listOf(POD2)
        provider.delete(toDelete)
        // then
        verify(client.pods().inNamespace(currentNamespace)).delete(toDelete)
    }

    @Test
    fun `#delete() won't delete if namespace is null`() {
        // given
        provider.namespace =  null
        clearInvocations(provider)
        // when
        provider.delete(listOf(POD2))
        // then
        verify(client.pods().inNamespace(currentNamespace), never()).delete(any<List<Pod>>())
    }

    @Test
    fun `#delete() returns true if client could delete`() {
        // given
        clearInvocations(provider)
        whenever(client.pods().inNamespace(currentNamespace).delete(any<List<Pod>>()))
            .thenReturn(true)
        // when
        val success = provider.delete(listOf(POD2))
        // then
        assertThat(success).isTrue()
    }

    @Test
    fun `#delete() returns false if client could NOT delete`() {
        // given
        clearInvocations(provider)
        whenever(client.pods().inNamespace(currentNamespace).delete(any<List<Pod>>()))
            .thenReturn(false)
        // when
        val success = provider.delete(listOf(POD2))
        // then
        assertThat(success).isFalse()
    }

    @Test
    fun `#setNamespace(namespace) sets namespace that's used in #loadAllResources(namespace)`() {
        // given
        val namespace = "darth vader"
        provider.namespace =  namespace
        val namespaceCaptor = argumentCaptor<String>()
        clearInvocations(provider)
        // when
        provider.allResources
        // then
        verify(provider).loadAllResources(namespaceCaptor.capture())
        assertThat(namespaceCaptor.firstValue).isEqualTo(namespace)
    }

    @Test
    fun `#setNamespace(namespace) sets namespace that's used in #getOperation(namespace)`() {
        // given
        val namespace = "darth vader"
        provider.namespace =  namespace
        val namespaceCaptor = argumentCaptor<String>()
        clearInvocations(provider)
        // when
        provider.getWatchable()
        // then
        verify(provider).getNamespacedOperation(namespaceCaptor.capture())
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
    fun `#replace(pod) replaces pod if pod with same uid already exist`() {
        // given
        val uid = POD2.metadata.uid
        val pod = resource<Pod>("lord vader", "sith", uid)
        assertThat(provider.allResources).doesNotContain(pod)
        // when
        val replaced = provider.replace(pod)
        // then
        assertThat(replaced).isTrue()
        assertThat(provider.allResources).contains(pod)
    }

    @Test
    fun `#replace(pod) does NOT replace pod if pod has different name`() {
        // given
        val namespace = POD2.metadata.namespace
        val pod = resource<Pod>("darth vader", namespace)
        assertThat(provider.allResources).doesNotContain(pod)
        // when
        val replaced = provider.replace(pod)
        // then
        assertThat(replaced).isFalse()
        assertThat(provider.allResources).doesNotContain(pod)
    }

    @Test
    fun `#replace(pod) does NOT replace pod if pod has different namespace`() {
        // given
        val name = POD2.metadata.name
        val pod = resource<Pod>(name, "sith")
        assertThat(provider.allResources).doesNotContain(pod)
        // when
        val replaced = provider.replace(pod)
        // then
        assertThat(replaced).isFalse()
        assertThat(provider.allResources).doesNotContain(pod)
    }

    @Test
    fun `#add(pod) adds pod if not contained yet`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(provider.allResources).doesNotContain(pod)
        // when
        provider.add(pod)
        // then
        assertThat(provider.allResources).contains(pod)
    }

    @Test
    fun `#add(pod) does not add if pod is already contained`() {
        // given
        val pod = provider.allResources.elementAt(0)
        // when
        val size = provider.allResources.size
        provider.add(pod)
        // then
        assertThat(provider.allResources).contains(pod)
        assertThat(provider.allResources.size).isEqualTo(size)
    }

    @Test
    fun `#add(pod) is replacing if different instance of same pod is already contained`() {
        // given
        val instance1 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3")
        val instance2 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3")
        provider.add(instance1)
        assertThat(provider.allResources).contains(instance1)
        // when
        provider.add(instance2)
        // then
        assertThat(provider.allResources).doesNotContain(instance1)
        assertThat(provider.allResources).contains(instance2)
    }

    @Test
    fun `#add(pod) returns true if pod was added`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(provider.allResources).doesNotContain(pod)
        // when
        val added = provider.add(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#add(pod) returns false if pod was not added`() {
        // given
        val pod = provider.allResources.elementAt(0)
        // when
        val added = provider.add(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#remove(pod) removes the given pod`() {
        // given
        val pod = provider.allResources.elementAt(0)
        // when
        provider.remove(pod)
        // then
        assertThat(provider.allResources).doesNotContain(pod)
    }

    @Test
    fun `#remove(pod) removes the given pod if it isn't the same instance but matches in uid`() {
        // given
        val pod1 = provider.allResources.elementAt(0)
        val pod2 = resource<Pod>("skywalker", "jedi", pod1.metadata.uid)
        // when
        provider.remove(pod2)
        // then
        assertThat(provider.allResources).doesNotContain(pod1)
    }

    @Test
    fun `#remove(pod) returns true if pod was removed`() {
        // given
        val pod = provider.allResources.elementAt(0)
        // when
        val removed = provider.remove(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#remove(pod) does not remove if pod is not contained`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(provider.allResources).doesNotContain(pod)
        // when
        val size = provider.allResources.size
        provider.remove(pod)
        // then
        assertThat(provider.allResources).doesNotContain(pod)
        assertThat(provider.allResources.size).isEqualTo(size)
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

        public override fun getNamespacedOperation(namespace: String): Supplier<WatchableListableDeletable<Pod>> {
            return super.getNamespacedOperation(namespace)
        }
    }
}
