/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Based on nl.altindag.ssl.trustmanager.CompositeX509ExtendedTrustManager at https://github.com/Hakky54/sslcontext-kickstart
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.intellij.kubernetes.model.client.ssl

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import java.util.function.Consumer
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

class CompositeX509ExtendedTrustManager(trustManagers: List<X509ExtendedTrustManager>): X509ExtendedTrustManager() {

    companion object {
        private const val CERTIFICATE_EXCEPTION_MESSAGE = "None of the TrustManagers trust this certificate chain"
    }

    val innerTrustManagers: List<X509ExtendedTrustManager>
    private val acceptedIssuers: Array<X509Certificate>

    init {
        innerTrustManagers = Collections.unmodifiableList(trustManagers)
        acceptedIssuers = trustManagers
            .map { manager: X509ExtendedTrustManager -> manager.acceptedIssuers }
            .flatMap { acceptedIssuers: Array<X509Certificate>? ->
                acceptedIssuers?.asList() ?: emptyList()
            }
            .toTypedArray()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return Arrays.copyOf(acceptedIssuers, acceptedIssuers.size)
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        checkTrusted { trustManager: X509ExtendedTrustManager ->
            trustManager.checkClientTrusted(
                chain,
                authType
            )
        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
        checkTrusted { trustManager: X509ExtendedTrustManager ->
            trustManager.checkClientTrusted(
                chain,
                authType,
                socket
            )
        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, sslEngine: SSLEngine) {
        checkTrusted { trustManager: X509ExtendedTrustManager ->
            trustManager.checkClientTrusted(
                chain,
                authType,
                sslEngine
            )
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        checkTrusted{ trustManager: X509ExtendedTrustManager ->
            trustManager.checkServerTrusted(
                chain,
                authType
            )
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
        checkTrusted{ trustManager: X509ExtendedTrustManager ->
            trustManager.checkServerTrusted(
                chain,
                authType,
                socket
            )
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, sslEngine: SSLEngine) {
        checkTrusted { trustManager: X509ExtendedTrustManager ->
            trustManager.checkServerTrusted(
                chain,
                authType,
                sslEngine
            )
        }
    }

    @Throws(CertificateException::class)
    private fun checkTrusted(consumer: (trustManager: X509ExtendedTrustManager) -> Unit) {
        val certificateExceptions: MutableList<CertificateException> = ArrayList()
        for (trustManager in innerTrustManagers) {
            try {
                consumer.invoke(trustManager)
                return
            } catch (e: CertificateException) {
                certificateExceptions.add(e)
            }
        }
        val certificateException = CertificateException(CERTIFICATE_EXCEPTION_MESSAGE)
        certificateExceptions.forEach(Consumer { exception: CertificateException? ->
            certificateException.addSuppressed(
                exception
            )
        })
        throw certificateException
    }
}
