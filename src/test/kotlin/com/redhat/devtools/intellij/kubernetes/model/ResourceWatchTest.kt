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
package com.redhat.devtools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class ResourceWatchTest {

    private val addOperationState = OperationState()
    private val addOperation: (HasMetadata) -> Unit = { addOperationState.operation(it) }
    private val removeOperationState = OperationState()
    private val removeOperation: (HasMetadata) -> Unit = { removeOperationState.operation(it) }
    private val replaceOperationState = OperationState()
    private val replaceOperation: (HasMetadata) -> Unit = { replaceOperationState.operation(it) }
    private val resourceWatch: TestableResourceWatch = spy(TestableResourceWatch(
        watchOperations = LinkedBlockingDeque(),
        watchOperationsRunner = mock()
    ))
    private val watchListener = ResourceWatch.WatchListeners(addOperation, removeOperation, replaceOperation)
    private val podKind = ResourceKind.create(Pod::class.java)
    private val podWatchOpProvider = WatchOperationProvider<Pod>()
    private val namespaceKind = ResourceKind.create(Namespace::class.java)
    private val namespaceWatchOp = WatchOperationProvider<Namespace>()
    private val hasMetaKind1 = ResourceKind.create("v1", HasMetadata::class.java, "HasMetadata")
    private val hasMetaKind2 = ResourceKind.create("v2", HasMetadata::class.java, "HasMetadata")

    @Before
    fun before() {
        resourceWatch.watch(podKind, podWatchOpProvider::watch, watchListener)
        resourceWatch.watch(namespaceKind, namespaceWatchOp::watch, watchListener)
    }

    @Test
    fun `#watch() should call operation#watch`() {
        // given
        // when watch supplier is added - in @Before
        // then
        assertThat(podWatchOpProvider.isWatchCalled()).isTrue
    }

    @Test
    fun `#watch() should add watch`() {
        // given
        val kind: ResourceKind<Pod> = mock()
        assertThat(resourceWatch.watches.keys).doesNotContain(kind)
        // when
        resourceWatch.watch(kind, podWatchOpProvider::watch, watchListener)
        // then
        assertThat(resourceWatch.watches.keys).contains(kind)
    }

    @Test
    fun `#watch() should not add if operation is null`() {
        // given
        assertThat(resourceWatch.watches.keys).doesNotContain(hasMetaKind1)
        val sizeBeforeAdd = resourceWatch.watches.keys.size
        // when
        resourceWatch.watch(hasMetaKind1, WatchOperationProvider<HasMetadata>(null)::watch, watchListener)
        // then
        verify(resourceWatch.watches, never()).put(eq(hasMetaKind1), any())
        assertThat(resourceWatch.watches.keys.size).isEqualTo(sizeBeforeAdd)
    }

    @Test
    fun `#watch() should not add if kind already exists`() {
        // given
        val existing = WatchOperationProvider<Pod>().watch
        resourceWatch.watches[podKind] = existing
        val new = WatchOperationProvider<HasMetadata>()
        // when
        resourceWatch.watch(podKind, new::watch, watchListener)
        // then
        val watch = resourceWatch.watches[podKind]
        assertThat(watch).isEqualTo(existing)
    }

    @Test
    fun `#getWatched() should return all watched kinds`() {
        // given
        assertThat(resourceWatch.watches.keys).contains(podKind)
        assertThat(resourceWatch.watches.keys).contains(namespaceKind)
        // when
        val watched = resourceWatch.getWatched()
        // then
        assertThat(watched).contains(podKind, namespaceKind)
    }

    @Test
    fun `#stopWatch() should close removed watch`() {
        // given
        // when starting 2nd time
        resourceWatch.stopWatch(podKind)
        // then
        assertThat(podWatchOpProvider.watch?.isClosed()).isTrue
    }

    @Test
    fun `#stopWatch() should not close remaining watches`() {
        // given
        val notRemoved = WatchOperationProvider<HasMetadata>()
        resourceWatch.watch(hasMetaKind1, notRemoved::watch, watchListener)
        // when starting 2nd time
        resourceWatch.stopWatch(podKind)
        // then
        assertThat(notRemoved.watch?.isClosed()).isFalse
    }

    @Test
    fun `#stopWatch() should remove watch`() {
        // given
        val toRemove = WatchFake()
        resourceWatch.watch(hasMetaKind1, WatchOperationProvider<HasMetadata>(toRemove)::watch, watchListener)
        assertThat(resourceWatch.watches.values).contains(toRemove)
        // when starting 2nd time
        resourceWatch.stopWatch(hasMetaKind1)
        // then
        assertThat(resourceWatch.watches.values).doesNotContain(toRemove)
    }

    @Test
    fun `#stopWatchAll() should continue stopping if previous close throws`() {
        // given watch throws
        val watch1 = spy(WatchFake())
        whenever(watch1.close()).thenThrow(KubernetesClientException::class.java)
        val watchOpProvider1 = WatchOperationProvider<HasMetadata>(watch1)
        val watch2 = spy(WatchFake())
        val watchOpProvider2 = WatchOperationProvider<Pod>(watch2)
        resourceWatch.watch(hasMetaKind1, watchOpProvider1::watch, watchListener)
        resourceWatch.watch(hasMetaKind2, watchOpProvider2::watch, watchListener)
        // when
        resourceWatch.stopWatchAll(listOf(hasMetaKind1, hasMetaKind2))
        // then watch was closed
        verify(watch1).close()
        verify(watch2).close()
    }

    @Test
    fun `#addOperation should get called if watch notifies ADDED`() {
        // given
        val resource: Pod = resource("some HasMetadata", "ns1", "somePodUid", "v1")
        // when
        podWatchOpProvider.watcher?.eventReceived(Watcher.Action.ADDED, resource)
        // then
        assertThat(addOperationState.wasInvokedWithResource(resource)).isTrue
        assertThat(removeOperationState.wasInvoked()).isFalse
    }

    @Test
    fun `#removeOperation should get invoked if watch notifies REMOVED`() {
        // given
        val resource: Pod = resource("some HasMetadata", "ns1", "somePodUid", "v1")
        // when
        podWatchOpProvider.watcher?.eventReceived(Watcher.Action.DELETED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isTrue
        assertThat(addOperationState.wasInvoked()).isFalse
    }

    @Test
    fun `should not invoke any operation if watch notifies action that is not ADD, REMOVE or MODIFIED`() {
        // given
        val resource: Pod = resource("some HasMetadata", "ns1", "somePodUid", "v1")
        // when
        podWatchOpProvider.watcher?.eventReceived(Watcher.Action.ERROR, resource)
        // then
        assertThat(removeOperationState.wasInvoked()).isFalse
        assertThat(addOperationState.wasInvoked()).isFalse
        assertThat(replaceOperationState.wasInvoked()).isFalse
    }

    @Test
    fun `#replaceOperation should get invoked if watch notifies MODIFIED`() {
        // given
        val resource: Pod = resource("some HasMetadata", "ns1", "somePodUid", "v1")
        // when
        podWatchOpProvider.watcher?.eventReceived(Watcher.Action.MODIFIED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isFalse
        assertThat(addOperationState.wasInvoked()).isFalse
        assertThat(replaceOperationState.wasInvokedWithResource(resource)).isTrue
    }

    @Test
    fun `#close() should close existing watches`() {
        // given
        // when starting 2nd time
        resourceWatch.close()
        // then
        assertThat(podWatchOpProvider.watch?.isClosed()).isTrue
        assertThat(namespaceWatchOp.watch?.isClosed()).isTrue
    }

    class TestableResourceWatch(
        watchOperations: BlockingDeque<WatchOperation<*>>,
        watchOperationsRunner: Runnable = mock()
    ): ResourceWatch<ResourceKind<out HasMetadata>>(watchOperations, watchOperationsRunner) {
        public override val watches = spy(super.watches)

        override fun watch(
            key: ResourceKind<out HasMetadata>,
            watchOperation: (watcher: Watcher<HasMetadata>) -> Watch?,
            watchListeners: WatchListeners
        ) {
            super.watch(key, watchOperation, watchListeners)
            val queuedOperation = watchOperations.pollFirst(10, TimeUnit.SECONDS)
            // run in sequence, not in separate thread
            queuedOperation?.run()
        }
    }

    private class OperationState {
        private var resource: HasMetadata? = null

        fun operation(resource: HasMetadata) {
            this.resource = resource
        }

        fun wasInvoked(): Boolean {
            return resource != null
        }

        fun wasInvokedWithResource(resource: HasMetadata?): Boolean {
            return resource == this.resource
        }
    }

    private class WatchOperationProvider<T: HasMetadata>(var watch: WatchFake? = WatchFake()) {
        var watcher: Watcher<in T>? = null

        fun watch(watcher: Watcher<in T>?): Watch? {
            this.watcher = watcher
            return watch
        }

        fun isWatchCalled(): Boolean {
            return watcher != null
        }
    }

    private class WatchFake: Watch {

        private var closed: Boolean = false

        override fun close() {
            closed = true
        }

        fun isClosed(): Boolean {
            return closed
        }
    }
}
