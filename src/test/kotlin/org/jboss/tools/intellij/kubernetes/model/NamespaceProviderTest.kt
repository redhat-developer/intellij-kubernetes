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

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.openshift.api.model.DeploymentConfig
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resourceKindProvider
import org.junit.Test

class NamespaceProviderTest {

    private val client = mock<NamespacedKubernetesClient>()
    private val podKindProvider = resourceKindProvider(Pod::class.java)
    private val rcKindProvider = resourceKindProvider(ReplicationController::class.java)
    private val namespaceProvider = NamespaceProvider(
        client,
        NAMESPACE1,
        mapOf<Class<out HasMetadata>, IResourceKindProvider<out HasMetadata>>(
            Pair(Pod::class.java, podKindProvider),
            Pair(ReplicationController::class.java, rcKindProvider)
        )
    )

    @Test
    fun `getResources(kind) calls kindProvider#allResources`() {
        // given
        // when
        namespaceProvider.getResources(rcKindProvider.kind)
        // then
        verify(rcKindProvider).getAllResources()
    }

    @Test
    fun `add(resource) with unknown resource kind wont add`() {
        // given
        val dc = mock<DeploymentConfig>()
        // when
        val added = namespaceProvider.add(dc)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `add(resource) adds rc to rc kind provider`() {
        // given
        val rc = mock<ReplicationController>()
        // when
        namespaceProvider.add(rc)
        // then
        verify(rcKindProvider).add(rc)
    }

    @Test
    fun `remove(resource) with unknown resource kind wont remove`() {
        // given
        val dc = mock<DeploymentConfig>()
        // when
        val removed = namespaceProvider.remove(dc)
        // then
        assertThat(removed).isFalse()
    }

    @Test
    fun `remove(resource) removes rc from rc kind provider`() {
        // given
        val rc = mock<ReplicationController>()
        // when
        val removed = namespaceProvider.remove(rc)
        // then
        verify(rcKindProvider).remove(rc)
    }

    @Test
    fun `clear() clears all kind providers`() {
        // given
        // when
        namespaceProvider.clear()
        // then
        verify(rcKindProvider).clear()
        verify(podKindProvider).clear()
    }

    @Test
    fun `clear(resource) clears the kind provider for resource`() {
        // given
        val rc = mock<ReplicationController>()
        // when
        namespaceProvider.clear(rc::class.java)
        // then
        verify(rcKindProvider).clear()
        verify(podKindProvider, never()).clear()
    }

    @Test
    fun `clear(resource) of unknown resource kind wont clear`() {
        // given
        val dc = mock<DeploymentConfig>()
        // when
        namespaceProvider.clear(dc::class.java)
        // then
        verify(rcKindProvider, never()).clear()
        verify(podKindProvider, never()).clear()
    }

}