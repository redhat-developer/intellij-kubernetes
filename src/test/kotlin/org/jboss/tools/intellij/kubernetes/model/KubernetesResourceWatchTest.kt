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

/*
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class KubernetesResourceWatchTest {

    private val addOperationState = OperationState()
    private val addOperation: (HasMetadata) -> Unit = { addOperationState.operation(it) }
    private val removeOperationState = OperationState()
    private val removeOperation: (HasMetadata) -> Unit = { removeOperationState.operation(it) }
    private val watch: KubernetesResourceWatch =
        KubernetesResourceWatch(addOperation = addOperation, removeOperation = removeOperation)
    private val watchable = WatchableFake()

    @Before
    fun before() {
        watch.add { watchable }
    }

    @Test
    fun `should call watchable#watch when supplier is added`() {
        // given
        // when watch supplier is added - in @Before
        // then
        assertThat(watchable.isWatchCalled()).isTrue()
    }

    @Test
    fun `should invoke addOperation if watch notifies ADDED`() {
        // given
        val resource: HasMetadata = mockk(relaxed = true)
        // when
        watchable.watcher?.eventReceived(Watcher.Action.ADDED, resource)
        // then
        assertThat(addOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(removeOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should invoke removeOperation if watch notifies REMOVED`() {
        // given
        val resource: HasMetadata = mockk(relaxed = true)
        // when
        watchable.watcher?.eventReceived(Watcher.Action.DELETED, resource)
        // then
        assertThat(removeOperationState.wasInvokedWithResource(resource)).isTrue()
        assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should not invoke any operation if watch notifies action that is not ADD or REMOVE`() {
        // given
        val resource: HasMetadata = mockk(relaxed = true)
        // when
        watchable.watcher?.eventReceived(Watcher.Action.ERROR, resource)
        // then
        assertThat(removeOperationState.wasInvoked()).isFalse()
        assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#clear should close existing watches`() {
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
 */