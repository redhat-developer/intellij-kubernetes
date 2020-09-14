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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable

/**
 * A watch that listens for changes on the kubernetes cluster and operates actions on a model accordingly.
 * The model is only visible to this watcher by operations that the former provides (addOperation, removeOperation).
 */
open class ResourceWatch(
    private val addOperation: (HasMetadata) -> Unit,
    private val removeOperation: (HasMetadata) -> Unit
) {
    private var watches: MutableMap<Watchable<Watch, Watcher<in HasMetadata>>, Watch?> = mutableMapOf()

    fun watchAll(suppliers: Collection<() -> Watchable<Watch, Watcher<in HasMetadata>>?>) {
        suppliers.forEach {
            try {
                doWatch(it)
            } catch (e: Exception) {
                logger<ResourceWatch>().warn("Could not watch $it", e)
            }
        }
    }

    fun watch(supplier: () -> Watchable<Watch, Watcher<in HasMetadata>>?) {
        try {
            doWatch(supplier)
        } catch(e: Exception){
            throw ResourceException("Could not watch resource.", e)
        }
    }

    private fun doWatch(supplier: () -> Watchable<Watch, Watcher<in HasMetadata>>?) {
        val watchable = supplier.invoke() ?: return
        watches.computeIfAbsent(watchable) { watchable ->
            watchable.watch(ResourceWatcher(addOperation, removeOperation))
        }
    }

    fun getAll(): List<Watchable<Watch, Watcher<in HasMetadata>>> {
        return watches.keys.toList()
    }

    fun ignoreAll(suppliers: Collection<() -> Watchable<Watch, Watcher<in HasMetadata>>?>) {
        suppliers.forEach {
            ignore(it)
        }
    }

    fun ignore(supplier: () -> Watchable<Watch, Watcher<in HasMetadata>>?) {
        try {
            val watchable = supplier.invoke() ?: return
            watches[watchable]?.close()
            watches.remove(watchable)
        } catch(e: Exception){
            logger<ResourceWatch>().warn("Could not stop watching resource $supplier", e)
        }
    }

    fun clear() {
        closeAll()
    }

    private fun closeAll() {
        val closed = watches.entries.filter {
                val watch = it.value
                if (watch == null) {
                    false
                } else {
                    safeClose(watch)
                }
            }
        closed.forEach { watches.remove( it.key) }
    }

    private fun safeClose(watch: Watch): Boolean {
        return try {
            watch.close()
            true
        } catch (e: KubernetesClientException) {
            logger<ResourceWatch>().error(e)
            false
        }
    }

    private inner class ResourceWatcher<R: HasMetadata>(
        private val addOperation: (HasMetadata) -> Unit,
        private val removeOperation: (HasMetadata) -> Unit
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
