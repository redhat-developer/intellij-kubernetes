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
import com.nhaarman.mockitokotlin2.doReturn
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
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClusterResourceTest {

    private val rebelsNamespace: Namespace = resource("rebels", null, "rebelsUid", "v1")
    private val client = ClientMocks.client(
        rebelsNamespace.metadata.name,
        arrayOf(rebelsNamespace)
    )
    private val clients: Clients<KubernetesClient> = Clients(client)
    private val endorResource: Pod = resource("Endor", rebelsNamespace.metadata.name, "endorUid", "v1", "1")
    private val endorResourceOnCluster: Pod = resource("Endor", rebelsNamespace.metadata.name, "endorUid", "v1", "2")
    private val modifiedEndorResourceOnCluster: Pod = resource("Endor", rebelsNamespace.metadata.name, "endorUid", "v1", "2")
    private val nabooResource: Pod = resource("Naboo", rebelsNamespace.metadata.name, "nabooUid", "v1", "1")
    private val watch: Watch = mock()
    private val watchOp: (watcher: Watcher<in Pod>) -> Watch? = { watch }
    private val operator = namespacedResourceOperator<Pod, KubernetesClient>(
        AllPodsOperator.KIND,
        emptyList(),
        rebelsNamespace,
        watchOp,
        true,
        endorResourceOnCluster
    )
    private val resourceWatch: ResourceWatch<HasMetadata> = mock()
    private val observable: ModelChangeObservable = mock()
    private val cluster = TestableClusterResource(endorResource, operator, clients, resourceWatch, observable)

    @Test
    fun `#get(false) should retrieve from cluster if there is no cached value yet`() {
        // given
        assertThat(cluster.updatedResource).isNull()
        // when
        cluster.get(false)
        // then
        verify(operator).get(any())
    }

    @Test
    fun `#get(false) should NOT retrieve from cluster if there is a cached value`() {
        // given
        cluster.updatedResource = endorResourceOnCluster
        assertThat(cluster.updatedResource).isNotNull()
        // when
        cluster.get(false)
        // then
        verify(operator, never()).get(any())
    }

    @Test
    fun `#get(true) should retrieve from cluster if there is a cached value`() {
        // given
        cluster.updatedResource = endorResourceOnCluster
        assertThat(cluster.updatedResource).isNotNull()
        // when
        cluster.get(true)
        // then
        verify(operator).get(any())
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

    @Test(expected = ResourceException::class)
    fun `#get(true) should throw if cluster throws exception that is not 404`() {
        // given
        whenever(operator.get(any()))
            .doThrow(KubernetesClientException("internal error", 500, null))
        // when
        cluster.get(true)
        // then should have thrown
    }

    @Test(expected = ResourceException::class)
    fun `#get(true) should throw if there is no operator`() {
        // given
        val cluster = TestableClusterResource(endorResource, null, clients, resourceWatch, observable)
        // when
        cluster.get(true)
        // then should have thrown
    }

    @Test
    fun `#canPush should return true if cluster has no resource`() {
        // given
        whenever(operator.get(any()))
            .doReturn(null)
        // when
        val canPush = cluster.canPush(endorResource)
        // then
        assertThat(canPush).isTrue()
    }

    @Test
    fun `#canPush should return true if given resource is null`() {
        // given
        // when
        val canPush = cluster.canPush(null)
        // then
        assertThat(canPush).isTrue()
    }

    @Test
    fun `#canPush should return true if given resource is modified`() {
        // given
        val modifiedResource = PodBuilder(endorResourceOnCluster)
                .editOrNewMetadata()
                .withNewResourceVersion("resourceVersion-42")
                .endMetadata()
            .build()
        // when
        val canPush = cluster.canPush(modifiedResource)
        // then
        assertThat(canPush).isTrue()
    }

    @Test
    fun `#canPush should return false if given resource has different name`() {
        // given
        val differentName = PodBuilder(endorResourceOnCluster)
            .editOrNewMetadata()
            .withNewName("name-42")
            .endMetadata()
            .build()
        // when
        val canPush = cluster.canPush(differentName)
        // then
        assertThat(canPush).isFalse()
    }

    @Test
    fun `#push should replace if exists on cluster`() {
        // given
        whenever(operator.get(any()))
            .doReturn(endorResourceOnCluster)
        // when
        cluster.push(endorResourceOnCluster)
        // then
        verify(operator).replace(endorResourceOnCluster)
    }

    @Test
    fun `#canPush should return false if given resource has different namespace`() {
        // given
        val differentNamespace = PodBuilder(endorResource)
            .editOrNewMetadata()
            .withNewNamespace("namespace-42")
            .endMetadata()
            .build()
        // when
        val canPush = cluster.canPush(differentNamespace)
        // then
        assertThat(canPush).isFalse()
    }

    @Test
    fun `#canPush should return false if given resource has different kind`() {
        // given
        val differentKind = PodBuilder(endorResource)
            .withKind("kind-42")
            .build()
        // when
        val canPush = cluster.canPush(differentKind)
        // then
        assertThat(canPush).isFalse()
    }

    @Test
    fun `#canPush should return false if given resource has different apiVersion`() {
        // given
        val differentApiVersion = PodBuilder(endorResource)
            .withApiVersion("apiVersion-42")
            .build()
        // when
        val isModified = cluster.canPush(differentApiVersion)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#push should create if does NOT exist on cluster`() {
        // given
        whenever(operator.get(any()))
            .doReturn(null)
        // when
        cluster.push(endorResourceOnCluster)
        // then
        verify(operator).create(endorResourceOnCluster)
    }

    @Test(expected=ResourceException::class)
    fun `#push should throw if given resource is NOT the same`() {
        // given
        whenever(operator.get(any()))
            .doReturn(null)
        // when
        cluster.push(nabooResource)
        // then
    }

    @Test
    fun `#set should set resource if it's the same in kind, apiVersion, name, namespace`() {
        // given
        // when
        cluster.set(modifiedEndorResourceOnCluster)
        // then
        assertThat(cluster.updatedResource).isEqualTo(modifiedEndorResourceOnCluster)
    }

    @Test
    fun `#set should NOT set resource if it's NOT the same in kind, apiVersion, name, namespace`() {
        // given
        // when
        cluster.set(nabooResource)
        // then
        assertThat(cluster.updatedResource).isNotEqualTo(endorResourceOnCluster)
    }

    @Test
    fun `#set should reset deleted`() {
        // given
        cluster.setDeleted(true)
        assertThat(cluster.isDeleted()).isTrue()
        // when
        cluster.set(modifiedEndorResourceOnCluster)
        // then
        assertThat(cluster.isDeleted()).isFalse()
    }

    @Test
    fun `#isDeleted() should be false initially`() {
        // given
        // when
        val deleted = cluster.isDeleted()
        // then
        assertThat(deleted).isFalse()
    }

    @Test
    fun `#isDeleted() should return true if watch listener notified 'removed'`() {
        // given
        // when
        cluster.watchListeners.removed(endorResourceOnCluster)
        // then
        assertThat(cluster.isDeleted()).isTrue()
    }

    @Test
    fun `#isSameResource should return false if given null and cluster has resource`() {
        // given
        // when
        val same = cluster.isSameResource(null)
        // then
        assertThat(same).isFalse()
    }

    @Test
    fun `#isSameResource should return true if given same resource as initial resource`() {
        // given
        val clone = PodBuilder(endorResource).build()
        // when
        val same = cluster.isSameResource(clone)
        // then
        assertThat(same).isTrue()
    }

    @Test
    fun `#isSameResource should return false if given has different name`() {
        // given
        // when
        val same = cluster.isSameResource(nabooResource)
        // then
        assertThat(same).isFalse()
    }

    @Test
    fun `#isOutdated should return false if given same resource`() {
        // given
        // when
        val outdated = cluster.isOutdated(endorResourceOnCluster)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isOutdated should return false if given different resource`() {
        // given
        // when
        val outdated = cluster.isOutdated(nabooResource)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isOutdated should return true if given null`() {
        // given
        // when
        val outdated = cluster.isOutdated(null)
        // then
        assertThat(outdated).isTrue()
    }

    @Test
    fun `#isOutdated should return false if given null and cluster does NOT have updated resource`() {
        // given
        whenever(operator.get(any()))
            .doReturn(null)
        // when
        val outdated = cluster.isOutdated(null)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isOutdated should return true if given outdated resource`() {
        // given
        // when
        val outdated = cluster.isOutdated(endorResource)
        // then
        assertThat(outdated).isTrue()
    }

    @Test
    fun `#isOutdated should return false if given resource is updated and cluster has outdated resource`() {
        // given
        whenever(operator.get(any()))
            .doReturn(endorResource)
        // when
        val outdated = cluster.isOutdated(endorResourceOnCluster)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isOutdated should return false if cluster has null resource`() {
        // given
        whenever(operator.get(any()))
            .doReturn(null)
        // when
        val outdated = cluster.isOutdated(endorResource)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isModified should return false if given resource has different kind`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .withKind("kind-42")
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#isModified should return false if given resource has different apiVersion`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .withApiVersion("apiVersion-42")
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#isModified should return false if given resource has different namespace`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .editOrNewMetadata()
            .withNewNamespace("name-42")
            .endMetadata()
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#isModified should return false if given resource has different name`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .editOrNewMetadata()
            .withNewName("name-42")
            .endMetadata()
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#isModified should return false if doesn't exist on cluster`() {
        // given
        whenever(operator.get(any()))
            .doReturn(null)
        // when
        val isModified = cluster.isModified(endorResource)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#exists should return false if resource retrieval has 404`() {
        // given
        whenever(operator.get(any()))
            .doThrow(KubernetesClientException("not found", 404, null))
        // when
        val exists = cluster.exists()
        // then
        assertThat(exists).isFalse()
    }

    @Test
    fun `#watch should watch resource`() {
        // given
        // when
        cluster.watch()
        // then
        verify(resourceWatch).watch(eq(endorResource), any(), any())
    }

    @Test
    fun `#watch should NOT watch resource if there is no operator`() {
        // given
        cluster.operator = null
        // when
        cluster.watch()
        // then
        verify(resourceWatch, never()).watch(eq(endorResource), any(), any())
    }

    @Test
    fun `watchListener#removed should set cached resource to null`() {
        // given
        // when trigger watchListener#remove
        cluster.watchListeners.removed(endorResourceOnCluster)
        // then
        assertThat(cluster.updatedResource).isNull()
    }

    @Test
    fun `watchListener#removed should fire observable#removed`() {
        // given
        // when trigger watchListener#remove
        cluster.watchListeners.removed(endorResourceOnCluster)
        // then
        verify(observable).fireRemoved(endorResourceOnCluster)
    }

    @Test
    fun `watchListener#replaced should update cached resource`() {
        // given
        // when trigger watchListener#replaced
        cluster.watchListeners.replaced(endorResourceOnCluster)
        // then
        assertThat(cluster.updatedResource).isEqualTo(endorResourceOnCluster)
    }

    @Test
    fun `watchListener#replace should fire observable#modified`() {
        // given
        // when trigger watchListener#replaced
        cluster.watchListeners.replaced(endorResourceOnCluster)
        // then
        verify(observable).fireModified(endorResourceOnCluster)
    }

    @Test
    fun `#stopWatch should stop watch`() {
        // given
        // when trigger watchListener#remove
        cluster.stopWatch()
        // then
        verify(resourceWatch).stopWatch(endorResource)
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
        public override var operator: IResourceOperator<out HasMetadata>?,
        clients: Clients<KubernetesClient>,
        watch: ResourceWatch<HasMetadata>,
        observable: ModelChangeObservable
    ) : ClusterResource(resource, clients, watch, observable) {

        public override var updatedResource: HasMetadata?
            get(): HasMetadata? {
                return super.updatedResource
            }
            set(value) {
                super.updatedResource = value
            }

        public override val watchListeners: ResourceWatch.WatchListeners
            get() {
                return super.watchListeners
            }

        public override fun setDeleted(deleted: Boolean) {
            super.setDeleted(deleted)
        }
    }
}