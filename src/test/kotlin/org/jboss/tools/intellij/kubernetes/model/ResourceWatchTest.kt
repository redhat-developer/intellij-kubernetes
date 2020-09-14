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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks
import org.junit.Before
import org.junit.Test

class ResourceWatchTest {

    private val addOperationState = OperationState()
    private val addOperation: (HasMetadata) -> Unit = { addOperationState.operation(it) }
    private val removeOperationState = OperationState()
    private val removeOperation: (HasMetadata) -> Unit = { removeOperationState.operation(it) }
    private val watch: ResourceWatch = ResourceWatch(
        addOperation = addOperation,
        removeOperation = removeOperation)
    private val watchable1 = WatchableFake()
    private val watchable2 = WatchableFake()

    @Before
    fun before() {
        watch.watch { watchable1 }
        watch.watch { watchable2 }
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
        val toAdd = WatchableFake()
        assertThat(watch.getAll()).doesNotContain(toAdd)
        // when
        watch.watch { toAdd }
        // then
        assertThat(watch.getAll()).contains(toAdd)
    }

    @Test
    fun `#watch() should not add if supplier is null`() {
        // given
        val sizeBeforeAdd = watch.getAll().size
        // when
        watch.watch { null }
        // then
        assertThat(watch.getAll().size).isEqualTo(sizeBeforeAdd)
    }

    @Test
    fun `#watchAll() should call watchable#watch() on each supplied watchable`() {
        // given
        val watchable1 = WatchableFake()
        val watchable2 = WatchableFake()
        // when
        watch.watchAll(listOf( { watchable1 }, { watchable2 }))
        // then
        assertThat(watchable1.isWatchCalled()).isTrue()
        assertThat(watchable2.isWatchCalled()).isTrue()
    }

    @Test
    fun `#watchAll() should continue watching if previous watchable throws`() {
        // given watchable1 throws
        val watchable1 = spy(WatchableFake())
        whenever(watchable1.watch(any())).thenThrow(RuntimeException::class.java)
        val watchable2 = WatchableFake()
        // when
        watch.watchAll(listOf( { watchable1 }, { watchable2 }))
        // then watchable2 is still watched
        assertThat(watchable2.isWatchCalled()).isTrue()
    }

    @Test
    fun `#ignore() should close removed watch`() {
        // given
        // when starting 2nd time
        watch.ignore { watchable1 }
        // then
        assertThat(watchable1.watch.isClosed()).isTrue()
    }

    @Test
    fun `#ignore() should not close remaining watches`() {
        // given
        val notRemoved = WatchableFake()
        watch.watch{ notRemoved }
        // when starting 2nd time
        watch.ignore { watchable1 }
        // then
        assertThat(notRemoved.watch.isClosed()).isFalse()
    }

    @Test
    fun `#ignore() should remove watch`() {
        // given
        val toRemove = WatchableFake()
        watch.watch{ toRemove }
        assertThat(watch.getAll()).contains(toRemove)
        // when starting 2nd time
        watch.ignore { toRemove }
        // then
        assertThat(watch.getAll()).doesNotContain(toRemove)
    }

    @Test
    fun `#ignoreAll() should continue ignoring if previous watchable throws`() {
        // given watchable1 throws
        val watch1 = spy(WatchFake())
        whenever(watch1.close()).thenThrow(RuntimeException::class.java)
        val watchable1 = WatchableFake(watch1)
        val watchable2 = WatchableFake()
        watch.watchAll(listOf( { watchable1 }, { watchable2 }))
        // when
        watch.ignoreAll(listOf( { watchable1 }, { watchable2 }))
        // then watchable2 is still watched
        verify(watch1).close()
    }

    @Test
    fun `#addOperation should get called if watch notifies ADDED`() {
        // given
        val resource: HasMetadata = mock()
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.ADDED, resource)
        // then
        assertThat(addOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(removeOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#removeOperation should get invoked if watch notifies REMOVED`() {
        // given
        val resource: HasMetadata = mock()
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.DELETED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should not invoke any operation if watch notifies action that is not ADD or REMOVE`() {
        // given
        val resource = mock<HasMetadata>()
        // when
        watchable1.watcher?.eventReceived(Watcher.Action.ERROR, resource)
        // then
        assertThat(removeOperationState.wasInvoked()).isFalse()
        assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#clear() should close existing watches`() {
        // given
        // when starting 2nd time
        watch.clear()
        // then
        assertThat(watchable1.watch.isClosed())
        assertThat(watchable2.watch.isClosed())
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

    private class WatchableFake(var watch: WatchFake = WatchFake()) : Watchable<Watch, Watcher<in HasMetadata>> {

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
