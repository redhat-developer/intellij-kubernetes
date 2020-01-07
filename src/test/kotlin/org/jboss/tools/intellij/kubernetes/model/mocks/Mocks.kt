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
package org.jboss.tools.intellij.kubernetes.model.mocks

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.ICluster
import org.jboss.tools.intellij.kubernetes.model.IResourceChangeObservable
import org.jboss.tools.intellij.kubernetes.model.IResourceKindProvider
import org.jboss.tools.intellij.kubernetes.model.NamespaceListOperation
import org.jboss.tools.intellij.kubernetes.model.NamespaceProvider

object Mocks {

    val NAMESPACE1 = namespace("namespace1")
    val NAMESPACE2 = namespace("namespace2")
    val NAMESPACE3 = namespace("namespace3")

    fun client(namespaces: List<Namespace>): NamespacedKubernetesClient {
        val namespaceList = mock<NamespaceList> {
            on { items } doReturn namespaces
        }
        val namespacesMock =
            mock<NamespaceListOperation> {
                on { list() } doReturn namespaceList
            }
        return mock<NamespacedKubernetesClient> {
            on { namespaces() } doReturn namespacesMock
        }
    }

    fun namespace(name: String): Namespace {
        val metadata = mock<ObjectMeta> {
            on { getName() } doReturn name
        }
        return mock {
            on { getMetadata() } doReturn metadata
        }
    }

    fun clusterFactory(cluster: ICluster): (IResourceChangeObservable) -> ICluster {
        return mock() {
            on { invoke(any()) } doReturn cluster
        }
    }

    fun cluster(client: NamespacedKubernetesClient, provider: NamespaceProvider): ICluster {
        return mock() {
            doNothing()
                .whenever(mock).watch()
            doReturn(client)
                .whenever(mock).client
            doReturn(provider)
                .whenever(mock).getNamespaceProvider(any<HasMetadata>())
            doReturn(provider)
                .whenever(mock).getNamespaceProvider(any<String>())
        }
    }

    fun namespaceProvider(): NamespaceProvider{
        return mock()
    }

    fun <T: HasMetadata> resourceKindProvider(kind: Class<T>): IResourceKindProvider<T> {
        return mock() {
            doReturn(kind)
                .whenever(mock).kind
        }
    }
}