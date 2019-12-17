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

class KubernetesResourceWatch<W: Watchable<Watch, Watcher<in HasMetadata>>>(
    private val addOperation: (HasMetadata) -> Unit,
    private val removeOperation: (HasMetadata) -> Unit) {

    private var watches: MutableList<Watch?> = mutableListOf()

    fun <S: () -> W> start(watchSuppliers: Collection<S?>) {
        stop()
        watches.addAll(watch(watchSuppliers))
    }

    fun stop() {
        stopWatch(watches)
    }

    private fun <S: () -> W> watch(watchSuppliers: Collection<S?>): Collection<Watch?> {
        return watchSuppliers.map { watch(it) }
    }

    private fun <S: () -> W> watch(watchSupplier: S?): Watch? {
        try {
            return watchSupplier?.let { it() }?.watch(ResourceWatcher(addOperation, removeOperation))
        } catch(e: RuntimeException) {
            logger<KubernetesResourceWatch<W>>().error(e)
            return null
        }
    }

    private fun stopWatch(watches: MutableList<Watch?>) {
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
