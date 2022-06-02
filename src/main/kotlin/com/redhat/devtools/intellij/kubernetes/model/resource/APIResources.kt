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
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.api.Pluralize
import io.fabric8.kubernetes.client.BaseClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.OperationSupport.createStatus
import java.net.HttpURLConnection
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Class that allows API discovery by querying cluster api resources.
 * Can be re-implemented via DefaultKubernetesClient.getApiResources(String) as per kubernetes-client 5.11.0
 */
class APIResources(private val client: KubernetesClient) {

    companion object {
        const val URL_APIS = "apis"
        const val URL_API = "api"
        private const val KEY_HEADER_AUTHORIZATION = "Authorization"
        private const val VALUE_HEADER_BEARER = "Bearer"
    }

    /**
     * Returns the [APIResource] for the given kind, group and version.
     * Returns `null` if it doesn't exist.
     * The cluster is queried for the existing api resources.
     *
     * @param kind the kind of the resource
     * @param group the api group of the resource
     * @param version the version of the resource
     * @return [APIResource] for the given kind, group and version
     * @throws KubernetesClientException
     */
    fun get(kind: String, group: String?, version: String): APIResource? {
        val resources =
            if (group == null) {
                // core api resources
                requestResources("$URL_API/$version", client)
            } else {
                // additional api resources
                requestResources("$URL_APIS/$group/$version", client)
            }
            ?: return null
        return getByKind(kind, resources)
    }

    private fun requestResources(url: String, client: KubernetesClient): List<APIResource>? {
        // can be replaced by OperationSupport.restCall(Class, String) in kubernetes-client >= 5.8.0
        request(url, client).use { response ->
            // auto-close response body
            val json = response?.bytes() ?: return null
            return ObjectMapper().readValue(json, APIResourceList::class.java)?.resources
        }
    }

    private fun getByKind(kind: String, resources: List<APIResource>): APIResource? {
        val plural = Pluralize.toPlural(kind).toLowerCase()
        return resources.firstOrNull {
            plural == it.name
        }
    }

    private fun request(url: String, client: KubernetesClient): ResponseBody? {
        // only base client exposes httpClient
        val baseClient = client as? BaseClient? ?: return null
        val httpClient = client.httpClient
        val config = baseClient.configuration
        val request = Request.Builder()
            .url("${baseClient.masterUrl}$url")
            .addHeader(KEY_HEADER_AUTHORIZATION, "$VALUE_HEADER_BEARER ${config.oauthToken}")
            .build()

        // IDEA complains about elvis operator always returning left portion. This is wrong, #newCall can return null
        @Suppress("USELESS_ELVIS") // compiler warning is wrong, execute may return null
        val response = httpClient.newCall(request).execute() ?: return null
        // doesn't compile in IDEA but does in gradle: IJ is using okhttp 3, gradle okhttp 4
        return when (response.code) {
            HttpURLConnection.HTTP_OK ->
                // doesn't compile in IDEA but does in gradle: IJ is using okhttp 3, gradle okhttp 4
                return response.body
            HttpURLConnection.HTTP_NOT_FOUND ->
                null
            else ->
                throw createException(url, response)
        }
    }

    private fun createException(url: String, response: Response): KubernetesClientException {
        val status = createStatus(response)
        val message = "Could not retrieve api resources at $url${
            if (true == status.message?.isNotBlank()) {
                ": ${status.message}"
            } else {
                ""
            }
        }"
        return KubernetesClientException(message, status.code, status)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class APIResourceList(
        var apiVersion: String? = null,
        var groupVersion: String? = null,
        var resources: List<APIResource> = mutableListOf()
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class APIResource(
        var name: String? = null,
        var singularName: String? = null,
        var namespaced: Boolean = false,
        var kind: String? = null
    )
}
