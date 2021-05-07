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
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import org.junit.Before
import org.junit.Test

class NamespacedPodsOperatorTest {

    private val currentNamespace = NAMESPACE2.metadata.name
    private val clients = Clients(client(currentNamespace, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)))
    private val operator = spy(TestablePodsOperator(clients))

    @Before
    fun before() {
        items(list(inNamespace(pods(clients.get()))), POD1, POD2, POD3)
        operator.namespace = currentNamespace
    }

    @Test
    fun `#getAllResources() returns cached pods, won't load a 2nd time`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        operator.namespace =  namespace
        operator.allResources
        // when
        operator.allResources
        // then
        verify(operator, times(1)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() wont return cached but load pods if #invalidate() is called`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        operator.namespace =  namespace
        operator.allResources
        verify(operator, times(1)).loadAllResources(namespace)
        operator.invalidate()
        // when
        operator.allResources
        // then
        verify(operator, times(2)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() won't load resources if namespace is null`() {
        // given
        operator.namespace =  null
        clearInvocations(operator)
        // when
        operator.allResources
        // then
        verify(operator, never()).loadAllResources(any())
    }

    @Test
    fun `#delete() deletes given pods in client`() {
        // given
        clearInvocations(operator)
        // when
        val toDelete = listOf(POD2)
        operator.delete(toDelete)
        // then
        verify(clients.get().pods().inNamespace(currentNamespace)).delete(toDelete)
    }

    @Test
    fun `#delete() won't delete if namespace is null`() {
        // given
        operator.namespace =  null
        clearInvocations(operator)
        // when
        operator.delete(listOf(POD2))
        // then
        verify(clients.get().pods().inNamespace(currentNamespace), never()).delete(any<List<Pod>>())
    }

    @Test
    fun `#delete() returns true if client could delete`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods().inNamespace(currentNamespace).delete(any<List<Pod>>()))
            .thenReturn(true)
        // when
        val success = operator.delete(listOf(POD2))
        // then
        assertThat(success).isTrue()
    }

    @Test
    fun `#delete() returns false if client could NOT delete`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods().inNamespace(currentNamespace).delete(any<List<Pod>>()))
            .thenReturn(false)
        // when
        val success = operator.delete(listOf(POD2))
        // then
        assertThat(success).isFalse()
    }

    @Test
    fun `#setNamespace(namespace) sets namespace that's used in #loadAllResources(namespace)`() {
        // given
        val namespace = "darth vader"
        operator.namespace =  namespace
        val namespaceCaptor = argumentCaptor<String>()
        clearInvocations(operator)
        // when
        operator.allResources
        // then
        verify(operator).loadAllResources(namespaceCaptor.capture())
        assertThat(namespaceCaptor.firstValue).isEqualTo(namespace)
    }

    @Test
    fun `#setNamespace(namespace) invalidates cache`() {
        // given
        clearInvocations(operator)
        // when
        operator.namespace =  "skywalker"
        // then
        verify(operator).invalidate()
    }

    @Test
    fun `#replace(pod) replaces pod if pod with same uid already exist`() {
        // given
        val uid = POD2.metadata.uid
        val pod = resource<Pod>("lord vader", "sith", uid)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isTrue()
        assertThat(operator.allResources).contains(pod)
    }

    @Test
    fun `#replace(pod) does NOT replace pod if pod has different name`() {
        // given
        val namespace = POD2.metadata.namespace
        val pod = resource<Pod>("darth vader", namespace)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isFalse()
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#replace(pod) does NOT replace pod if pod has different namespace`() {
        // given
        val name = POD2.metadata.name
        val pod = resource<Pod>(name, "sith")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isFalse()
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#add(pod) adds pod if not contained yet`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        operator.added(pod)
        // then
        assertThat(operator.allResources).contains(pod)
    }

    @Test
    fun `#add(pod) does not add if pod is already contained`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val size = operator.allResources.size
        operator.added(pod)
        // then
        assertThat(operator.allResources).contains(pod)
        assertThat(operator.allResources.size).isEqualTo(size)
    }

    @Test
    fun `#add(pod) is replacing if different instance of same pod is already contained`() {
        // given
        val instance1 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3")
        val instance2 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3")
        operator.added(instance1)
        assertThat(operator.allResources).contains(instance1)
        // when
        operator.added(instance2)
        // then
        assertThat(operator.allResources).doesNotContain(instance1)
        assertThat(operator.allResources).contains(instance2)
    }

    @Test
    fun `#add(pod) returns true if pod was added`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val added = operator.added(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#add(pod) returns false if pod was not added`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val added = operator.added(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#remove(pod) removes the given pod`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        operator.removed(pod)
        // then
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#remove(pod) removes the given pod if it isn't the same instance but matches in uid`() {
        // given
        val pod1 = operator.allResources.elementAt(0)
        val pod2 = resource<Pod>("skywalker", "jedi", pod1.metadata.uid)
        // when
        operator.removed(pod2)
        // then
        assertThat(operator.allResources).doesNotContain(pod1)
    }

    @Test
    fun `#remove(pod) returns true if pod was removed`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val removed = operator.removed(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#remove(pod) does not remove if pod is not contained`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val size = operator.allResources.size
        operator.removed(pod)
        // then
        assertThat(operator.allResources).doesNotContain(pod)
        assertThat(operator.allResources.size).isEqualTo(size)
    }

    @Test
    fun `#remove(pod) returns false if pod was not removed`() {
        // given
        val pod = resource<Pod>("papa-smurf")
        // when
        val removed = operator.removed(pod)
        // then
        assertThat(removed).isFalse()
    }

    class TestablePodsOperator(clients: Clients<KubernetesClient>): NamespacedPodsOperator(clients) {

        public override fun loadAllResources(namespace: String): List<Pod> {
            return super.loadAllResources(namespace)
        }
    }
}
