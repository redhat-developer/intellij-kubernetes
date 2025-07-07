/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.context

import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.OSClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.activeContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.clientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import org.junit.Before
import org.junit.Test

class LazyOpenShiftContextTest {

    private val context = namedContext("leia")
    private var modelChange: IResourceModelObservable = mock()

    private lateinit var kubernetesClientAdapter: KubeClientAdapter

    private lateinit var openshiftClientAdapter: OSClientAdapter

    private lateinit var kubernetesContext: IActiveContext<out HasMetadata, out KubernetesClient>
    private lateinit var kubernetesContextFactory: (context: NamedContext,
                                                    modelChange: IResourceModelObservable,
                                                    client: KubeClientAdapter
    ) -> IActiveContext<out HasMetadata, out KubernetesClient>
    private lateinit var openshiftContext: IActiveContext<out HasMetadata, out KubernetesClient>
    private lateinit var openshiftContextFactory: (
        client: OSClientAdapter,
        observable: IResourceModelObservable
    ) -> IActiveContext<out HasMetadata, out KubernetesClient>?

    @Before
    fun before() {
        this.openshiftClientAdapter = clientAdapter<OSClientAdapter>(
            null,
            client(
                "yoda",
                emptyArray()
            )
        )
        this.openshiftContext = activeContext(
            resource<Namespace>("jedi"),
            context,
            ProjectsOperator.KIND,
            isOpenshift = true
        )
        this.openshiftContextFactory = mock {
            on { invoke(any(), any()) }
                .thenReturn(openshiftContext)
        }

        this.kubernetesClientAdapter = clientAdapter<KubeClientAdapter>(
            null, client(
                "skywalker",
                emptyArray()
            ),
            openshiftClientAdapter
        )

        this.kubernetesContext = activeContext(
            resource<Namespace>("jedi"),
            context,
            NamespacesOperator.KIND,
            isOpenshift = false
        )
        this.kubernetesContextFactory = mock {
            on { invoke(any(), any(), any()) }
                .thenReturn(kubernetesContext)
        }
    }

    @Test
    fun `#constructor creates a kubernetes context`() {
        // given
        // when
        createClusterAwareContext()
        // then
        verify(kubernetesContextFactory)
            .invoke(context, modelChange, kubernetesClientAdapter)
    }

    @Test
    fun `#constructor creates an openshift context if client can adapt to OpenShift`() {
        // given
        doReturn(true)
            .whenever(kubernetesClientAdapter).canAdaptToOpenShift()
        // when
        createClusterAwareContext()
        // then
        verify(openshiftContextFactory)
            .invoke(openshiftClientAdapter, modelChange)
    }

    @Test
    fun `#constructor does not create an openshift context if client cannot adapt to OpenShift`() {
        // given
        doReturn(false)
            .whenever(kubernetesClientAdapter).canAdaptToOpenShift()
        // when
        createClusterAwareContext()
        // then
        verify(openshiftContextFactory, never())
            .invoke(openshiftClientAdapter, modelChange)
    }

    @Test
    fun `#constructor notifies model change if it created an openshift context`() {
        // given
        doReturn(true)
            .whenever(kubernetesClientAdapter).canAdaptToOpenShift()
        // when
        val context = createClusterAwareContext()
        // then
        verify(modelChange)
            .fireModified(context)
    }

    @Test
    fun `#constructor does NOT notify model change if it did NOT create an openshift context`() {
        // given
        doReturn(false)
            .whenever(kubernetesClientAdapter).canAdaptToOpenShift()
        // when
        val context = createClusterAwareContext()
        // then
        verify(modelChange, never())
            .fireModified(context)
    }

    @Test
    fun `#isOpenShift delegates to openshift context if client can adapt to OpenShift`() {
        // given
        doReturn(true)
            .whenever(kubernetesClientAdapter).canAdaptToOpenShift()
        val context = createClusterAwareContext()
        // when
        context.isOpenShift()
        // then
        verify(kubernetesContext, never())
            .isOpenShift()
        verify(openshiftContext)
            .isOpenShift()
    }

    @Test
    fun `#isOpenShift delegates to kubernetes context if client CANNOT adapt to OpenShift`() {
        // given
        doReturn(false)
            .whenever(kubernetesClientAdapter).canAdaptToOpenShift()
        val context = createClusterAwareContext()
        // when
        context.isOpenShift()
        // then
        verify(kubernetesContext)
            .isOpenShift()
        verify(openshiftContext, never())
            .isOpenShift()
    }

    private fun createClusterAwareContext(): LazyOpenShiftContext {
        return LazyOpenShiftContext(
            context,
            modelChange,
            kubernetesClientAdapter,
            kubernetesContextFactory,
            openshiftContextFactory,
            { runnable -> runnable.invoke() }
        )
    }
}