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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.container
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.inNamespace
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.items
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.list
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.pods
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resourceListOperation
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resourceOperation
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.withName
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class NamespacedPodsOperatorTest {

    private val currentNamespace = NAMESPACE2.metadata.name
    private val client = KubeClientAdapter(client(currentNamespace, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)))
    private val operator = spy(TestablePodsOperator(client))
    private val op = inNamespace(pods(client.get()))
    private val container1 = container("clones")
    private val container2 = container("supplies")

    @Before
    fun before() {
        items(list(op), POD1, POD2, POD3) // list
        val podResource = withName(op, POD2) // create, replace, get
        ClientMocks.inContainer(podResource, container1)
        ClientMocks.inContainer(podResource, container2)
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
    fun `#replaced(pod) replaces pod if pod which is same resource already exist`() {
        // given
        val pod = resource<Pod>(
            POD2.metadata.name,
            POD2.metadata.namespace,
            POD2.metadata.uid, POD2.apiVersion)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isTrue
        assertThat(operator.allResources).contains(pod)
    }

    @Test
    fun `#replaced(pod) does NOT replace pod if pod has different name`() {
        // given
        val pod = resource<Pod>(
            "darth vader",
            POD2.metadata.namespace,
            POD2.metadata.uid,
            POD2.apiVersion)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isFalse
    }

    @Test
    fun `#replaced(pod) does NOT replace pod if pod has different namespace`() {
        // given
        val pod = resource<Pod>(POD2.metadata.name, "sith", POD2.metadata.uid, POD2.apiVersion)
        assertThat(operator.allResources).doesNotContain(pod)
        val elementsBefore = operator.allResources.toTypedArray()
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isFalse
        assertThat(operator.allResources).containsExactly(*elementsBefore)
    }

    @Test
    fun `#added(pod) adds pod if not contained yet`() {
        // given
        val pod = resource<Pod>("papa-smurf", "smurf forest", "smurfUid", "v1")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        operator.added(pod)
        // then
        assertThat(operator.allResources).contains(pod)
    }

    @Test
    fun `#added(pod) does NOT add if pod is already contained`() {
        // given
        val pod = operator.allResources.elementAt(0)
        val elementsBefore = operator.allResources.toTypedArray()
        // when
        operator.added(pod)
        // then
        assertThat(operator.allResources).containsExactly(*elementsBefore)
    }

    @Test
    fun `#added(pod) is replacing if different instance of same pod is already contained`() {
        // given
        val instance1 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3", "v1")
        val instance2 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3", "v1")
        operator.added(instance1)
        assertThat(operator.allResources).contains(instance1)
        // when
        operator.added(instance2)
        // then
        assertThat(operator.allResources).doesNotContain(instance1)
        assertThat(operator.allResources).contains(instance2)
    }

    @Test
    fun `#added(pod) returns true if pod was added`() {
        // given
        val pod = resource<Pod>("papa-smurf", "ns1", "papaUid", "v1")
        assertThat(operator.allResources).doesNotContain(pod)
        val elementsBefore = listOf(*operator.allResources.toTypedArray())
        // when
        val added = operator.added(pod)
        // then
        assertThat(added).isTrue
        assertThat(operator.allResources).contains(pod)
        assertThat(operator.allResources).containsAll(elementsBefore)
    }

    @Test
    fun `#added(pod) returns false if pod was not added`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val added = operator.added(pod)
        // then
        assertThat(added).isFalse
    }

    @Test
    fun `#removed(pod) removes the given pod`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        operator.removed(pod)
        // then
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#removed(pod) removes the given pod if it isn't the same instance but is same pod`() {
        // given
        val pod1 = operator.allResources.elementAt(0)
        val pod2 = resource<Pod>(pod1.metadata.name, pod1.metadata.namespace, pod1.metadata.uid, pod1.apiVersion)
        // when
        operator.removed(pod2)
        // then
        assertThat(operator.allResources).doesNotContain(pod1)
    }

    @Test
    fun `#removed(pod) returns true if pod was removed`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val removed = operator.removed(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#removed(pod) does not remove if pod is not contained`() {
        // given
        val pod = resource<Pod>("papa-smurf", "ns1", "papaUid", "v1")
        assertThat(operator.allResources).doesNotContain(pod)
        val elementsBefore = operator.allResources.toTypedArray()
        // when
        operator.removed(pod)
        // then
        assertThat(operator.allResources).doesNotContain(pod)
        assertThat(operator.allResources).containsExactly(*elementsBefore)
    }

    @Test
    fun `#removed(pod) returns false if pod was not removed`() {
        // given
        val pod = resource<Pod>("papa-smurf", "ns1", "papaUid", "v1")
        // when
        val removed = operator.removed(pod)
        // then
        assertThat(removed).isFalse
    }

    @Test
    fun `#watchAll() watches all pods in given namespace using client`() {
        // given
        val watcher = mock<Watcher<Pod>>()
        // when
        operator.watchAll(watcher)
        // then
        verify(client.get().pods()
            .inNamespace(POD2.metadata.namespace)
        ).watch(watcher)
    }

    @Test
    fun `#watchAll() does NOT watch if operator has no namespace`() {
        // given
        val watcher = mock<Watcher<Pod>>()
        operator.namespace = null
        // when
        operator.watchAll(watcher)
        // then
        verify(client.get().pods()
            .inNamespace(POD2.metadata.namespace), never()
        ).watch(watcher)
    }

    @Test
    fun `#watch() watches given pod`() {
        // given
        val watcher = mock<Watcher<Pod>>()
        // when
        operator.watch(POD2, watcher)
        // then
        verify(client.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name)
        ).watch(watcher)
    }

    @Test
    fun `#delete(List, false) deletes given resources`() {
        // given
        val toDelete = listOf(POD2)
        resourceListOperation(resourceList = toDelete, client = client.get())
        // when
        operator.delete(toDelete, false)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resourceList(toDelete))
            .delete()
    }

    @Test
    fun `#delete(List, true) deletes given resources with grace period 0`() {
        // given
        val toDelete = listOf(POD2)
        resourceListOperation(resourceList = toDelete, client = client.get())
        // when
        operator.delete(toDelete, true)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resourceList(toDelete)
            .withGracePeriod(0))
            .delete()
    }

    @Test
    fun `#delete(List) returns true if client could delete`() {
        // given
        val toDelete = listOf(POD2)
        resourceListOperation(resourceList = toDelete, client = client.get())
        // when
        val success = operator.delete(toDelete, false)
        // then
        assertThat(success).isTrue
    }

    @Test
    fun `#delete(pods) returns false if client could NOT delete`() {
        // given
        val toDelete = listOf(POD2)
        resourceListOperation(resourceList = toDelete, statusDetails = emptyList(), client = client.get())
        // when
        val success = operator.delete(toDelete, false)
        // then
        assertThat(success).isFalse
    }

    @Test
    fun `#replace() is replacing given pod in client`() {
        // given
        val toReplace = POD2
        resourceOperation(resource = toReplace, client = client.get())
        // when
        operator.replace(toReplace)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resource(toReplace)
            .inNamespace(toReplace.metadata.namespace))
            .patch()
    }

    @Test
    fun `#replace() is removing and restoring resource version`() {
        // given
        val toReplace = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        val resourceVersion = toReplace.metadata.resourceVersion
        resourceOperation(resource = toReplace, client = client.get())
        // when
        operator.replace(toReplace)
        // then
        verify(toReplace.metadata).resourceVersion = null
        verify(toReplace.metadata).resourceVersion = resourceVersion
    }

    @Test
    fun `#replace() is removing uid`() {
        // given
        val toReplace = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        resourceOperation(resource = toReplace, client = client.get())
        clearInvocations(toReplace)
        val uid = toReplace.metadata.uid
        // when
        operator.replace(toReplace)
        // then
        verify(toReplace.metadata).uid = null
        verify(toReplace.metadata).uid = uid
    }

    @Test
    fun `#replace() will replace even if operator namespace is null`() {
        // given
        val toReplace = POD2
        operator.namespace =  null
        clearInvocations(operator)
        resourceOperation(resource = toReplace, client = client.get())
        // when
        operator.replace(toReplace)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resource(toReplace)
            .inNamespace(toReplace.metadata.namespace))
            .patch()
    }

    @Test
    fun `#replace() returns new pod if client replaced`() {
        // given
        val toReplace = POD2
        clearInvocations(operator)
        resourceOperation(resource = toReplace, client = client.get())
        whenever(client.get().adapt(KubernetesClient::class.java)
            .resource(toReplace)
            .inNamespace(toReplace.metadata.namespace)
            .patch())
            .thenReturn(POD3)
        // when
        val newPod = operator.replace(toReplace)
        // then
        assertThat(newPod).isEqualTo(POD3)
    }

    @Test
    fun `#create() is creating given pod in client`() {
        // given
        val toCreate = POD2
        resourceOperation(resource = toCreate, client = client.get())
        // when
        operator.create(toCreate)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resource(toCreate)
            .inNamespace(toCreate.metadata.namespace))
            .create()
    }

    @Test
    fun `#create() is removing and restoring resource version`() {
        // given
        val toCreate = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        resourceOperation(resource = toCreate, client = client.get())
        val resourceVersion = toCreate.metadata.resourceVersion
        // when
        operator.create(toCreate)
        // then
        verify(toCreate.metadata).resourceVersion = null // 1st call: clear before sending
        verify(toCreate.metadata).resourceVersion = resourceVersion // 2nd call: restore after sending
    }

    @Test
    fun `#create() is removing uid`() {
        // given
        val toCreate = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        resourceOperation(resource = toCreate, client = client.get())
        val uid = toCreate.metadata.uid
        // when
        operator.create(toCreate)
        // then
        verify(toCreate.metadata).uid = null // 1st call: clear before sending
        verify(toCreate.metadata).uid = uid // 2nd call: restore after sending
    }

    @Test
    fun `#create() is creating with resource namespace if operator namespace is null`() {
        // given
        val toCreate = POD2
        operator.namespace =  null
        resourceOperation(resource = toCreate, client = client.get())
        // when
        operator.create(toCreate)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resource(toCreate)
            .inNamespace(toCreate.metadata.namespace))
            .create()
    }

    @Test
    fun `#create() is creating with operator namespace if resource namespace is null`() {
        // given
        val toCreate = resource<Pod>("pod2", null, "podUid2", "v1", "1")
        operator.namespace =  "death start"
        resourceOperation(resource = toCreate, client = client.get())
        // when
        operator.create(toCreate)
        // then
        verify(client.get().adapt(KubernetesClient::class.java)
            .resource(toCreate)
            .inNamespace(operator.namespace))
            .create()
    }

    @Test
    fun `#create() returns new pod if client has successfully created`() {
        // given
        val toCreate = POD2
        resourceOperation(resource = toCreate, client = client.get())
        whenever(client.get().adapt(KubernetesClient::class.java)
            .resource(toCreate)
            .inNamespace(toCreate.metadata.namespace)
            .create())
            .thenReturn(POD3)
        // when
        val newPod = operator.create(toCreate)
        // then
        assertThat(newPod).isEqualTo(POD3)
    }

    @Test
    fun `#get() is getting given pod in client`() {
        // given
        val toGet = POD2
        // when
        operator.get(toGet)
        // then
        verify(client.get()
            .pods()
            .inNamespace(toGet.metadata.namespace)
            .withName(toGet.metadata.name))
            .get()
    }

    @Test
    fun `#get() is using resource namespace if exists`() {
        // given
        val toGet = PodBuilder()
            .withNewMetadata()
                .withName("Princess Leia")
                .withNamespace("Alderaan")
            .endMetadata()
            .build()
        // when
        operator.get(toGet)
        // then
        verify(client.get()
            .pods())
            .inNamespace(toGet.metadata.namespace)
    }

    @Test
    fun `#get() is using operator namespace if resource namespace is null`() {
        // given
        val pod = PodBuilder()
            .withNewMetadata()
                .withName("Princess Leia")
                .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "Alderaan" // should use it
        clearInvocations(operator)
        // when
        operator.get(pod)
        // then
        verify(client.get()
            .pods())
            .inNamespace(operator.namespace)
    }

    @Test
    fun `#watchLog() is using resource namespace if exists`() {
        // given
        clearInvocations(operator)
        // when
        operator.watchLog(container1, POD2, mock())
        // then
        verify(client.get().pods())
            .inNamespace(POD2.metadata.namespace)
    }

    @Test
    fun `#watchLog() is using operator namespace if resource namespace is null`() {
        // given
        val pod = PodBuilder()
            .withNewMetadata()
                .withName("Princess Leia")
                .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "Alderaan" // should use it
        clearInvocations(operator)
        // when
        operator.watchLog(container1, pod, mock())
        // then
        verify(client.get().pods())
            .inNamespace(operator.namespace)
    }

    @Test
    fun `#watchLog() is using resource by name`() {
        // given
        val pod = PodBuilder()
            .withNewMetadata()
                .withName("Princess Leia")
                .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "Alderaan" // should use it
        clearInvocations(operator)
        // when
        operator.watchLog(container1, pod, mock())
        // then
        verify(client.get().pods()
            .inNamespace(operator.namespace))
            .withName(pod.metadata.name)
    }

    @Test
    fun `#watchLog() is using resource by container name`() {
        // given
        val pod = PodBuilder()
            .withNewMetadata()
                .withName(POD2.metadata.name)
                .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "Alderaan" // should use it
        clearInvocations(operator)
        // when
        operator.watchLog(container1, pod, mock())
        // then
        verify(client.get().pods()
            .inNamespace(operator.namespace)
            .withName(pod.metadata.name))
            .inContainer(container1.name)
    }

    @Test
    fun `#watchExec() is using resource namespace if exists`() {
        // given
        clearInvocations(operator)
        // when
        operator.watchExec(container1, POD2, mock())
        // then
        verify(client.get().pods())
            .inNamespace(POD2.metadata.namespace)
    }

    @Test
    fun `#watchExec() is using operator namespace if resource namespace is null`() {
        // given
        val pod = PodBuilder()
            .withNewMetadata()
                .withName("Princess Leia")
                .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "Ando" // should use it
        clearInvocations(operator)
        // when
        operator.watchExec(container1, pod, mock())
        // then
        verify(client.get().pods())
            .inNamespace(operator.namespace)
    }

    @Test
    fun `#watchExec() is using resource by name`() {
        // given
        operator.namespace =  "Alderaan" // should use it
        clearInvocations(operator)
        // when
        operator.watchExec(container1, POD1, mock())
        // then
        verify(client.get().pods()
            .inNamespace(operator.namespace))
            .withName(POD1.metadata.name)
    }

    @Test
    fun `#watchExec() is using container by name`() {
        // given
        operator.namespace =  "Alderaan" // should use it
        clearInvocations(operator)
        // when
        operator.watchExec(container1, POD2, mock())
        // then
        verify(client.get().pods()
            .inNamespace(operator.namespace)
            .withName(POD2.metadata.name))
            .inContainer(container1.name)
    }

    class TestablePodsOperator(client: ClientAdapter<out KubernetesClient>): NamespacedPodsOperator(client) {

        public override fun loadAllResources(namespace: String): List<Pod> {
            return super.loadAllResources(namespace)
        }
    }
}
