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
package com.redhat.devtools.intellij.kubernetes.model.client.ssl

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.ConfirmingTrustManager
import java.lang.reflect.Field
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import org.apache.commons.lang3.reflect.FieldUtils

class IDEATrustManager(private val trustManager: X509TrustManager = CertificateManager.getInstance().trustManager) {

    fun configure(toAdd: Array<out X509ExtendedTrustManager>): X509TrustManager {
        try {
            if (hasSystemManagerField()) {
                // < IC-2022.2
                setCompositeManager(toAdd, trustManager)
            } else {
                // >= IC-2022.2
                addCompositeManager(toAdd, trustManager)
            }
        } catch (e: RuntimeException) {
            logger<IDEATrustManager>().warn("Could not configure IDEA trust manager.", e)
        }
        return trustManager
    }

    /**
     * Returns `true` if [ConfirmingTrustManager] has a private field `mySystemManager`.
     * Returns `false` otherwise.
     * IDEA < IC-2022.2 manages a single [X509TrustManager] in a private field called `mySystemManager`.
     * IDEA >= IC-2022.2 manages a list of [X509TrustManager]s in a private list called `mySystemManagers`.
     *
     * @return true if com.intellij.util.net.ssl.ConfirmingTrustManager has a field mySystemManager. False otherwise.
     */
    private fun hasSystemManagerField(): Boolean {
        return getSystemManagerField() != null
    }

    private fun getSystemManagerField(): Field? {
        return FieldUtils.getDeclaredField(
            trustManager::class.java,
            "mySystemManager",
            true
        )
    }

    /**
     * Sets a [CompositeX509ExtendedTrustManager] with the given [X509TrustManager]s
     * to the given destination [X509TrustManager].
     * If a [CompositeX509ExtendedTrustManager] already exists, his first entry is taken and set to a new
     * [CompositeX509ExtendedTrustManager] that replaces the existing one.
     *
     * @param trustManagers the trust managers that should be set to the destination trust manager
     * @param destination the destination trust manager that should receive the trust managers
     * @return true if the operation worked
     */
    private fun setCompositeManager(
        trustManagers: Array<out X509ExtendedTrustManager>,
        destination: X509TrustManager
    ): Boolean {
        val systemManagerField = getSystemManagerField() ?: return false
        val systemManager = systemManagerField.get(destination) as? X509ExtendedTrustManager ?: return false
        val compositeTrustManager = createCompositeTrustManager(systemManager, trustManagers)
        systemManagerField.set(destination, compositeTrustManager)
        return true
    }

    private fun createCompositeTrustManager(
        systemManager: X509ExtendedTrustManager,
        clientTrustManagers: Array<out X509ExtendedTrustManager>
    ): X509ExtendedTrustManager {
        val trustManagers = if (systemManager is CompositeX509ExtendedTrustManager) {
            // already patched CertificateManager, take 1st entry in existing system manager
            mutableListOf(systemManager.innerTrustManagers[0])
        } else {
            // unpatched CertificateManager, take system manager
            mutableListOf(systemManager)
        }
        trustManagers.addAll(clientTrustManagers)
        return CompositeX509ExtendedTrustManager(trustManagers)
    }

    /**
     * Adds a [CompositeX509ExtendedTrustManager] to the given destination [X509TrustManager].
     * If a [CompositeX509ExtendedTrustManager] already exists, it is replaced by a new [CompositeX509ExtendedTrustManager].
     *
     * @param trustManagers the trust managers that should be added to destination trust manager
     * @param destination the trust manager that should receive the given trust managers
     */
    private fun addCompositeManager(
        trustManagers: Array<out X509ExtendedTrustManager>,
        destination: X509TrustManager
    ): Boolean {
        val systemManagersField = FieldUtils.getDeclaredField(
            destination::class.java,
            "mySystemManagers",
            true
        ) ?: return false
        val managers = systemManagersField.get(destination) as? MutableList<X509TrustManager> ?: return false
        val nonCompositeManagers = managers.filterNot { it is CompositeX509ExtendedTrustManager }
        val clientTrustManager = CompositeX509ExtendedTrustManager(trustManagers.asList())
        managers.clear()
        managers.addAll(nonCompositeManagers)
        managers.add(clientTrustManager)
        return true
    }
}