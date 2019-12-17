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

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test

class KubernetesResourceWatchTest {

    private val addOperationState = OperationState()
    private val addOperation: (HasMetadata) -> Unit = { addOperationState.operation(it) }
    private val removeOperationState = OperationState()
    private val removeOperation: (HasMetadata) -> Unit = { removeOperationState.operation(it) }
    private val watch: KubernetesResourceWatch<Watchable<Watch, Watcher<in HasMetadata>>> =
        KubernetesResourceWatch(addOperation = addOperation, removeOperation = removeOperation)
    private val watchable = WatchableFake()

    @Before
    fun before() {
        watch.start(watchSuppliers = listOf { watchable })
    }

    @Test
    fun `should invoke addOperation if watch notifies ADDED`() {
        // given
        val resource: HasMetadata = mockk(relaxed = true)
        // when
        watchable.watcher?.eventReceived(Watcher.Action.ADDED, resource)
        // then
        Assertions.assertThat(addOperationState.wasInvokedWithResource(resource)).isTrue()
        Assertions.assertThat(removeOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should invoke removeOperation if watch notifies REMOVED`() {
        // given
        val resource: HasMetadata = mockk(relaxed = true)
        // when
        watchable.watcher?.eventReceived(Watcher.Action.DELETED, resource)
        // then
        Assertions.assertThat(removeOperationState.wasInvokedWithResource(resource)).isTrue()
        Assertions.assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `should not invoke anyOperation if watch notifies ERROR`() {
        // given
        val resource: HasMetadata = mockk(relaxed = true)
        // when
        watchable.watcher?.eventReceived(Watcher.Action.ERROR, resource)
        // then
        Assertions.assertThat(removeOperationState.wasInvoked()).isFalse()
        Assertions.assertThat(addOperationState.wasInvoked()).isFalse()
    }

    @Test
    fun `#start should close existing watches`() {
        // given
        // when starting 2nd time
        watch.start(watchSuppliers = listOf { WatchableFake() })
        // then
        watchable.verifyClosed()
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

        private val watch: Watch = mockk(relaxed = true)
        var watcher: Watcher<in HasMetadata>? = null

        override fun watch(watcher: Watcher<in HasMetadata>?): Watch {
            this.watcher = watcher
            return watch
        }

        override fun watch(resourceVersion: String?, watcher: Watcher<in HasMetadata>?): Watch {
            this.watcher = watcher
            return watch
        }

        fun verifyClosed() {
            verify { watch.close() }
        }
    }
}