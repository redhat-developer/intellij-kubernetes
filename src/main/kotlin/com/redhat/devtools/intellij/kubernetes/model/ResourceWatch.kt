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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import java.util.*
import java.util.concurrent.BlockingDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

/**
 * A watch that listens for changes on the kubernetes cluster and operates actions on a model accordingly.
 * The model is only visible to this watcher by operations that the former provides (addOperation, removeOperation).
 */
open class ResourceWatch<T>(
    protected val watchOperations: BlockingDeque<WatchOperation<*>> = LinkedBlockingDeque(),
    watchOperationsRunner: Runnable = WatchOperationsRunner(watchOperations)
) {
    companion object {
        @JvmField val WATCH_OPERATION_ENQUEUED: Watch = Watch {  }
    }

    protected open val watches: ConcurrentHashMap<T, Watch?> = ConcurrentHashMap()
    private val executor: ExecutorService = Executors.newWorkStealingPool(20)
    private val thread = executor.submit(watchOperationsRunner)

    open fun watchAll(
        toWatch: Collection<Pair<T, (watcher: Watcher<in HasMetadata>) -> Watch?>>,
        watchListeners: WatchListeners
    ) {
        toWatch.forEach { watch(it.first, it.second, watchListeners) }
    }

    open fun watch(
        key: T,
        watchOperation: (watcher: Watcher<HasMetadata>) -> Watch?,
        watchListeners: WatchListeners
    ) {
        watches.computeIfAbsent(key) {
            logger<ResourceWatch<*>>().debug("Enqueueing watch for $key resources.")
            val operation = WatchOperation(
                    key,
                    watchOperation,
                    watches,
                    watchListeners.added,
                    watchListeners.removed,
                    watchListeners.replaced)
            watchOperations.add(operation) // enqueue watch operation
            WATCH_OPERATION_ENQUEUED // Marker: watch operation submitted
        }
    }

    fun stopWatchAll(keys: Collection<T>): Collection<T> {
        val existing = watches.entries.filter { keys.contains(it.key) }
        closeAll(existing)
        return existing.map { it.key }
    }

    fun stopWatch(key: T): Watch? {
        try {
            if (key == null) {
                return null
            }
            logger<ResourceWatch<*>>().debug("Closing watch for $key resource(s).")
            val watch = watches[key] ?: return null
            watch.close()
            return watches.remove(key)
        } catch (e: Exception) {
            logger<ResourceWatch<*>>().warn("Could not close watch for $key resources", e)
            return null
        }
    }

    fun getWatched(): Collection<T> {
        return Collections.list(watches.keys())
    }

    fun close() {
        closeAll(watches.entries.toList())
    }

    private fun closeAll(entries: Collection<MutableMap.MutableEntry<T, Watch?>>) {
        val closed = entries.filter {
            val watch = it.value
            if (watch == null) {
                false
            } else {
                safeClose(it.key, watch)
            }
        }
        closed.forEach { watches.remove(it.key) }
    }

    private fun safeClose(type: T, watch: Watch): Boolean {
        return try {
            logger<ResourceWatch<*>>().debug("Closing watch for $type resources.")
            watch.close()
            true
        } catch (e: Exception) {
            logger<ResourceWatch<*>>().warn("Error when closing watch for $type resources.", e)
            // do as if close() worked so that watch gets removed
            true
        }
    }

    private class WatchOperationsRunner(private val watchOperations: BlockingDeque<WatchOperation<*>>) : Runnable {
        override fun run() {
            while (true) {
                val op = watchOperations.take()
                logger<ResourceWatch<*>>().debug("Executing watch operation for ${op.key} resource(s).")
                op.run()
            }
        }
    }

    class WatchOperation<out T>(
            val key: T,
            private val watchOperation: (watcher: Watcher<HasMetadata>) -> Watch?,
            private val watches: MutableMap<T, Watch?>,
            private val addOperation: (HasMetadata) -> Unit,
            private val removeOperation: (HasMetadata) -> Unit,
            private val replaceOperation: (HasMetadata) -> Unit
    ) : Runnable {
        override fun run() {
            try {
                logger<ResourceWatch<*>>().debug("Watching $key resource(s).")
                val watch: Watch? = watchOperation.invoke(ResourceWatcher(addOperation, removeOperation, replaceOperation))
                saveWatch(watch)
            } catch (e: Exception) {
                watches.remove(key) // remove placeholder
                logger<ResourceWatch<*>>().warn("Could not watch resource(s) $key.", e)
            }
        }

        private fun saveWatch(watch: Watch?) {
            if (watch == null) {
                watches.remove(key) // remove placeholder
            } else {
                logger<ResourceWatch<*>>().debug("Created watch for $key resources.")
                watches[key] = watch // replace placeholder
            }
        }
    }

    class ResourceWatcher(
            private val addOperation: (HasMetadata) -> Unit,
            private val removeOperation: (HasMetadata) -> Unit,
            private val replaceOperation: (HasMetadata) -> Unit
    ) : Watcher<HasMetadata> {
        override fun eventReceived(action: Watcher.Action?, resource: HasMetadata) {
            logger<ResourceWatch<*>>().debug(
                    """Received $action event for ${resource.kind} '${resource.metadata.name}'
                            |"${if (resource.metadata.namespace != null) "in namespace ${resource.metadata.namespace}" else ""}.""")
            when (action) {
                Watcher.Action.ADDED ->
                    addOperation(resource)
                Watcher.Action.DELETED ->
                    removeOperation(resource)
                Watcher.Action.MODIFIED ->
                    replaceOperation(resource)
                else -> Unit
            }
        }

        override fun onClose(e: WatcherException?) {
            logger<ResourceWatch<*>>().debug("watcher closed.", e)
        }
    }

    class WatchListeners(
        val added: (HasMetadata) -> Unit,
        val removed: (HasMetadata) -> Unit,
        val replaced: (HasMetadata) -> Unit
    )

}
