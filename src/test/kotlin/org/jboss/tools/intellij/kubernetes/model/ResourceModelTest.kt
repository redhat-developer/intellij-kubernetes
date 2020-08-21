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
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.context
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.contextFactory
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsProvider
import org.jboss.tools.intellij.kubernetes.model.util.KubeConfig
import org.junit.Test
import java.util.function.Predicate

class ResourceModelTest {

    private val client: NamespacedKubernetesClient = mock()
    private val modelChange: IModelChangeObservable = mock()
    private val namespace: Namespace = resource("papa smurf")
    private val context: IActiveContext<HasMetadata, KubernetesClient> = context(client, namespace)
    private val contextFactory: (IModelChangeObservable, NamedContext?) -> IActiveContext<HasMetadata, KubernetesClient> =
            contextFactory(context)

    private val context1 = namedContext("ctx1", "namespace1", "cluster1", "user1")
    private val context2 = namedContext("ctx2", "namespace2", "cluster2", "user2")
    private val context3 = namedContext("ctx3", "namespace3", "cluster3", "user3")
    private val config: KubeConfig = createKubeConfigContexts(
            context2,
            listOf(context1, context2, context3)
    )

    private val model: ResourceModel = ResourceModel(modelChange, contextFactory, config)

    @Test
    fun `#getResources(kind, CURRENT_NAMESPACE) should call context#getResources(kind, CURRENT_NAMESPACE)`() {
        // given
        // when
        model.getResources(NamespacedPodsProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
        // then
        verify(context).getResources(NamespacedPodsProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
    }

    @Test
    fun `#getResources(kind, NO_NAMESPACE) should call context#getResources(kind, NO_NAMESPACE)`() {
        // given
        // when
        model.getResources(NamespacedPodsProvider.KIND, ResourcesIn.NO_NAMESPACE)
        // then
        verify(context).getResources(NamespacedPodsProvider.KIND, ResourcesIn.NO_NAMESPACE)
    }

    @Test
    fun `#getResources(kind) should call predicate#test for each resource that is returned from context`() {
        // given
        val filter = mock<Predicate<Pod>>()
        whenever(context.getResources(any<ResourceKind<HasMetadata>>(), any()))
                .thenReturn(listOf(
                        POD1,
                        POD2,
                        POD3))
        // when
        model.getResources(NamespacedPodsProvider.KIND, ResourcesIn.NO_NAMESPACE, filter)
        // then
        verify(filter, times(3)).test(any())
    }

    @Test
    fun `#getCustomResources should call activeContext#getCustomResources`() {
        // given
        val definition = mock<CustomResourceDefinition>()
        // when
        model.getResources(definition)
        // then
        verify(context).getCustomResources(definition)
    }

    @Test
    fun `#setCurrentNamespace(name) should call context#setCurrentNamespace(name)`() {
        // given
        // when
        model.setCurrentNamespace("papa-smurf")
        // then
        verify(context).setCurrentNamespace("papa-smurf")
    }

    @Test
    fun `#getCurrentNamespace() should call context#getCurrentNamespace()`() {
        // given
        // when
        model.getCurrentNamespace()
        // then
        verify(context).getCurrentNamespace()
    }

    @Test
    fun `#invalidate(model) should create new context`() {
        // given
        model.getCurrentContext() // trigger creation of context
        clearInvocations(contextFactory)
        // when
        model.invalidate(model)
        // then
        verify(contextFactory).invoke(any(), anyOrNull()) // anyOrNull() bcs NamedContext is nullable
    }

    @Test
    fun `#invalidate(model) should close existing context`() {
        // given
        // when
        model.invalidate(model)
        // then
        verify(context).close()
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
    fun `#invalidate(model) should cause #allContexts to (drop cache and) load again`() {
        // given
        model.getAllContexts()
        verify(config, times(1)).contexts
        clearInvocations(config)
        // when
        model.invalidate(model)
        model.getAllContexts()
        // then
        verify(config, times(1)).contexts
    }

    @Test
    fun `#invalidate(class) should call context#invalidate(class)`() {
        // given
        // when
        model.invalidate(NamespacedPodsProvider.KIND)
        // then
        verify(context).invalidate(NamespacedPodsProvider.KIND)
    }

    @Test
    fun `#invalidate(class) should fire class change`() {
        // given
        // when
        model.invalidate(NamespacedPodsProvider.KIND)
        // then
        verify(modelChange).fireModified(NamespacedPodsProvider.KIND)
    }

    @Test
    fun `#invalidate(resource) should call context#invalidate(resource)`() {
        // given
        val resource = mock<HasMetadata>()
        // when
        model.invalidate(resource)
        // then
        verify(context).invalidate(resource)
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

    @Test
    fun `#allContexts should get all contexts in kube config`() {
        // given
        // when
        model.getAllContexts()
        // then
        verify(config).contexts
    }

    @Test
    fun `#allContexts should return contexts for all contexts in kube config`() {
        // given
        // when
        val numOf = model.getAllContexts().size
        // then
        assertThat(numOf).isEqualTo(config.contexts.size)
    }

    @Test
    fun `#allContexts should not load twice`() {
        // given
        clearInvocations(config)
        // when
        model.getAllContexts()
        model.getAllContexts()
        // then
        verify(config, times(1)).contexts
    }

    @Test
    fun `#allContexts should not create currentContext if exists already`() {
        // given
        model.invalidate(model)
        model.getCurrentContext()
        clearInvocations(contextFactory)
        // when
        model.getAllContexts()
        // then
        verify(contextFactory, never()).invoke(any(), anyOrNull())
    }

    @Test
    fun `#currentContext should create new context if there's no context yet`() {
        // given
        // when
        model.getCurrentContext()
        // then
        verify(contextFactory).invoke(any(), anyOrNull())
    }

    @Test
    fun `#getCurrentContext() should not create new active context if there's no current context in kubeconfig`() {
        // given
        val config: KubeConfig = createKubeConfigContexts(
                null,
                listOf(context1, context2, context3)
        )
        val model = ResourceModel(modelChange, contextFactory, config)
        // when
        model.getCurrentContext()
        // then
        verify(contextFactory, never()).invoke(any(), anyOrNull())
    }

    @Test
    fun `#setCurrentContext(context) should not create new active context if setting (same) existing current context`() {
        // given
        val currentContext = model.getCurrentContext()
        clearInvocations(contextFactory)
        // when
        model.setCurrentContext(currentContext!!)
        // then
        verify(contextFactory, never()).invoke(any(), anyOrNull())
    }

    @Test
    fun `#setCurrentContext(context) should create new active context for given context`() {
        // given
        model.getCurrentContext()
        clearInvocations(contextFactory)
        // when
        model.setCurrentContext(mock())
        // then
        verify(contextFactory).invoke(any(), anyOrNull())
    }

    @Test
    fun `#setCurrentContext(context) should replace context in #allContexts`() {
        // given
        val newCurrentContext = context(client, namespace, context3)
        model.getAllContexts() // create all contexts
        val currentContext = model.getCurrentContext()
        assertThat(currentContext).isNotEqualTo(newCurrentContext)
        assertThat(model.getAllContexts()).contains(currentContext)
        assertThat(model.getAllContexts()).doesNotContain(newCurrentContext)
        doReturn(newCurrentContext)
                .whenever(contextFactory).invoke(any(), anyOrNull())
        // when
        model.setCurrentContext(newCurrentContext)
        // then
        assertThat(model.getAllContexts()).contains(newCurrentContext)
    }

    private fun createKubeConfigContexts(currentContext: NamedContext?, allContexts: List<NamedContext>): KubeConfig {
        return mock {
            on { contexts } doReturn allContexts
            on { this.currentContext } doReturn currentContext
            if (currentContext != null) {
                on { isCurrent(eq(currentContext)) } doReturn true
            }
        }
    }
}
