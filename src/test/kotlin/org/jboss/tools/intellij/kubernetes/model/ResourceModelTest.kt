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
package org.jboss.tools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.fabric8.kubernetes.api.model.DoneableNamespace
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.jboss.tools.intellij.kubernetes.model.cluster.IActiveCluster
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.clusterFactory
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resource
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.cluster
import org.junit.Test

typealias NamespaceListOperation = NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>

class ResourceModelTest {

    private val client: NamespacedKubernetesClient = mock()
    private val modelChange: IModelChangeObservable = mock()
    private val namespace: Namespace = resource("papa smurf")
    private val cluster: IActiveCluster<HasMetadata, KubernetesClient> = cluster(client, namespace)
    private val clusterFactory: (IModelChangeObservable) -> IActiveCluster<HasMetadata, KubernetesClient> = clusterFactory(cluster)
    private val model: IResourceModel = ResourceModel(modelChange, clusterFactory)

    @Test
    fun `#getResources(kind) should call cluster#getResources(kind)`() {
        // given
        // when
        model.getResources(Pod::class.java)
        // then
        verify(cluster).getResources(Pod::class.java)
    }

    @Test
    fun `#setCurrentNamespace(name) should call cluster#setCurrentNamespace(name)`() {
        // given
        // when
        model.setCurrentNamespace("papa-smurf")
        // then
        verify(cluster).setCurrentNamespace("papa-smurf")
    }

    @Test
    fun `#getCurrentNamespace() should call cluster#getCurrentNamespace()`() {
        // given
        // when
        model.getCurrentNamespace()
        // then
        verify(cluster).getCurrentNamespace()
    }

    @Test
    fun `#invalidate(model) should create new cluster`() {
        // given
        createCluster() // access cluster field, cause creation of cluster
        clearInvocations(clusterFactory)
        // when
        model.invalidate(model)
        // then
        verify(clusterFactory).invoke(any())
    }

    @Test
    fun `#invalidate(model) should close existing cluster`() {
        // given
        // when
        model.invalidate(model)
        // then
        verify(cluster).close()
    }

    @Test
    fun `#invalidate(model) should watch new cluster`() {
        // given
        createCluster() // access cluster field, cause creation of cluster
        clearInvocations(cluster)
        // when
        model.invalidate(model)
        // then
        verify(cluster).startWatch()
    }

    @Test
    fun `#invalidate(model) should notify client change`() {
        // given
        // when
        model.invalidate(model)
        // then
        verify(modelChange).fireModified(model)
    }

    @Test
    fun `#invalidate(class) should call ICluster#invalidate(class)`() {
        // given
        createCluster()
        clearInvocations(cluster)
        // when
        model.invalidate(Pod::class.java)
        // then
        verify(cluster).invalidate(Pod::class.java)
    }

    @Test
    fun `#invalidate(class) should fire class change`() {
        // given
        // when
        model.invalidate(Pod::class.java)
        // then
        verify(modelChange).fireModified(Pod::class.java)
    }

    @Test
    fun `#invalidate(resource) should call ICluster#invalidate(resource)`() {
        // given
        createCluster()
        clearInvocations(cluster)
        val resource = mock<HasMetadata>()
        // when
        model.invalidate(resource)
        // then
        verify(cluster).invalidate(resource)
    }

    @Test
    fun `#invalidate(resource) should fire resource change`() {
        // given
        // when
        val resource = mock<HasMetadata>()
        model.invalidate(resource)
        // then
        verify(modelChange).fireModified(resource)
    }

    private fun createCluster() {
        // access cluster field, cause creation of cluster
        model.getClient()
    }
}
