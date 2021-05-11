/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class ClusterResourceTest {

    private val rebelsNamespace: Namespace = resource("rebels")
    private val client = ClientMocks.client(
        rebelsNamespace.metadata.name,
        arrayOf(rebelsNamespace)
    )
    private val clients: Clients<KubernetesClient> = Clients(client)
    private val resource: Pod = resource("Coruscant")
    private val updatedResource: Pod = resource("Endor", rebelsNamespace.metadata.name)
    private val watch: Watch = mock()
    private val watchOp: (watcher: Watcher<in Pod>) -> Watch? = { watch }
    private val operator = namespacedResourceOperator<Pod, KubernetesClient>(
        AllPodsOperator.KIND,
        emptyList(),
        rebelsNamespace,
        watchOp,
        true,
        updatedResource
    )
    private val resourceWatch: ResourceWatch<HasMetadata> = mock()
    private val observable: ModelChangeObservable = mock()
    private val cluster = TestableClusterResource(resource, operator, clients, resourceWatch, observable)

    @Test
    fun `#get(false) should not retrieve from cluster`() {
        // given
        // when
        cluster.get(false)
        // then
        verify(operator, never()).get(any())
    }

    @Test
    fun `#get(true) should retrieve from cluster`() {
        // given
        // when
        cluster.get(true)
        // then
        verify(operator, times(1)).get(any())
    }

    @Test
    fun `#get(true) should return null if cluster returns 404`() {
        // given
        whenever(operator.get(any()))
            .doThrow(KubernetesClientException("not found", 404, null))
        // when
        val retrieved = cluster.get(true)
        // then resource was deleted
        assertThat(retrieved).isNull()
    }

    @Test(expected = KubernetesClientException::class)
    fun `#get(true) should throw if cluster throws exception that is not 404`() {
        // given
        whenever(operator.get(any()))
            .doThrow(KubernetesClientException("internal error", 500, null))
        // when
        cluster.get(true)
        // then should have thrown
    }

    @Test
    fun `#saveToCluster should replace if same resource`() {
        // given
        // when
        val saved = cluster.saveToCluster(resource)
        // then
        verify(operator).replace(resource)
    }

    @Test
    fun `#saveToCluster should create if different resource`() {
        // given
        // when
        cluster.saveToCluster(updatedResource)
        // then
        verify(operator).create(updatedResource)
    }

    @Test
    fun `#set should set resource that is returned from cache`() {
        // given
        val original = cluster.get(false)
        // when
        cluster.set(updatedResource)
        // then
        val updated = cluster.get(false)
        assertThat(updated).isEqualTo(updatedResource)
    }

    @Test
    fun `#isSameResource should return true if same resource as initial resource`() {
        // given
        // when
        val same = cluster.isSameResource(resource)
        // then
        assertThat(same).isTrue()
    }

    @Test
    fun `#isSameResource should return false if NOT same resource as initial resource`() {
        // given
        // when
        val same = cluster.isSameResource(updatedResource)
        // then
        assertThat(same).isFalse()
    }

    @Test
    fun `#isDeleted should return true if resource retrieval has 404`() {
        // given
        whenever(operator.get(any()))
            .doThrow(KubernetesClientException("not found", 404, null))
        // when
        val deleted = cluster.isDeleted()
        // then
        assertThat(deleted).isTrue()
    }

    @Test
    fun `#watch should watch`() {
        // given
        // when
        cluster.watch()
        // then
        verify(resourceWatch).watch(eq(resource), any(), any())
    }

    @Test
    fun `watchListener#removed should set cached resource to null`() {
        // given
        // when trigger watchListener#remove
        cluster.watchListeners.removed(updatedResource)
        // then
        assertThat(cluster.get(false)).isNull()
    }

    @Test
    fun `watchListener#removed should fire observable#removed`() {
        // given
        // when trigger watchListener#remove
        cluster.watchListeners.removed(updatedResource)
        // then
        verify(observable).fireRemoved(updatedResource)
    }

    @Test
    fun `watchListener#replaced should update cached resource`() {
        // given
        // when trigger watchListener#replaced
        cluster.watchListeners.replaced(updatedResource)
        // then
        assertThat(cluster.get(false)).isEqualTo(updatedResource)
    }

    @Test
    fun `watchListener#replace should fire observable#modified`() {
        // given
        // when trigger watchListener#replaced
        cluster.watchListeners.replaced(updatedResource)
        // then
        verify(observable).fireModified(updatedResource)
    }

    @Test
    fun `#stopWatch should stop watch`() {
        // given
        // when trigger watchListener#remove
        cluster.stopWatch()
        // then
        verify(resourceWatch).stopWatch(resource)
    }

    @Test
    fun `#close should close watch`() {
        // given
        // when trigger watchListener#remove
        cluster.close()
        // then
        verify(resourceWatch).close()
    }

    private class TestableClusterResource(
        resource: HasMetadata,
        private val operator: IResourceOperator<out HasMetadata>?,
        clients: Clients<KubernetesClient>,
        watch: ResourceWatch<HasMetadata>,
        observable: ModelChangeObservable
    ) : ClusterResource(resource, "ClusterResourceTest", clients, watch, observable) {

        public override val watchListeners: ResourceWatch.WatchListeners
            get() {
                return super.watchListeners
            }

        override fun createOperator(resource: HasMetadata): IResourceOperator<out HasMetadata>? {
            return operator
        }
    }
}