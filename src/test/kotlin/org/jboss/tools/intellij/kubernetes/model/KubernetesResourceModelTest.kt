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
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.DoneableNamespace
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.junit.Before
import org.junit.Test

typealias NamespaceListOperation = NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>

class KubernetesResourceModelTest {

    private lateinit var client: NamespacedKubernetesClient
    private lateinit var resourceChange: IResourceChangeObservable
    private lateinit var clusterMock: ICluster
    private lateinit var clusterFactory: (IResourceChangeObservable) -> Cluster
    private lateinit var model: IKubernetesResourceModel

    @Before
    fun before() {
        client = mock()
        resourceChange = mock()

        clusterMock = mockCluster(client)
        clusterFactory = mockClusterFactory(clusterMock)
        model = KubernetesResourceModel(resourceChange, clusterFactory)
    }

    private fun mockClusterFactory(cluster: ICluster): (IResourceChangeObservable) -> Cluster {
        val clusterFactory: (IResourceChangeObservable) -> Cluster = mock()
        doReturn(cluster)
            .whenever(clusterFactory).invoke(any())
        return clusterFactory
    }

    private fun mockCluster(client: NamespacedKubernetesClient): ICluster {
        val clusterMock: ICluster = mock()
        doNothing()
            .whenever(clusterMock).watch()
        doReturn(client)
            .whenever(clusterMock).client
        return clusterMock
    }

    @Test
    fun `clear should create new cluster`() {
        // given
        // reset cluster creation upon model instantiation
        reset(clusterFactory)
        // when
        model.clear()
        // then
        verify(clusterFactory, times(1)).invoke(any())
    }

    @Test
    fun `clear should notify client change`() {
        // given
        // when
        model.clear()
        // then
        verify(resourceChange, times(1)).fireModified(client)
    }

    open class TestableCluster(resourceChange: ResourceChangeObservable): Cluster(resourceChange) {

        public override fun createClient(): NamespacedKubernetesClient {
            return super.createClient()
        }

    }
}
