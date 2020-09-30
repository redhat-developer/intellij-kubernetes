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
        protected val watchOperations: BlockingDeque<Runnable> = LinkedBlockingDeque(),
        watchOperationsRunner: Runnable = WatchOperationsRunner(watchOperations)
) {
    companion object {
        @JvmField val WATCH_OPERATION_ENQUEUED: Watch = Watch {  }
    }

    protected open val watches: ConcurrentHashMap<ResourceKind<*>, Watch?> = ConcurrentHashMap()
    private val executor: ExecutorService = Executors.newWorkStealingPool(10)
    private val watchOperationsRunner = executor.submit(watchOperationsRunner)

    open fun watch(kind: ResourceKind<out HasMetadata>, supplier: () -> Watchable<Watch, Watcher<in HasMetadata>>?) {
        logger<ResourceWatcher>().info("Watching $kind resources.")
        watches.computeIfAbsent(kind) {
            val watchOperation = WatchOperation(kind,
                    supplier.invoke(),
                    watches,
                    addOperation,
                    removeOperation)
            watchOperations.add(watchOperation) // enqueue watch operation
            WATCH_OPERATION_ENQUEUED // Marker: watch operation submitted
        }
    }

    fun ignoreAll(kinds: Collection<ResourceKind<*>>) {
        closeAll(watches.entries.filter { kinds.contains(it.key) })
    }

    fun ignore(kind: ResourceKind<*>) {
        try {
            logger<ResourceWatcher>().info("Closing watch for $kind resources.")
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
            logger<ResourceWatch>().debug("Closing watch for $kind resources")
            watch.close()
            true
        } catch (e: KubernetesClientException) {
            logger<ResourceWatch>().warn("Failed to close watch for $kind resources", e)
            false
        }
    }

    private class WatchOperationsRunner(private val watchOperations: BlockingDeque<Runnable>) : Runnable {
        override fun run() {
            while (true) {
                val op = watchOperations.take()
                op.run()
            }
        }
    }

    protected class WatchOperation<R: HasMetadata>(
            private val kind: ResourceKind<out R>,
            private val watchable: Watchable<Watch, Watcher<in R>>?,
            private val watches: MutableMap<ResourceKind<*>, Watch?>,
            private val addOperation: (HasMetadata) -> Unit,
            private val removeOperation: (HasMetadata) -> Unit
    ) : Runnable {
        override fun run() {
            try {
                logger<ResourceWatcher>().debug("Trying to watch $kind resources.")
                val watch: Watch? = watchable?.watch(ResourceWatcher(addOperation, removeOperation))
                if (watch == null) {
                    watches.remove(kind) // remove placeholder
                } else {
                    watches[kind] = watch // replace placeholder
                    logger<ResourceWatcher>().debug("Successfully created watch for $kind resources.")
                }
            } catch (e: Exception) {
                watches.remove(kind) // remove placeholder
                logger<ResourceWatcher>().error("Could not watch resource $kind.", e)
            }
        }
    }

    class ResourceWatcher(
            private val addOperation: (HasMetadata) -> Unit,
            private val removeOperation: (HasMetadata) -> Unit
    ) : Watcher<HasMetadata> {
        override fun eventReceived(action: Watcher.Action?, resource: HasMetadata) {
            logger<ResourceWatcher>().debug("Watcher event: resource ${resource.metadata.name} in namespace ${resource.metadata.namespace} was $action.")
            when (action) {
                Watcher.Action.ADDED ->
                    addOperation(resource)
                Watcher.Action.DELETED ->
                    removeOperation(resource)
                else -> Unit
            }
        }

        override fun onClose(e: KubernetesClientException?) {
            logger<ResourceWatcher>().debug("Watcher event: watcher closed.", e)
        }
    }
}
