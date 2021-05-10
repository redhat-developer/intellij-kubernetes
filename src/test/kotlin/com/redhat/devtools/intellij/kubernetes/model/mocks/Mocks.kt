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

import com.nhaarman.mockitokotlin2.*
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watcher
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.client.Watch
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

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

    inline fun <reified T : HasMetadata, C : Client> namespacedResourceOperator(
        kind: ResourceKind<T>,
        resources: Collection<T>,
        namespace: Namespace,
        crossinline watchOperation: (watcher: Watcher<in T>) -> Watch? = { null }
    ): INamespacedResourceOperator<T, C> {
        return mock {
            Mockito.doReturn(namespace.metadata.name)
                .`when`(mock).namespace
            on { this.kind } doReturn kind
            on { allResources } doReturn resources
            on { watch(any(), any()) } doAnswer { invocation ->
                watchOperation.invoke(invocation.getArgument(0))
            }
            on { watchAll(any()) } doAnswer { invocation ->
                watchOperation.invoke(invocation.getArgument(0))
            }
        }
    }

    inline fun <reified T : HasMetadata, C : Client> nonNamespacedResourceOperator(
        kind: ResourceKind<T>,
        resources: Collection<T>,
        crossinline watchOperation: (watcher: Watcher<in T>) -> Watch? = { null },
        deleteSuccess: Boolean = true
    ) : INonNamespacedResourceOperator<T, C> {
        return mock {
            on { this.kind } doReturn kind
            on { allResources } doReturn resources
            on { watch(any(), any()) } doAnswer { invocation ->
                watchOperation.invoke(invocation.getArgument(0))
            }
            on { watchAll(any()) } doAnswer { invocation ->
                watchOperation.invoke(invocation.getArgument(0))
            }
            on { delete(any()) } doReturn deleteSuccess
        }
    }

    fun resourceModel(): IResourceModel {
        return mock {}
    }
}
