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

import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.client.OSClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.*
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.LogWatch
import org.mockito.Mockito
import java.util.concurrent.CompletableFuture

object Mocks {

    fun clientFactory(clientAdapter: ClientAdapter<KubernetesClient>): (String?, String?) -> ClientAdapter<out KubernetesClient> =
        mock<(String?, String?) -> ClientAdapter<out KubernetesClient>>().apply {
            doReturn(clientAdapter)
                .whenever(this).invoke(anyOrNull(), anyOrNull())
        }

    inline fun <reified A: ClientAdapter<out KubernetesClient>> clientAdapter(clientConfig: ClientConfig?, client: KubernetesClient? = null, openshiftAdapter: OSClientAdapter? = null): A {
        return mock<A>().apply {
            doReturn(clientConfig)
                .whenever(this).config
            doReturn(client)
                .whenever(this).get()
            doReturn(openshiftAdapter)
                .whenever(this).toOpenShift()
        }
    }

    fun contextFactory(context: IActiveContext<HasMetadata, KubernetesClient>?)
            : (ClientAdapter<out KubernetesClient>, IResourceModelObservable) -> IActiveContext<out HasMetadata, out KubernetesClient> {
        return mock {
            /**
             * Trying to use {@code org.mockito.kotlin.doReturn} leads to
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

    fun context(namedContext: NamedContext): IContext {
        val context = mock<IContext>()
        doReturn(namedContext.name)
            .whenever(context).name
        doReturn(namedContext.context.namespace)
            .whenever(context).namespace
        return context
    }

    fun activeContext(
        currentNamespace: Namespace,
        context: NamedContext,
        namespaceKind: ResourceKind<out HasMetadata> = NamespacesOperator.KIND,
        isOpenshift: Boolean = false
    ): IActiveContext<HasMetadata, KubernetesClient> {
        val mock = mock<IActiveContext<HasMetadata, KubernetesClient>>()
        doReturn(currentNamespace.metadata.name)
            .whenever(mock).getCurrentNamespace()
        doReturn(context.name)
            .whenever(mock).name
        doReturn(context.context.namespace)
            .whenever(mock).namespace
        doReturn(true)
            .whenever(mock).active
        doReturn(namespaceKind)
            .whenever(mock).namespaceKind
        doReturn(isOpenshift)
            .whenever(mock).isOpenShift()
        return mock
    }

    inline fun <reified T : HasMetadata, C : Client> namespacedResourceOperator(
        kind: ResourceKind<T>?,
        resources: Collection<T>,
        namespace: Namespace,
        crossinline watchOperation: (watcher: Watcher<in T>) -> Watch? = { null },
        deleteSuccess: Boolean = true,
        getReturnValue: T? = null
    ): INamespacedResourceOperator<T, C>  {
        val mock = mock<INamespacedResourceOperator<T, C>>()
        mockNamespacedOperatorMethods(namespace, kind, resources, watchOperation, deleteSuccess, getReturnValue, mock)
        return mock
    }

    inline fun <reified T : HasMetadata, C : Client> logAndExecWatchingNamespacedResourceOperator(
        kind: ResourceKind<T>,
        resources: Collection<T>,
        namespace: Namespace,
        crossinline watchOperation: (watcher: Watcher<in T>) -> Watch? = { null },
        deleteSuccess: Boolean = true,
        getReturnValue: T? = null
    ): INamespacedResourceOperator<T, C>  {
        val mock = mock<INamespacedResourceOperator<T, C>>(arrayOf(IWatchableLog::class, IWatchableExec::class))
        mockNamespacedOperatorMethods(namespace, kind, resources, watchOperation, deleteSuccess, getReturnValue, mock)
        mockLogWatcher(mock)
        mockExecWatcher(mock)
        return mock
    }

    inline fun <reified T : HasMetadata, C : Client> mockLogWatcher(mock: INamespacedResourceOperator<T, C>) {
        @Suppress("UNCHECKED_CAST")
        val watchable = mock as IWatchableLog<T>
        val logWatch: LogWatch = mock()
        doReturn(logWatch)
            .whenever(watchable).watchLog(any(), any(), any())
    }

    inline fun <reified T : HasMetadata, C : Client> mockExecWatcher(mock: INamespacedResourceOperator<T, C>) {
        @Suppress("UNCHECKED_CAST")
        val execWatcher = mock as IWatchableExec<T>
        val execWatch: ExecWatch = mock()
        doReturn(execWatch)
            .whenever(execWatcher).watchExec(any(), any(), any())
    }

    inline fun <C : Client, reified T : HasMetadata> mockNamespacedOperatorMethods(
        namespace: Namespace,
        kind: ResourceKind<T>?,
        resources: Collection<T>,
        crossinline watchOperation: (watcher: Watcher<in T>) -> Watch?,
        deleteSuccess: Boolean,
        getReturnValue: T?,
        mock: INamespacedResourceOperator<T, C>
        ) {
        doReturn(namespace.metadata.name)
            .whenever(mock).namespace
        doReturn(kind)
            .whenever(mock).kind
        doReturn(resources)
            .whenever(mock).allResources
        doAnswer { invocation ->
            watchOperation.invoke(invocation.getArgument(0))
        }
            .whenever(mock).watch(any(), any())
        doAnswer { invocation ->
            watchOperation.invoke(invocation.getArgument(0))
        }
            .whenever(mock).watchAll(any())

        doReturn(deleteSuccess)
            .whenever(mock).delete(any(), any())
        doReturn(getReturnValue)
            .whenever(mock).get(any())
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
            on { delete(any(), any()) } doReturn deleteSuccess
        }
    }

    fun resourceModel(): IResourceModel {
        return mock {}
    }

    fun kubernetesTypeInfo(kind: String?, apiGroup: String?): KubernetesTypeInfo {
        return mock {
            on { this.apiGroup } doReturn apiGroup
            on { this.kind } doReturn kind
        }
    }

    fun kubernetesResourceInfo(name: String?, namespace: String?, typeInfo: KubernetesTypeInfo): KubernetesResourceInfo {
        val mock = mock<KubernetesResourceInfo>()
        whenever(mock.name)
            .thenReturn(name)
        whenever(mock.namespace)
            .thenReturn(namespace)
        whenever(mock.kind)
            .thenAnswer { typeInfo.kind } // avoid unfinished stubbing error in mockito
        whenever(mock.apiGroup)
            .thenAnswer { typeInfo.apiGroup } // avoid unfinished stubbing error in mockito
        return mock
    }

    fun clientConfig(currentContext: NamedContext?, allContexts: List<NamedContext>): ClientConfig {
        val saveFuture: CompletableFuture<Boolean> = mock()
        return mock {
            on { this.currentContext } doReturn currentContext
            on { isCurrent(any()) } doAnswer { invocation ->
                invocation.getArgument<NamedContext>(0) == mock.currentContext
            }
            on { this.allContexts } doReturn allContexts
            on { this.save() } doReturn saveFuture
        }
    }
}
