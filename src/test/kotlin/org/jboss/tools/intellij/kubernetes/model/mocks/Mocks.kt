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
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind

object Mocks {

    fun contextFactory(context: IActiveContext<HasMetadata, KubernetesClient>)
            : (IModelChangeObservable, NamedContext?) -> IActiveContext<HasMetadata, KubernetesClient> {
        return mock {
            doReturn(context)
                    .whenever(mock).invoke(any(), anyOrNull()) // anyOrNull() bcs NamedContext is nullable
        }
    }

    fun context(client: NamespacedKubernetesClient, currentNamespace: Namespace, context: NamedContext? = null)
            : IActiveContext<HasMetadata, KubernetesClient> {
        return mock {
            doNothing()
                .whenever(mock).startWatch()
            doReturn(client)
                .whenever(mock).client
            doReturn(currentNamespace.metadata.name)
                .whenever(mock).getCurrentNamespace()
            doReturn(context)
                    .whenever(mock).context
        }
    }

    fun <T : HasMetadata> namespacedResourceProvider(
            kind: ResourceKind<T>,
            resources: Collection<T>,
            namespace: Namespace,
            watchableSupplier: () -> Watchable<Watch, Watcher<HasMetadata>>? = { null })
            : INamespacedResourcesProvider<T> {
        return mock {
            doReturn(namespace.metadata.name)
                    .whenever(mock).namespace
            doReturn(kind)
                    .whenever(mock).kind
            doReturn(resources)
                    .whenever(mock).getAllResources()
            doReturn(watchableSupplier)
                    .whenever(mock).getWatchable()
        }
    }

    fun <T: HasMetadata> nonNamespacedResourceProvider(kind: ResourceKind<T>, resources: Collection<T>)
            : INonNamespacedResourcesProvider<T> {
        return mock {
            on { this.kind } doReturn kind
            on { getAllResources() } doReturn(resources)
        }
    }

    fun resourceModel(client: KubernetesClient): IResourceModel {
        return mock {
            on { getClient() } doReturn client
        }
    }
}