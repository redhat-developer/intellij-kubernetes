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

import com.redhat.devtools.intellij.kubernetes.model.client.ssl.IDEATrustManager
import com.redhat.devtools.intellij.kubernetes.model.util.isUnauthorized
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.http.HttpClient
import io.fabric8.kubernetes.client.impl.AppsAPIGroupClient
import io.fabric8.kubernetes.client.impl.BatchAPIGroupClient
import io.fabric8.kubernetes.client.impl.NetworkAPIGroupClient
import io.fabric8.kubernetes.client.impl.StorageAPIGroupClient
import io.fabric8.kubernetes.client.internal.SSLUtils
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

open class OSClientAdapter(client: OpenShiftClient, private val kubeClient: KubernetesClient) :
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

open class KubeClientAdapter(client: KubernetesClient) :
    ClientAdapter<KubernetesClient>(client) {
    override fun isOpenShift(): Boolean {
        return false
    }
}

abstract class ClientAdapter<C : KubernetesClient>(private val fabric8Client: C) {

    companion object Factory {
        fun create(
            namespace: String? = null,
            context: String? = null,
            trustManagerProvider: ((toIntegrate: Array<out X509ExtendedTrustManager>) -> X509TrustManager)
            = IDEATrustManager()::configure
        ): ClientAdapter<out KubernetesClient> {
            val config = Config.autoConfigure(context)
            return create(namespace, config, trustManagerProvider)
        }

        fun create(
            namespace: String? = null,
            config: Config,
            externalTrustManagerProvider: (toIntegrate: Array<out X509ExtendedTrustManager>) -> X509TrustManager
            = IDEATrustManager()::configure
        ): ClientAdapter<out KubernetesClient> {
            setNamespace(namespace, config)
            val kubeClient = KubernetesClientBuilder()
                .withConfig(config)
                .withHttpClientBuilderConsumer { builder ->
                    setSslContext(builder, config, externalTrustManagerProvider)
                }
                .build()
            val osClient = kubeClient.adapt(NamespacedOpenShiftClient::class.java)
            val isOpenShift = isOpenShift(osClient)
            return if (isOpenShift) {
                OSClientAdapter(osClient, kubeClient)
            } else {
                KubeClientAdapter(kubeClient)
            }
        }

        private fun setSslContext(
            builder: HttpClient.Builder,
            config: Config,
            externalTrustManagerProvider: (toIntegrate: Array<out X509ExtendedTrustManager>) -> X509TrustManager
        ) {
            val clientTrustManagers = SSLUtils.trustManagers(config)
                .filterIsInstance<X509ExtendedTrustManager>()
                .toTypedArray()
            val externalTrustManager = externalTrustManagerProvider.invoke(clientTrustManagers)
            builder.sslContext(SSLUtils.keyManagers(config), arrayOf(externalTrustManager))
        }

        private fun isOpenShift(osClient: NamespacedOpenShiftClient): Boolean {
            return try {
                osClient.isSupported
            } catch (e: KubernetesClientException) {
                e.isUnauthorized()
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
