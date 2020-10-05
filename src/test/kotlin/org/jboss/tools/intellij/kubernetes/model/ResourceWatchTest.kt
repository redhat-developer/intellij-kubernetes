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
package org.jboss.tools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.junit.Before
import org.junit.Test
import java.util.concurrent.BlockingDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class ResourceWatchTest {

    private val addOperationState = OperationState()
    private val addOperation: (HasMetadata) -> Unit = { addOperationState.operation(it) }
    private val removeOperationState = OperationState()
    private val removeOperation: (HasMetadata) -> Unit = { removeOperationState.operation(it) }
    private val replaceOperationState = OperationState()
    private val replaceOperation: (HasMetadata) -> Unit = { replaceOperationState.operation(it) }
    private val resourceWatch: TestableResourceWatch = spy(TestableResourceWatch(
        addOperation = addOperation,
        removeOperation = removeOperation,
        replaceOperation = replaceOperation,
        watchOperations = LinkedBlockingDeque<Runnable>(),
        watchOperationsRunner = mock()
    ))
    private val podKind = ResourceKind.new(Pod::class.java)
    private val watchable1 = WatchableFake()
    private val namespaceKind = ResourceKind.new(Namespace::class.java)
    private val watchable2 = WatchableFake()
    private val hasMetaKind1 = ResourceKind.new("v1", HasMetadata::class.java, "HasMetadata")
    private val hasMetaKind2 = ResourceKind.new("v2", HasMetadata::class.java, "HasMetadata")

    @Before
    fun before() {
        resourceWatch.watch(podKind, { watchable1 })
        resourceWatch.watch(namespaceKind, { watchable2 })
    }

    @Test
    fun `#watch() should call watchable#watch() on supplied watchable`() {
        // given
        // when watch supplier is added - in @Before
        // then
        assertThat(watchable1.isWatchCalled()).isTrue()
    }

    @Test
    fun `#watch() should add watch`() {
        // given
        val kind: ResourceKind<Pod> = mock()
        val watchable = WatchableFake()
        assertThat(resourceWatch.watches.keys).doesNotContain(kind)
        // when
        resourceWatch.watch(kind, { watchable })
        // then
        assertThat(resourceWatch.watches.keys).contains(kind)
    }

    @Test
    fun `#watch() should not add if supplier is null`() {
        // given
        assertThat(resourceWatch.watches.keys).doesNotContain(hasMetaKind1)
        val sizeBeforeAdd = resourceWatch.watches.keys.size
        // when
        resourceWatch.watch(hasMetaKind1, { null })
        // then
        verify(resourceWatch.watches, never()).put(eq(hasMetaKind1), any())
        assertThat(resourceWatch.watches.keys.size).isEqualTo(sizeBeforeAdd)
    }

    @Test
    fun `#watch() should not add if kind already exists`() {
        // given
        val existing = WatchableFake().watch
        resourceWatch.watches[podKind] = existing
        val new = WatchableFake()
        // when
        resourceWatch.watch(podKind, { new })
        // then
        val watch = resourceWatch.watches[podKind]
        assertThat(watch).isEqualTo(existing)
    }

    @Test
    fun `#ignore() should close removed watch`() {
        // given
        // when starting 2nd time
        resourceWatch.ignore(podKind)
        // then
        assertThat(watchable1.watch.isClosed()).isTrue()
    }

    @Test
    fun `#ignore() should not close remaining watches`() {
        // given
        val notRemoved = WatchableFake()
        resourceWatch.watch(hasMetaKind1, { notRemoved })
        // when starting 2nd time
        resourceWatch.ignore(podKind)
        // then
        assertThat(notRemoved.watch.isClosed()).isFalse()
    }

    @Test
    fun `#ignore() should remove watch`() {
        // given
        val toRemove = WatchFake()
        resourceWatch.watch(hasMetaKind1, { WatchableFake(toRemove) })
        assertThat(resourceWatch.watches.values).contains(toRemove)
        // when starting 2nd time
        resourceWatch.ignore(hasMetaKind1)
        // then
        assertThat(resourceWatch.watches.values).doesNotContain(toRemove)
    }

    @Test
    fun `#ignoreAll() should continue ignoring if previous watchable throws`() {
        // given watchable1 throws
        val watch1 = spy(WatchFake())
        whenever(watch1.close()).thenThrow(KubernetesClientException::class.java)
        val watchable1 = WatchableFake(watch1)
        val watch2 = spy(WatchFake())
        val watchable2 = WatchableFake(watch2)
        resourceWatch.watch(hasMetaKind1, { watchable1 })
        resourceWatch.watch(hasMetaKind2, { watchable2 })
        // when
        resourceWatch.ignoreAll(listOf(hasMetaKind1, hasMetaKind2))
        // then watchable2 was closed
        verify(watch1).close()
        verify(watch2).close()
    }

    @Test
    fun `#addOperation should get called if watch notifies ADDED`() {
        // given
        val resource: HasMetadata = resource("some HasMetadata")
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.ADDED, resource)
        // then
        assertThat(addOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(removeOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#removeOperation should get invoked if watch notifies REMOVED`() {
        // given
        val resource: HasMetadata = resource("some HasMetadata")
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.DELETED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should not invoke any operation if watch notifies action that is not ADD, REMOVE or MODIFIED`() {
        // given
        val resource: HasMetadata = resource("some HasMetadata")
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.ERROR, resource)
        // then
        assertThat(removeOperationState.wasInvoked()).isFalse()
        assertThat(addOperationState.wasInvoked()).isFalse()
        assertThat(replaceOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#replaceOperation should get invoked if watch notifies MODIFIED`() {
        // given
        val resource: HasMetadata = resource("some HasMetadata")
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.MODIFIED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isFalse()
        assertThat(addOperationState.wasInvoked()).isFalse()
        assertThat(replaceOperationState.wasInvokedWithResource(resource)).isTrue()
    }

    @Test
    fun `#clear() should close existing watches`() {
        // given
        // when starting 2nd time
        resourceWatch.clear()
        // then
        assertThat(watchable1.watch.isClosed())
        assertThat(watchable2.watch.isClosed())
    }

    class TestableResourceWatch(
            addOperation: (HasMetadata) -> Unit,
            removeOperation: (HasMetadata) -> Unit,
            replaceOperation: (HasMetadata) -> Unit,
            watchOperations: BlockingDeque<Runnable>,
            watchOperationsRunner: Runnable = mock()
    ): ResourceWatch(addOperation, removeOperation, replaceOperation, watchOperations, watchOperationsRunner) {
        public override val watches: ConcurrentHashMap<ResourceKind<*>, Watch?> = spy(super.watches)

        override fun watch(kind: ResourceKind<out HasMetadata>, supplier: () -> Watchable<Watch, Watcher<in HasMetadata>>?) {
            super.watch(kind, supplier)
            val watchOperation = watchOperations.pollFirst(10, TimeUnit.SECONDS)
            // run in sequence, not in separate thread
            watchOperation?.run()
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

    private class WatchableFake (var watch: WatchFake = WatchFake()) : Watchable<Watch, Watcher<in HasMetadata>> {

        var watcher: Watcher<in HasMetadata>? = null

        override fun watch(watcher: Watcher<in HasMetadata>?): Watch {
            this.watcher = watcher
            return watch
        }

        override fun watch(options: ListOptions?, watcher: Watcher<in HasMetadata>?): Watch {
            return watch(watcher)
        }

        override fun watch(resourceVersion: String?, watcher: Watcher<in HasMetadata>): Watch {
            return watch(watcher)
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
