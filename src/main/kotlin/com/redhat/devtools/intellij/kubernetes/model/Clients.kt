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

import io.fabric8.kubernetes.client.AppsAPIGroupClient
import io.fabric8.kubernetes.client.BatchAPIGroupClient
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.ExtensionsAPIGroupClient
import io.fabric8.kubernetes.client.StorageAPIGroupClient
import io.fabric8.openshift.client.OpenShiftClient
import java.util.concurrent.ConcurrentHashMap

class Clients<C: Client>(private val client: C) {

    private val clients = ConcurrentHashMap<Class<out Client>, Client>()

    fun isOpenShift(): Boolean {
        return OpenShiftClient::class.java.isAssignableFrom(client.javaClass)
    }

    fun get(): C {
        return client
    }

    fun getApps(): AppsAPIGroupClient {
        return get(AppsAPIGroupClient::class.java)
    }

    fun getBatch(): BatchAPIGroupClient {
        return get(BatchAPIGroupClient::class.java)
    }

    fun getStorage(): StorageAPIGroupClient {
        return get(StorageAPIGroupClient::class.java)
    }

    fun getExtensions(): ExtensionsAPIGroupClient {
        return get(ExtensionsAPIGroupClient::class.java)
    }

    fun <T: Client> get(type: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (type.isAssignableFrom(client::class.java)) {
            client as T
        } else {
            clients.computeIfAbsent(type) { client.adapt(type) } as T
        }
    }

    fun close() {
        clients.values.forEach{ it.close() }
        client.close()
    }
}
