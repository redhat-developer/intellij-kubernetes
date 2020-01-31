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

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.DoneablePod
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.PodResource
import org.jboss.tools.intellij.kubernetes.model.NamespaceListOperation
import org.mockito.ArgumentMatchers

object ClientMocks {

    val NAMESPACE1 = resource<Namespace>("namespace1")
    val NAMESPACE2 = resource<Namespace>("namespace2")
    val NAMESPACE3 = resource<Namespace>("namespace3")

    val POD1 = resource<Pod>("pod1")
    val POD2 = resource<Pod>("pod2")
    val POD3 = resource<Pod>("pod3")

    fun client(currentNamespace: String?, namespaces: Array<Namespace>): NamespacedKubernetesClient {
        val namespaceList = mock<NamespaceList> {
            on { items } doReturn namespaces.asList()
        }
        val namespacesMock = mock<NamespaceListOperation> {
                on { list() } doReturn namespaceList
            }
        val config = mock<Config>()

        return mock<NamespacedKubernetesClient> {
            on { namespaces() } doReturn namespacesMock
            on { namespace } doReturn currentNamespace
            on { configuration } doReturn config
        }
    }

    fun inNamespace(client: NamespacedKubernetesClient): NamespacedKubernetesClient {
        val namespaceClient = mock<NamespacedKubernetesClient>()
        whenever(client.inNamespace(ArgumentMatchers.anyString()))
            .doReturn(namespaceClient)
        return namespaceClient
    }

    fun pods(client: NamespacedKubernetesClient): MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> {
        val podsOp = mock<MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>>>()
        whenever(client.pods())
            .doReturn(podsOp)
        return podsOp
    }

    fun list(mixedOp: MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>>): PodList {
        val podList = mock<PodList>()
        whenever(mixedOp.list())
            .doReturn(podList)
        return podList
    }

    fun items(podList: PodList, vararg pods: Pod ) {
        val returnedPods = listOf(*pods)
        whenever(podList.items)
            .doReturn(returnedPods)
    }

    fun withName(mixedOp: MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>>, pod: Pod) {
        val podResource = mock<PodResource<Pod, DoneablePod>>()
        whenever(podResource.get())
            .doReturn(pod)
        whenever(mixedOp.withName(pod.metadata.name))
            .doReturn(podResource)
    }

    inline fun <reified T: HasMetadata> resource(name: String, namespace: String? = null): T {
        val metadata = mock<ObjectMeta> {
            on { getName() } doReturn name
            if (namespace != null) {
                on { getNamespace() } doReturn namespace
            }
        }
        return mock {
            on { getMetadata() } doReturn metadata
        }
    }

}