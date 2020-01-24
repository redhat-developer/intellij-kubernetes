/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class KubernetesResourceWatchTest {

    private val addOperationState = OperationState()
    private val addOperation: (HasMetadata) -> Unit = { addOperationState.operation(it) }
    private val removeOperationState = OperationState()
    private val removeOperation: (HasMetadata) -> Unit = { removeOperationState.operation(it) }
    private val watch: KubernetesResourceWatch = KubernetesResourceWatch(
        addOperation = addOperation,
        removeOperation = removeOperation)
    private val watchable = WatchableFake()

    @Before
    fun before() {
        watch.add { watchable }
    }

    @Test
    fun `#add() should call watchable#watch() on supplied watchable`() {
        // given
        // when watch supplier is added - in @Before
        // then
        assertThat(watchable.isWatchCalled()).isTrue()
    }

    @Test
    fun `#add() should add watch`() {
        // given
        val toAdd = WatchableFake()
        assertThat(watch.getAllWatched()).doesNotContain(toAdd)
        // when
        watch.add { toAdd }
        // then
        assertThat(watch.getAllWatched()).contains(toAdd)
    }

    @Test
    fun `#add() should not add if supplier is null`() {
        // given
        val sizeBeforeAdd = watch.getAllWatched().size
        // when
        watch.add(null)
        // then
        assertThat(watch.getAllWatched().size).isEqualTo(sizeBeforeAdd)
    }

    @Test
    fun `#addAll() should call watchable#watch() on each supplied watchable`() {
        // given
        val watchable1 = WatchableFake()
        val watchable2 = WatchableFake()
        // when
        watch.addAll(listOf( { watchable1 }, { watchable2 }))
        // then
        assertThat(watchable1.isWatchCalled()).isTrue()
        assertThat(watchable2.isWatchCalled()).isTrue()
    }

    @Test
    fun `#remove() should close removed watch`() {
        // given
        // when starting 2nd time
        watch.remove { watchable }
        // then
        assertThat(watchable.watch.isClosed()).isTrue()
    }

    @Test
    fun `#remove() should not close remaining watches`() {
        // given
        val notRemoved = WatchableFake()
        watch.add{ notRemoved }
        // when starting 2nd time
        watch.remove { watchable }
        // then
        assertThat(notRemoved.watch.isClosed()).isFalse()
    }

    @Test
    fun `#remove() should remove watch`() {
        // given
        val toRemove = WatchableFake()
        watch.add{ toRemove }
        assertThat(watch.getAllWatched()).contains(toRemove)
        // when starting 2nd time
        watch.remove { toRemove }
        // then
        assertThat(watch.getAllWatched()).doesNotContain(toRemove)
    }

    @Test
    fun `#addOperation should get called if watch notifies ADDED`() {
        // given
        val resource: HasMetadata = mock()
        // when
        watchable.watcher?.eventReceived(Watcher.Action.ADDED, resource)
        // then
        assertThat(addOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(removeOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#removeOperation should get invoked if watch notifies REMOVED`() {
        // given
        val resource: HasMetadata = mock()
        // when
        watchable.watcher?.eventReceived(Watcher.Action.DELETED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should not invoke any operation if watch notifies action that is not ADD or REMOVE`() {
        // given
        val resource: HasMetadata = mock()
        // when
        watchable.watcher?.eventReceived(Watcher.Action.ERROR, resource)
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
        assertThat(watchable.watch.isClosed())
    }

    private class OperationState() {
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

    private class WatchableFake : Watchable<Watch, Watcher<in HasMetadata>> {

        var watcher: Watcher<in HasMetadata>? = null
        var watch: WatchFake = WatchFake()

        override fun watch(watcher: Watcher<in HasMetadata>?): Watch {
            this.watcher = watcher
            return watch
        }

        override fun watch(resourceVersion: String?, watcher: Watcher<in HasMetadata>?): Watch {
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
