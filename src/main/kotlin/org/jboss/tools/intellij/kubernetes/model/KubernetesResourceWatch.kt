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
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable

class KubernetesResourceWatch(private val addOperation: (HasMetadata) -> Unit,
                              private val removeOperation: (HasMetadata) -> Unit) {

    private var watches: MutableList<Watch?> = mutableListOf()

    fun start(client: NamespacedKubernetesClient) {
        stop()
        watches.addAll(arrayOf(
            watch{ client.namespaces() },
            watch{ client.pods().inAnyNamespace() }))
    }

    fun stop() {
        stopWatch(watches)
    }

    private fun <T: HasMetadata> watch(watchSupplier: () -> Watchable<Watch, Watcher<T>>): Watch? {
        try{
            return watchSupplier().watch(ResourceWatcher(addOperation, removeOperation))
        } catch(e: RuntimeException) {
            logger<KubernetesResourceWatch>().error(e)
            return null
        }
    }


    private fun stopWatch(watches: MutableList<Watch?>) {
        watches.removeAll {
            it?.close()
            true
        }
    }

    private class ResourceWatcher<T: HasMetadata>(private val addOperation: (T) -> Unit,
                                                   private val removeOperation: (T) -> Unit)
        : Watcher<T> {
        override fun eventReceived(action: Watcher.Action?, resource: T) {
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
