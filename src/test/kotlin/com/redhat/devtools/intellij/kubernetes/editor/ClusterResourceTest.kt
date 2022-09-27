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
package com.redhat.devtools.intellij.kubernetes.editor

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.ResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClusterResourceTest {

    private val rebelsNamespace: Namespace = resource("rebels", null, "rebelsUid", "v1")
    private val endorResource: Pod = resource("Endor", rebelsNamespace.metadata.name, "endorUid", "v1", "1")
    private val endorResourceOnCluster: Pod = resource("Endor", rebelsNamespace.metadata.name, "endorUid", "v1", "2")
    private val modifiedEndorResourceOnCluster: Pod = resource("Endor", rebelsNamespace.metadata.name, "endorUid", "v1", "3")
    private val nabooResource: Pod = resource("Naboo", rebelsNamespace.metadata.name, "nabooUid", "v1", "1")
    private val watch: Watch = mock()
    private val context: IActiveContext<out HasMetadata, out KubernetesClient> = mock {
        on { get(any()) } doReturn endorResourceOnCluster
        on { replace(any()) } doReturn endorResourceOnCluster
        on { watch(any(), any()) } doReturn watch
    }
    private val resourceWatch: ResourceWatch<HasMetadata> = mock()
    private val observable: ResourceModelObservable = mock()
    private val cluster = TestableClusterResource(endorResource, context, resourceWatch, observable)

    @Test
    fun `#pull(false) should retrieve from cluster if there is no cached value yet`() {
        // given
        assertThat(cluster.updatedResource).isNull()
        // when
        cluster.pull(false)
        // then
        verify(context).get(any())
    }

    @Test
    fun `#pull(false) should NOT retrieve from cluster if there is a cached value`() {
        // given
        cluster.updatedResource = endorResourceOnCluster
        assertThat(cluster.updatedResource).isNotNull()
        // when
        cluster.pull(false)
        // then
        verify(context, never()).get(any())
    }

    @Test
    fun `#pull(true) should retrieve from cluster if there is a cached value`() {
        // given
        cluster.updatedResource = endorResourceOnCluster
        assertThat(cluster.updatedResource).isNotNull()
        // when
        cluster.pull(true)
        // then
        verify(context).get(any())
    }

    @Test
    fun `#pull(true) should retrieve from cluster`() {
        // given
        // when
        cluster.pull(true)
        // then
        verify(context, times(1)).get(any())
    }

    @Test(expected = ResourceException::class)
    fun `#pull(true) should throw exception that happens when operator#get throws`() {
        // given
        whenever(context.get(any()))
            .doThrow(KubernetesClientException("forbidden", 401, null))
        // when
        cluster.pull(true)
    }

    @Test(expected = ResourceException::class)
    fun `#pull(true) should throw if cluster throws exception that is not 404`() {
        // given
        whenever(context.get(any()))
            .doThrow(KubernetesClientException("internal error", 500, null))
        // when
        cluster.pull(true)
        // then should have thrown
    }

    @Test
    fun `#canPush should return true if cluster has no resource`() {
        // given
        whenever(context.get(any()))
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
                .withResourceVersion("resourceVersion-42")
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
            .withName("name-42")
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
        whenever(context.get(any()))
            .doReturn(endorResourceOnCluster)
        // when
        cluster.push(endorResourceOnCluster)
        // then
        verify(context).replace(endorResourceOnCluster)
    }

    @Test
    fun `#canPush should return false if given resource has different namespace`() {
        // given
        val differentNamespace = PodBuilder(endorResource)
            .editOrNewMetadata()
            .withNamespace("namespace-42")
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
        val canPush = cluster.canPush(differentApiVersion)
        // then
        assertThat(canPush).isFalse()
    }

    @Test
    fun `#push should call operator#replace`() {
        // given
        whenever(context.get(any()))
            .doReturn(null)
        // when
        cluster.push(endorResourceOnCluster)
        // then
        verify(context).replace(endorResourceOnCluster)
    }

    @Test(expected= ResourceException::class)
    fun `#push should throw if given resource is NOT the same`() {
        // given
        whenever(context.get(any()))
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
    fun `#isOutdated(String) should return false if cluster has null resource`() {
        // given
        val resourceVersion = "42"
        whenever(context.get(any()))
            .doReturn(null)
        // when
        val outdated = cluster.isOutdated(resourceVersion)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isOutdated(String) should return false if resourceVersion is null and cluster has null resource`() {
        // given
        val resourceVersion = null
        whenever(context.get(any()))
            .doReturn(null)
        // when
        val outdated = cluster.isOutdated(resourceVersion as String?)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isOutdated(String) should return true if resourceVersion is null and cluster has resource`() {
        // given
        val resourceVersion = null
        whenever(context.get(any()))
            .doReturn(endorResourceOnCluster)
        // when
        val outdated = cluster.isOutdated(resourceVersion as String?)
        // then
        assertThat(outdated).isTrue()
    }

    @Test
    fun `#isOutdated(String) should return true if resourceVersion is smaller than cluster resource version`() {
        // given
        val resourceVersion = (endorResourceOnCluster.metadata.resourceVersion.toInt() - 1).toString()
        whenever(context.get(any()))
            .doReturn(endorResourceOnCluster)
        // when
        val outdated = cluster.isOutdated(resourceVersion as String?)
        // then
        assertThat(outdated).isTrue()
    }

    @Test
    fun `#isOutdated(String) should return true if resourceVersion is greater than cluster resource version`() {
        // given
        val resourceVersion = (endorResourceOnCluster.metadata.resourceVersion.toInt() + 1).toString()
        whenever(context.get(any()))
            .doReturn(endorResourceOnCluster)
        // when
        val outdated = cluster.isOutdated(resourceVersion as String?)
        // then - resourceVersion is alphanumeric, no numeric comparison is possible
        // see https://kubernetes.io/docs/reference/using-api/api-concepts/#resource-versions
        assertThat(outdated).isTrue()
    }

    @Test
    fun `#isOutdated(String) should return false if resourceVersion is equal to cluster resource version`() {
        // given
        val resourceVersion = endorResourceOnCluster.metadata.resourceVersion
        whenever(context.get(any()))
            .doReturn(endorResourceOnCluster)
        // when
        val outdated = cluster.isOutdated(resourceVersion)
        // then
        assertThat(outdated).isFalse()
    }

    @Test
    fun `#isModified should return true if given resource has different kind`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .withKind("kind-42")
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isTrue()
    }

    @Test
    fun `#isModified should return true if given resource has different apiVersion`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .withApiVersion("apiVersion-42")
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isTrue()
    }

    @Test
    fun `#isModified should return true if given resource has different namespace`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .editOrNewMetadata()
            .withNamespace("name-42")
            .endMetadata()
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isTrue()
    }

    @Test
    fun `#isModified should return true if given resource has different name`() {
        // given
        val modifiedResource = PodBuilder(endorResource)
            .editOrNewMetadata()
            .withName("name-42")
            .endMetadata()
            .build()
        // when
        val isModified = cluster.isModified(modifiedResource)
        // then
        assertThat(isModified).isTrue()
    }

    @Test
    fun `#isModified should return false if doesn't exist on cluster`() {
        // given
        whenever(context.get(any()))
            .doReturn(null)
        // when
        val isModified = cluster.isModified(endorResource)
        // then
        assertThat(isModified).isFalse()
    }

    @Test
    fun `#exists should return false if resource retrieval has 404`() {
        // given
        whenever(context.get(any()))
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
    fun `#watch should notify modified if cluster has modified resource`() {
        // given
        cluster.pull() // initialize updatedResource
        doReturn(modifiedEndorResourceOnCluster) // request returns modified resource
            .whenever(context).get(any())
        // when
        cluster.watch()
        // then
        verify(observable).fireModified(modifiedEndorResourceOnCluster)
    }

    @Test
    fun `#watch should notify removed if resource was removed on cluster`() {
        // given
        val updated = cluster.pull() // initialize updatedResource
        doReturn(null) // request returns modified resource
            .whenever(context).get(any())
        // when
        cluster.watch()
        // then
        verify(observable).fireRemoved(updated!!)
    }

    @Test
    fun `#watch should NOT notify if resource wasn't not requested yet`() {
        // given
        // no cluster.get() so that updatedResource is present yet
        doReturn(modifiedEndorResourceOnCluster) // request returns modified resource
            .whenever(context).get(any())
        // when
        cluster.watch()
        // then
        verify(observable, never()).fireModified(modifiedEndorResourceOnCluster)
        verify(observable, never()).fireRemoved(endorResourceOnCluster)
    }

    @Test(expected = ResourceException::class)
    fun `#watch should throw ResourceException if watch throws IllegalArgumentException`() {
        // given
        whenever(resourceWatch.watch(any(), any(), any()))
            .doThrow(IllegalArgumentException())
        // when
        cluster.watch()
        // then
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

    @Test
    fun `#close should NOT close watch if it is already closed`() {
        // given
        // when trigger watchListener#remove
        cluster.close()
        cluster.close()
        // then
        verify(resourceWatch, times(1)).close()
    }

    @Test
    fun `#isClosed should be false after instantiation`() {
        // given
        // when
        // then
        val closed = cluster.isClosed()
        assertThat(closed).isFalse()
    }

    @Test
    fun `#isClosed should be true after cluster was closed`() {
        // given
        // when
        cluster.close()
        // then
        assertThat(cluster.isClosed()).isTrue()
    }

    private class TestableClusterResource(
        resource: HasMetadata,
        context: IActiveContext<out HasMetadata, out KubernetesClient>,
        watch: ResourceWatch<HasMetadata>,
        observable: ResourceModelObservable
        ) : ClusterResource(resource, context, watch, observable) {

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

        public override fun set(resource: HasMetadata?) {
            super.set(resource)
        }

    }
}