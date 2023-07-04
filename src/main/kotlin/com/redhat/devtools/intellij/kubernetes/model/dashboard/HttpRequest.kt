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

import io.fabric8.kubernetes.client.http.HttpResponse
import java.net.HttpURLConnection
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class HttpRequest {

    companion object {
        private val trustAllTrustManager = object : X509TrustManager {

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
                // ignore aka trust
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }

            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
                // ignore aka trust
            }
        }
    }

    fun request(url: String): HttpStatusCode {
        return requestHttpStatusCode(url)
    }

    fun request(host: String, port: Int): HttpStatusCode {
        var status = requestHttpStatusCode("http://$host:$port")
        if (status.isSuccessful) {
            return status
        } else {
            status = requestHttpStatusCode("https://$host:$port")
            if (status.isSuccessful) {
                return status
            }
        }
        return status
    }

    /**
     * Requests the https status code for the given url.
     * Return [HttpStatusCode] and throws if connecting fails.
     *
     * @param url the url to request the http status code for
     *
     * The implementation is ignores (private) SSL certificates and doesn't verify the hostname.
     * All that matters is whether we can connect successfully or not.
     * OkHttp is used because it allows to set a [javax.net.ssl.HostnameVerifier] on a per connection base.
     */
    private fun requestHttpStatusCode(url: String): HttpStatusCode {
        val sslContext = createSSLContext()
        var response: Response? = null
        try {
            response = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllTrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
                .newCall(
                    Request.Builder()
                        .url(url)
                        .build()
                )
                .execute()
            return HttpStatusCode(url, response.code)
        } catch (e: SSLHandshakeException) {
            if (e.cause is CertificateParsingException) {
                /**
                 * Fake 200 OK response in case ssl handshake certificate could not be parsed.
                 * This happens with azure dashboard where a certificate is used that the jdk cannot handle:
                 * ```
                 * javax.net.ssl.SSLHandshakeException: Failed to parse server certificates
                 * java.security.cert.CertificateParsingException: Empty issuer DN not allowed in X509Certificates
                 * ```
                 * @see [Stackoverflow question 65692099](https://stackoverflow.com/questions/65692099/java-empty-issuer-dn-not-allowed-in-x509certificate-libimobiledevice-implementa)
                 * @see [kubernetes cert-manager issue #3634](https://github.com/cert-manager/cert-manager/issues/3634)
                 */
                return HttpStatusCode(url, HttpURLConnection.HTTP_OK)
            } else {
                throw e
            }
        } finally {
            response?.close()
        }
    }

    private fun createSSLContext(): SSLContext {
        val sslContext: SSLContext = SSLContext.getDefault()
        sslContext.init(null, arrayOf<TrustManager>(trustAllTrustManager), SecureRandom())
        return sslContext
    }

    class HttpStatusCode(val url: String, val status: Int?) {
        val isSuccessful: Boolean
            get() {
                return status != null
                        && HttpResponse.isSuccessful(status)
            }
        val isForbidden: Boolean
            get() {
                return status != null
                        && HttpURLConnection.HTTP_FORBIDDEN == status
            }
    }
}
