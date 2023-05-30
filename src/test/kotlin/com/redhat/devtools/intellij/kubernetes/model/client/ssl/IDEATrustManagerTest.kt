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

import com.nhaarman.mockitokotlin2.mock
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class IDEATrustManagerTest {

    @Test
    fun `single system manager field - should replace existing trust manager with new composite trust manager`() {
        // given
        val trustManager = TrustManagerWithMySystemManagerField(mock<X509ExtendedTrustManager>())
        val operator = IDEATrustManager(trustManager)
        assertThat(trustManager.mySystemManager)
            .isNotInstanceOf(CompositeX509ExtendedTrustManager::class.java)
        // when
        operator.configure(emptyArray())
        // then
        assertThat(trustManager.mySystemManager)
            .isInstanceOf(CompositeX509ExtendedTrustManager::class.java)
    }

    @Test
    fun `single system manager field - should replace existing trust manager with new composite trust manager that contains given trust managers`() {
        // given
        val trustManager = TrustManagerWithMySystemManagerField(mock<X509ExtendedTrustManager>())
        val operator = IDEATrustManager(trustManager)
        val newTrustManagers = arrayOf(
            mock<X509ExtendedTrustManager>(),
            mock<X509ExtendedTrustManager>()
        )
        // when
        operator.configure(newTrustManagers)
        // then
        assertThat(trustManager.mySystemManager)
            .isInstanceOf(CompositeX509ExtendedTrustManager::class.java)
        val afterConfigure = (trustManager.mySystemManager as CompositeX509ExtendedTrustManager).innerTrustManagers
        assertThat(afterConfigure)
            .contains(*newTrustManagers) // new instance contains list given to configure()
    }

    @Test
    fun `single system manager field - should replace existing trust manager with new composite trust manager that has replaced trust manager as 1st entry`() {
        // given
        val beforeReplace = mock<X509ExtendedTrustManager>()
        val trustManager = TrustManagerWithMySystemManagerField(beforeReplace)
        val operator = IDEATrustManager(trustManager)
        // when
        operator.configure(
            arrayOf(
                mock<X509ExtendedTrustManager>(),
                mock<X509ExtendedTrustManager>()
            )
        )
        // then
        assertThat(trustManager.mySystemManager)
            .isInstanceOf(CompositeX509ExtendedTrustManager::class.java)
        val afterConfigure = (trustManager.mySystemManager as CompositeX509ExtendedTrustManager).innerTrustManagers
        assertThat(afterConfigure[0]) // new instance contains 1st entry of replaced instance
            .isEqualTo(beforeReplace)
    }

    @Test
    fun `single system manager field - should replace composite trust manager with new instance that has 1st entry of replaced composite manager`() {
        // given
        val toInclude = mock<X509ExtendedTrustManager>()
        val toExclude = mock<X509ExtendedTrustManager>()
        val compositeTrustManager = CompositeX509ExtendedTrustManager(listOf(toInclude, toExclude))
        val trustManager = TrustManagerWithMySystemManagerField(compositeTrustManager)
        val manager = IDEATrustManager(trustManager)
        // when
        manager.configure(
            arrayOf(
                mock<X509ExtendedTrustManager>(),
                mock<X509ExtendedTrustManager>()
            )
        )
        // then
        assertThat(trustManager.mySystemManager)
            .isNotSameAs(compositeTrustManager) // a new instance was created
        assertThat(trustManager.mySystemManager)
            .isInstanceOf(CompositeX509ExtendedTrustManager::class.java)
        val afterConfigure = (trustManager.mySystemManager as CompositeX509ExtendedTrustManager).innerTrustManagers
        assertThat(afterConfigure[0]) // new instance contains 1st entry of replaced instance
            .isEqualTo(toInclude)
    }

    @Test
    fun `multi system managers field - should still contain existing trust managers`() {
        // given
        val existing = mock<X509ExtendedTrustManager>()
        val managers = mutableListOf<X509TrustManager>(existing)
        val trustManager = TrustManagerWithMySystemManagersField(managers)
        val operator = IDEATrustManager(trustManager)
        // when
        operator.configure(emptyArray())
        // then
        assertThat(trustManager.mySystemManagers)
            .contains(existing)
    }

    @Test
    fun `multi system managers field - should add composite manager that contains new trust managers`() {
        // given
        val managers = mutableListOf<X509TrustManager>(mock<X509ExtendedTrustManager>())
        val trustManager = TrustManagerWithMySystemManagersField(managers)
        val operator = IDEATrustManager(trustManager)
        val new = arrayOf(
            mock<X509ExtendedTrustManager>(),
            mock<X509ExtendedTrustManager>()
        )
        // when
        operator.configure(new)
        // then
        val composite = trustManager.mySystemManagers.find {
            it is CompositeX509ExtendedTrustManager
        } as CompositeX509ExtendedTrustManager
        assertThat(composite.innerTrustManagers).containsExactly(*new)
    }

    @Test
    fun `multi system managers field - should replace existing composite manager that contains new trust managers`() {
        // given
        val existingTrustManager = mock<X509ExtendedTrustManager>()
        val existingCompositeManager = CompositeX509ExtendedTrustManager(listOf(mock<X509ExtendedTrustManager>()))
        val managers = mutableListOf<X509TrustManager>(existingTrustManager, existingCompositeManager)
        val trustManager = TrustManagerWithMySystemManagersField(managers)
        val operator = IDEATrustManager(trustManager)
        val new = arrayOf(
            mock<X509ExtendedTrustManager>(),
            mock<X509ExtendedTrustManager>()
        )
        // when
        operator.configure(new)
        // then
        assertThat(trustManager.mySystemManagers).doesNotContain(existingCompositeManager)
        val composite = trustManager.mySystemManagers.find {
            it is CompositeX509ExtendedTrustManager
        } as CompositeX509ExtendedTrustManager
        assertThat(composite.innerTrustManagers).containsExactly(*new)
    }

    /** [com.intellij.util.net.ssl.ConfirmingTrustManager] in < IC-2022.2 */
    private class TrustManagerWithMySystemManagerField(var mySystemManager: X509TrustManager): X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }

    }

    /** [com.intellij.util.net.ssl.ConfirmingTrustManager] in >= IC-2022.2 */
    private class TrustManagerWithMySystemManagersField(var mySystemManagers: MutableList<X509TrustManager> = mutableListOf()): X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }

    }
}