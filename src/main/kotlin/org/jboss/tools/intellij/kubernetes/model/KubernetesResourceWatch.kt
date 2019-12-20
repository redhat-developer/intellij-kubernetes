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

open class KubernetesResourceWatch(private val addOperation: (HasMetadata) -> Unit,
                              private val removeOperation: (HasMetadata) -> Unit) {

    private var watches: MutableList<Watch?> = mutableListOf()

    fun addAll(suppliers: List<WatchableResourceSupplier?>) {
        suppliers.forEach { add(it) }
    }

    fun add(supplier: WatchableResourceSupplier?) {
        watch(supplier)
    }

    fun clear() {
        closeWatches(watches)
    }

    private fun watch(supplier: WatchableResourceSupplier?): Watch? {
        try {
            return supplier?.invoke()?.watch(ResourceWatcher(addOperation, removeOperation))
        } catch(e: RuntimeException) {
            logger<KubernetesResourceWatch>().error(e)
            return null
        }
    }

    private fun closeWatches(watches: MutableList<Watch?>) {
        watches.removeAll {
            it?.close()
            true
        }
    }

    private class ResourceWatcher<R: HasMetadata>(private val addOperation: (R) -> Unit,
                                                   private val removeOperation: (R) -> Unit) : Watcher<R> {
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
