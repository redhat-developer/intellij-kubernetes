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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.DoneableNamespace
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.namespaceProvider
import org.junit.Test

typealias NamespaceListOperation = NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>

class KubernetesResourceModelTest {

    private var client: NamespacedKubernetesClient = mock()
    private var modelChange: IModelChangeObservable = mock()
    private var provider: NamespaceProvider = namespaceProvider()
    private var cluster: ICluster = Mocks.cluster(client, provider)
    private var clusterFactory: (IModelChangeObservable) -> ICluster = Mocks.clusterFactory(cluster)
    private var model: IKubernetesResourceModel = KubernetesResourceModel(modelChange, clusterFactory)

    @Test
    fun `getAllNamespaces should return all namespaces in cluster`() {
        // given
        val namespaces = listOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
        doReturn(namespaces)
            .whenever(cluster).getAllNamespaces()
        // when
        model.getAllNamespaces()
        // then
        verify(cluster, times(1)).getAllNamespaces()
    }

    @Test
    fun `getNamespace(name) should return namespace from cluster`() {
        // given
        val name = NAMESPACE2.metadata.name
        doReturn(NAMESPACE2)
            .whenever(cluster).getNamespace(name)
        // when
        model.getNamespace(name)
        // then
        verify(cluster, times(1)).getNamespace(name)
    }

    @Test
    fun `getResources(name) should return all resources of a kind in the given namespace`() {
        // given
        // when
        val kind = HasMetadata::class.java
        model.getResources("anyNamespace", kind)
        // then
        verify(provider, times(1)).getResources(kind)
    }

    @Test
    fun `#invalidate() should create new cluster`() {
        // given
        // reset cluster factory invocation done when model is instantiated
        clearInvocations(clusterFactory)
        // when
        model.invalidate()
        // then
        verify(clusterFactory, times(1)).invoke(any())
    }

    @Test
    fun `#invalidate() should close existing cluster`() {
        // given
        // when
        model.invalidate()
        // then
        verify(cluster, times(1)).close()
    }

    @Test
    fun `#invalidate() should watch new cluster`() {
        // given
        // reset watch invocation done when model is instantiated
        clearInvocations(cluster)
        // when
        model.invalidate()
        // then
        verify(cluster, times(1)).startWatch()
    }

    @Test
    fun `#invalidate() should notify client change`() {
        // given
        // when
        model.invalidate()
        // then
        verify(modelChange, times(1)).fireModified(client)
    }

    @Test
    fun `#invalidate(resource) should call NamespaceProvider#invalidate()`() {
        // given
        // when
        model.invalidate(mock<HasMetadata>())
        // then
        verify(provider, times(1)).invalidate()
    }

    @Test
    fun `#invalidate(resource) for inexistent resource should not call NamespaceProvider#invalidate()`() {
        // given no namespace provider returned
        doReturn(null)
            .whenever(cluster).getNamespaceProvider(any<HasMetadata>())
        // when
        model.invalidate(mock<HasMetadata>())
        // then
        verify(provider, never()).invalidate()
    }

    @Test
    fun `#invalidate() resource should fire namespace provider change`() {
        // given
        // when
        val resource = mock<HasMetadata>()
        model.invalidate(resource)
        // then
        verify(modelChange, times(1)).fireModified(resource)
    }
}
