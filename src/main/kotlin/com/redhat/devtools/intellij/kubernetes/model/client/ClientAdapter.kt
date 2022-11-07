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
package com.redhat.devtools.intellij.kubernetes.model.client

import io.fabric8.kubernetes.client.*
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import io.fabric8.openshift.client.OpenShiftNotAvailableException
import java.util.concurrent.ConcurrentHashMap

open class OSClientAdapter(client: NamespacedOpenShiftClient, private val kubeClient: NamespacedKubernetesClient) :
    ClientAdapter<OpenShiftClient>(client) {

    override val config by lazy {
        // openshift client configuration does not have kube config entries
        ClientConfig(kubeClient)
    }

    override fun isOpenShift(): Boolean {
        return true
    }

    override fun close() {
        super.close()
        kubeClient.close()
    }
}

open class KubeClientAdapter(client: NamespacedKubernetesClient) :
    ClientAdapter<NamespacedKubernetesClient>(client) {
    override fun isOpenShift(): Boolean {
        return false
    }
}

abstract class ClientAdapter<C: KubernetesClient>(private val fabric8Client: C) {

    companion object Factory {
        fun create(namespace: String? = null, context: String? = null): ClientAdapter<out KubernetesClient> {
            return create(namespace, Config.autoConfigure(context))
        }

        fun create(namespace: String? = null, config: Config): ClientAdapter<out KubernetesClient> {
            setNamespace(namespace, config)
            val kubeClient = DefaultKubernetesClient(config)
            return try {
                val osClient = kubeClient.adapt(NamespacedOpenShiftClient::class.java)
                OSClientAdapter(osClient, kubeClient)
            } catch (e: RuntimeException) {
                when (e) {
                    is KubernetesClientException, // unauthorized openshift
                    is OpenShiftNotAvailableException ->
                        KubeClientAdapter(kubeClient)
                    else -> throw e
                }
            }
        }

        private fun setNamespace(namespace: String?, config: Config) {
            if (namespace != null) {
                config.namespace = namespace
                config.currentContext?.context?.namespace = namespace
            }
        }
    }

    val namespace: String?
        get() {
            return get().namespace
        }

    private val clients = ConcurrentHashMap<Class<out Client>, Client>()

    open val config by lazy {
        ClientConfig(fabric8Client)
    }

    abstract fun isOpenShift(): Boolean

    fun get(): C {
        return fabric8Client
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

    fun getNetworking(): NetworkAPIGroupClient {
        return get(NetworkAPIGroupClient::class.java)
    }

    fun <T: Client> get(type: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (type.isAssignableFrom(fabric8Client::class.java)) {
            fabric8Client as T
        } else {
            clients.computeIfAbsent(type) { fabric8Client.adapt(type) } as T
        }
    }

    open fun close() {
        clients.values.forEach{ it.close() }
        fabric8Client.close()
    }
}
