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
import io.fabric8.kubernetes.client.dsl.Watchable
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import java.util.concurrent.BlockingDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.function.Supplier

/**
 * A watch that listens for changes on the kubernetes cluster and operates actions on a model accordingly.
 * The model is only visible to this watcher by operations that the former provides (addOperation, removeOperation).
 */
open class ResourceWatch(
        protected val watchOperations: BlockingDeque<WatchOperation> = LinkedBlockingDeque(),
        watchOperationsRunner: Runnable = WatchOperationsRunner(watchOperations)
) {
    companion object {
        @JvmField val WATCH_OPERATION_ENQUEUED: Watch = Watch {  }
    }

    protected open val watches: ConcurrentHashMap<Any, Watch?> = ConcurrentHashMap()
    private val executor: ExecutorService = Executors.newWorkStealingPool(10)
    private val thread = executor.submit(watchOperationsRunner)

    open fun watchAll(
        toWatch: Collection<Pair<ResourceKind<out HasMetadata>, (watcher: Watcher<out HasMetadata>) -> Watch?>>,
        watchListeners: WatchListeners
    ) {
        toWatch.forEach { watch(it.first, it.second, watchListeners) }
    }

    open fun watch(
        key: Any,
        watchOperation: (watcher: Watcher<out HasMetadata>) -> Watch?,
        watchListeners: WatchListeners
    ) {
        watches.computeIfAbsent(key) {
            logger<ResourceWatch>().debug("Enqueueing watch for $key resources.")
            val watchOperation = WatchOperation(
                    key,
                    watchOperation,
                    watches,
                    watchListeners.add,
                    watchListeners.remove,
                    watchListeners.replace)
            watchOperations.add(watchOperation) // enqueue watch operation
            WATCH_OPERATION_ENQUEUED // Marker: watch operation submitted
        }
    }

    fun stopWatchAll(kinds: Collection<ResourceKind<*>>): Collection<Any> {
        val existing = watches.entries.filter { kinds.contains(it.key) }
        closeAll(existing)
        return existing.map { it.key }
    }

    fun stopWatch(kind: ResourceKind<*>) {
        try {
            logger<ResourceWatch>().debug("Closing watch for $kind resources.")
            stopWatch(kind)
        } catch (e: Exception) {
            logger<ResourceWatch>().warn("Could not close watch for $kind resources", e)
        }
    }

    fun stopWatch(resource: HasMetadata) {
        try {
            logger<ResourceWatch>().debug("Closing watch for resource ${resource.metadata.namespace}/${resource.metadata.name}.")
            stopWatch(resource)
        } catch (e: Exception) {
            logger<ResourceWatch>().warn("Could not close watch for resource ${resource.metadata.namespace}/${resource.metadata.name}", e)
        }
    }

    private fun stopWatch(key: Any) {
        val watch = watches[key] ?: return
        watch.close()
        watches.remove(key)
    }

    fun close() {
        closeAll(watches.entries.toList())
    }

    private fun closeAll(entries: List<MutableMap.MutableEntry<Any, Watch?>>) {
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

    private fun safeClose(type: Any, watch: Watch): Boolean {
        return try {
            logger<ResourceWatch>().debug("Closing watch for $type resources.")
            watch.close()
            true
        } catch (e: KubernetesClientException) {
            logger<ResourceWatch>().warn("Failed to close watch for $type resources.", e)
            false
        }
    }

    private class WatchOperationsRunner(private val watchOperations: BlockingDeque<WatchOperation>) : Runnable {
        override fun run() {
            while (true) {
                val op = watchOperations.take()
                logger<ResourceWatch>().debug("Executing watch operation for ${op.key} resource(s).")
                op.run()
            }
        }
    }

    class WatchOperation(
            val key: Any,
            private val watchOperation: (watcher: Watcher<out HasMetadata>) -> Watch?,
            private val watches: MutableMap<Any, Watch?>,
            private val addOperation: (HasMetadata) -> Unit,
            private val removeOperation: (HasMetadata) -> Unit,
            private val replaceOperation: (HasMetadata) -> Unit
    ) : Runnable {
        override fun run() {
            try {
                logger<ResourceWatcher>().debug("Watching $key resource(s).")
                val watch: Watch? = watchOperation.invoke(ResourceWatcher(addOperation, removeOperation, replaceOperation))
                saveWatch(watch)
            } catch (e: Exception) {
                watches.remove(key) // remove placeholder
                logger<ResourceWatcher>().warn("Could not watch resource(s) $key.", e)
            }
        }

        private fun saveWatch(watch: Watch?) {
            if (watch == null) {
                watches.remove(key) // remove placeholder
            } else {
                logger<ResourceWatcher>().debug("Created watch for $key resources.")
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
            logger<ResourceWatcher>().debug(
                    """Received $action event for ${resource.kind} ${resource.metadata.name}
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
            logger<ResourceWatcher>().debug("watcher closed.", e)
        }
    }

    class WatchListeners(
        val add: (HasMetadata) -> Unit,
        val remove: (HasMetadata) -> Unit,
        val replace: (HasMetadata) -> Unit
    )

}
