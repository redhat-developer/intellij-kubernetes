/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.dashboard

import com.redhat.devtools.intellij.kubernetes.model.util.PluginException
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.http.HttpStatusMessage


/**
 * An abstract factory that can determine the url of the dashboard for a cluster.
 */
abstract class AbstractDashboard<C : KubernetesClient>(
    protected val client: C,
    private val contextName: String,
    private val clusterUrl: String?,
    /** for testing purposes */
    protected val httpRequest: HttpRequest
): IDashboard {

    private var url: String? = null

    override fun get(): String {
        val url = this.url ?: connect()
        this.url = url
        return url
    }

    private fun connect(): String {
        val status = try {
            doConnect()
        } catch (e: Exception) {
            throw PluginException("Could not find Dashboard for cluster $contextName at $clusterUrl: ${e.message}")
        } ?: throw PluginException("Could not find Dashboard for cluster $contextName at $clusterUrl")

        if (status.isSuccessful
            || status.isForbidden
        ) {
            return status.url
        } else {
            throw PluginException(
                "Could not reach dashboard for cluster $contextName ${
                    if (clusterUrl.isNullOrEmpty()) {
                        ""
                    } else {
                        "at $clusterUrl"
                    }
                }" + "${
                    if (status.status == null) {
                        ""
                    } else {
                        ". Responded with ${
                            HttpStatusMessage.getMessageForStatus(status.status)
                        }"
                    }
                }."
            )
        }
    }

    protected abstract fun doConnect(): HttpRequest.HttpStatusCode?

    override fun close() {
        // noop default impl
    }
}

interface IDashboard {
    fun get(): String
    fun close()
}