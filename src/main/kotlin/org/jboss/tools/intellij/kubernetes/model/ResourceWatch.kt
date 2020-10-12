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
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import java.util.concurrent.BlockingDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

/**
 * A watch that listens for changes on the kubernetes cluster and operates actions on a model accordingly.
 * The model is only visible to this watcher by operations that the former provides (addOperation, removeOperation).
 */
open class ResourceWatch(
        private val addOperation: (HasMetadata) -> Unit,
        private val removeOperation: (HasMetadata) -> Unit,
        private val replaceOperation: (HasMetadata) -> Unit,
        protected val watchOperations: BlockingDeque<WatchOperation<*>> = LinkedBlockingDeque(),
        watchOperationsRunner: Runnable = WatchOperationsRunner(watchOperations)
) {
    companion object {
        @JvmField val WATCH_OPERATION_ENQUEUED: Watch = Watch {  }
        @JvmField val NULL_WATCHABLE: Watch = Watch {  }
    }

    protected open val watches: ConcurrentHashMap<ResourceKind<*>, Watch?> = ConcurrentHashMap()
    private val executor: ExecutorService = Executors.newWorkStealingPool(10)
    private val watchOperationsRunner = executor.submit(watchOperationsRunner)

    open fun watch(kind: ResourceKind<out HasMetadata>, supplier: () -> Watchable<Watch, Watcher<in HasMetadata>>?) {
        val watchable = supplier.invoke() ?: return
        watches.computeIfAbsent(kind) {
            logger<ResourceWatch>().debug("Enqueueing watch for $kind resources in $watchable.")
            val watchOperation = WatchOperation(
                    kind,
                    watchable,
                    watches,
                    addOperation,
                    removeOperation,
                    replaceOperation)
            watchOperations.add(watchOperation) // enqueue watch operation
            WATCH_OPERATION_ENQUEUED // Marker: watch operation submitted
        }
    }

    fun ignoreAll(kinds: Collection<ResourceKind<*>>): Collection<ResourceKind<*>> {
        val existing = watches.entries.filter { kinds.contains(it.key) }
        closeAll(existing)
        return existing.map { it.key }
    }

    fun ignore(kind: ResourceKind<*>) {
        try {
            logger<ResourceWatch>().debug("Closing watch for $kind resources.")
            val watch = watches[kind] ?: return
            watch.close()
            watches.remove(kind)
        } catch (e: Exception) {
            logger<ResourceWatch>().warn("Could not close watch for $kind resources", e)
        }
    }

    fun clear() {
        closeAll(watches.entries.toList())
    }

    private fun closeAll(entries: List<MutableMap.MutableEntry<ResourceKind<*>, Watch?>>) {
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

    private fun safeClose(kind: ResourceKind<*>, watch: Watch): Boolean {
        return try {
            logger<ResourceWatch>().debug("Closing watch for $kind resources.")
            watch.close()
            true
        } catch (e: KubernetesClientException) {
            logger<ResourceWatch>().warn("Failed to close watch for $kind resources.", e)
            false
        }
    }

    private class WatchOperationsRunner(private val watchOperations: BlockingDeque<WatchOperation<*>>) : Runnable {
        override fun run() {
            while (true) {
                val op = watchOperations.take()
                logger<ResourceWatch>().debug("Executing watch operation for ${op.kind} resources.")
                op.run()
            }
        }
    }

    class WatchOperation<R: HasMetadata>(
            val kind: ResourceKind<out R>,
            private val watchable: Watchable<Watch, Watcher<in R>>?,
            private val watches: MutableMap<ResourceKind<*>, Watch?>,
            private val addOperation: (HasMetadata) -> Unit,
            private val removeOperation: (HasMetadata) -> Unit,
            private val replaceOperation: (HasMetadata) -> Unit
    ) : Runnable {
        override fun run() {
            try {
                logger<ResourceWatcher>().debug("Watching $kind resources.")
                val watch: Watch = createWatch()
                saveWatch(watch)
            } catch (e: Exception) {
                watches.remove(kind) // remove placeholder
                logger<ResourceWatcher>().warn("Could not watch resource $kind.", e)
            }
        }

        private fun createWatch(): Watch {
            return if (watchable == null) {
                NULL_WATCHABLE
            } else {
                watchable?.watch(ResourceWatcher(addOperation, removeOperation, replaceOperation))
            }
        }

        private fun saveWatch(watch: Watch) {
            if (watch == null) {
                watches.remove(kind) // remove placeholder
            } else {
                logger<ResourceWatcher>().debug("Created watch for $kind resources.")
                watches[kind] = watch // replace placeholder
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

        override fun onClose(e: KubernetesClientException?) {
            logger<ResourceWatcher>().debug("watcher closed.", e)
        }
    }
}
