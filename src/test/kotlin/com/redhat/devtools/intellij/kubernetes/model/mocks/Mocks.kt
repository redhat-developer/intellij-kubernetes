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
package com.redhat.devtools.intellij.kubernetes.model.mocks

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import org.mockito.Mockito
import java.util.function.Supplier

object Mocks {

    fun contextFactory(context: IActiveContext<HasMetadata, KubernetesClient>?)
            : (IModelChangeObservable, NamedContext?) -> IActiveContext<HasMetadata, KubernetesClient> {
        return mock {
            /**
             * Trying to use {@code com.nhaarman.mockitokotlin2.doReturn} leads to
             * "Overload Resolution Ambiguity" with {@code org.mockito.Mockito.doReturn} in intellij.
             * Gradle compiles it just fine
             *
             * @see <a href="https://youtrack.jetbrains.com/issue/KT-22961">KT-22961</a>
             * @see <a href="https://stackoverflow.com/questions/38779666/how-to-fix-overload-resolution-ambiguity-in-kotlin-no-lambda">fix-overload-resolution-ambiguity</a>
             */
            Mockito.doReturn(context)
                .`when`(mock).invoke(any(), anyOrNull()) // anyOrNull() bcs NamedContext is nullable
        }
    }

    fun context(namedContext: NamedContext)
            : IContext {
        return mock {
            Mockito.doReturn(namedContext)
                .`when`(mock).context
        }
    }

    fun activeContext(currentNamespace: Namespace, context: NamedContext)
            : IActiveContext<HasMetadata, KubernetesClient> {
        return mock {
            Mockito.doReturn(currentNamespace.metadata.name)
                .`when`(mock).getCurrentNamespace()
            Mockito.doReturn(context)
                .`when`(mock).context
        }
    }

    fun <T : HasMetadata, C: Client> namespacedResourceOperator(
        kind: ResourceKind<T>,
        resources: Collection<T>,
        namespace: Namespace,
        watchableSupplier: Supplier<Watchable<Watcher<T>>?> = Supplier { null })
            : INamespacedResourceOperator<T, C> {
        return mock {
            Mockito.doReturn(namespace.metadata.name)
                .`when`(mock).namespace
            Mockito.doReturn(kind as Any?)
                .`when`(mock).kind
            Mockito.doReturn(resources)
                .`when`(mock).allResources
            Mockito.doReturn(watchableSupplier)
                .`when`(mock).getKindWatchable()
        }
    }

    fun <T : HasMetadata, C: Client> nonNamespacedResourceOperator(
        kind: ResourceKind<T>,
        resources: Collection<T>,
        watchableSupplier: Supplier<Watchable<Watcher<T>>?> = Supplier { null },
        deleteSuccess: Boolean = true)
            : INonNamespacedResourceOperator<T, C> {
        return mock {
            on { this.kind } doReturn kind
            on { allResources } doReturn resources
            on { getKindWatchable() } doReturn watchableSupplier
            on { delete(any()) } doReturn deleteSuccess
        }
    }

    fun resourceModel(): IResourceModel {
        return mock {}
    }
}
