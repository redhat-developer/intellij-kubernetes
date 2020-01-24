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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
typealias WatchableResource = Watchable<Watch, Watcher<in HasMetadata>>
typealias WatchableResourceSupplier = () -> WatchableResource

/**
 * A watch that listens for changes on the kubernetes cluster and operates actions on a model accordingly.
 * The model is only visible to this watcher by operations that the former provides (addOperation, removeOperation).
 */
open class KubernetesResourceWatch(
    private val addOperation: (HasMetadata) -> Unit,
    private val removeOperation: (HasMetadata) -> Unit
) {

    private var watches: MutableMap<WatchableResource, Watch?> = mutableMapOf()

    fun addAll(suppliers: List<WatchableResourceSupplier?>) {
        suppliers.forEach { add(it) }
    }

    fun add(supplier: WatchableResourceSupplier?) {
        val watchable = supplier?.invoke() ?: return
        val watch = watch(watchable) ?: return
        watches[watchable] = watch
    }

    fun remove(supplier: WatchableResourceSupplier?) {
        val watchable = supplier?.invoke() ?: return
        watches[watchable]?.close()
        watches.remove(watchable)
    }

    fun removeAll(suppliers: List<WatchableResourceSupplier?>) {
        suppliers.forEach { remove(it) }
    }

    fun clear() {
        closeAll()
    }

    fun getAllWatched(): List<WatchableResource> {
        return watches.keys.toList()
    }

    private fun watch(watchable: WatchableResource): Watch? {
        try {
            return watchable.watch(ResourceWatcher(addOperation, removeOperation))
        } catch(e: RuntimeException) {
            logger<KubernetesResourceWatch>().error(e)
            return null
        }
    }

    private fun closeAll() {
        watches.values.forEach {
            it?.close()
        }
    }

    private fun close(watchable: Watchable<*,*>) {
        watches.remove(watchable)
    }

    private class ResourceWatcher<R : HasMetadata>(
        private val addOperation: (R) -> Unit,
        private val removeOperation: (R) -> Unit
    ) : Watcher<R> {
        override fun eventReceived(action: Watcher.Action?, resource: R) {
            when (action) {
                Watcher.Action.ADDED ->
                    addOperation(resource)

                Watcher.Action.DELETED ->
                    removeOperation(resource)
                else -> Unit
            }
        }

        override fun onClose(cause: KubernetesClientException?) {
        }

    }

}
