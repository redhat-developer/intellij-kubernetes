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
package org.jboss.tools.intellij.kubernetes.model.mocks

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.cluster.ICluster
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider

object Mocks {

    fun clusterFactory(cluster: ICluster<HasMetadata, KubernetesClient>): (IModelChangeObservable) -> ICluster<HasMetadata, KubernetesClient> {
        return mock {
            doReturn(cluster)
                .whenever(mock).invoke(any())
        }
    }

    fun cluster(client: NamespacedKubernetesClient, currentNamespace: Namespace): ICluster<HasMetadata, KubernetesClient> {
        return mock {
            doNothing()
                .whenever(mock).startWatch()
            doReturn(client)
                .whenever(mock).client
            doReturn(currentNamespace.metadata.name)
                .whenever(mock).getCurrentNamespace()
        }
    }

    fun <T: HasMetadata> namespacedResourceProvider(resources: Collection<T>, namespace: Namespace): INamespacedResourcesProvider<T> {
        return mock {
            on { getAllResources(namespace.metadata.name) } doReturn resources
        }
    }

    fun <T: HasMetadata> nonNamespacedResourceProvider(resources: Collection<T>): INonNamespacedResourcesProvider<T> {
        return mock {
            on { getAllResources() } doReturn(resources)
        }
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

    fun resourceModel(client: KubernetesClient): IResourceModel {
        return mock<IResourceModel> {
            on { getClient() } doReturn client
        }
    }
}