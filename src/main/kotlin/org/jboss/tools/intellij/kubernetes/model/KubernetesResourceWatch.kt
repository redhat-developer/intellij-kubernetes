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
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import java.util.function.Consumer

class KubernetesResourceWatch(private val addOperation: Consumer<in HasMetadata>) {

    private var watches: MutableList<Watch> = mutableListOf()

    fun start(client: NamespacedKubernetesClient) {
        stop()
        watches.add(watch(client))
    }

    fun stop() {
        stopWatch(watches)
    }

    private fun watch(client: NamespacedKubernetesClient): Watch {
        return watchNamespaces(client)
    }

    private fun watchNamespaces(client: NamespacedKubernetesClient): Watch {
        return client.namespaces().watch(object : Watcher<Namespace> {
            override fun eventReceived(action: Watcher.Action, namespace: Namespace) {
                when (action) {
                    Watcher.Action.ADDED -> {
                        addOperation.accept(namespace)
                    }
                }
            }

            override fun onClose(cause: KubernetesClientException?) {
            }
        })
    }

    private fun stopWatch(watches: MutableList<Watch>) {
        watches.removeAll {
            it.close()
            true
        }
    }

}
