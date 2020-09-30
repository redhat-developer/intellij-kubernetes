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
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.activeContext
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.context
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.contextFactory
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsProvider
import org.junit.Test
import java.util.function.Predicate

class ResourceModelTest {

    private val client: NamespacedKubernetesClient = mock()
    private val modelChange: IModelChangeObservable = mock()
    private val namespace: Namespace = resource("papa smurf")
    private val activeContext: IActiveContext<HasMetadata, KubernetesClient> = activeContext(client, namespace)
    private val contextFactory: (IModelChangeObservable, NamedContext?) -> IActiveContext<HasMetadata, KubernetesClient> =
            contextFactory(activeContext)

    private val namedContext1 =
            namedContext("ctx1", "namespace1", "cluster1", "user1")
    private val namedContext2 =
            namedContext("ctx2", "namespace2", "cluster2", "user2")
    private val namedContext3 =
            namedContext("ctx3", "namespace3", "cluster3", "user3")
/*
    private val config: KubeConfig = createKubeConfigContexts(
            context2,
            listOf(context1, context2, context3)
    )
*/

    private val contexts = createContexts(activeContext, listOf(
            context(namedContext1),
            activeContext,
            context(namedContext3)
    ))
    private val model: ResourceModel = ResourceModel(modelChange, contexts)

    @Test
    fun `#getResources(kind, CURRENT_NAMESPACE) should call context#getResources(kind, CURRENT_NAMESPACE)`() {
        // given
        // when
        model.getResources(NamespacedPodsProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
        // then
        verify(activeContext).getResources(NamespacedPodsProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
    }

    @Test
    fun `#getResources(kind, NO_NAMESPACE) should call context#getResources(kind, NO_NAMESPACE)`() {
        // given
        // when
        model.getResources(NamespacedPodsProvider.KIND, ResourcesIn.NO_NAMESPACE)
        // then
        verify(activeContext).getResources(NamespacedPodsProvider.KIND, ResourcesIn.NO_NAMESPACE)
    }

    @Test
    fun `#getResources(kind) should call predicate#test for each resource that is returned from context`() {
        // given
        val filter = mock<Predicate<Pod>>()
        whenever(activeContext.getResources(any<ResourceKind<HasMetadata>>(), any()))
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
        verify(activeContext).getResources(definition)
    }

    @Test
    fun `#setCurrentNamespace(name) should call context#setCurrentNamespace(name)`() {
        // given
        // when
        model.setCurrentNamespace("papa-smurf")
        // then
        verify(activeContext).setCurrentNamespace("papa-smurf")
    }

    @Test
    fun `#getCurrentNamespace() should call context#getCurrentNamespace()`() {
        // given
        // when
        model.getCurrentNamespace()
        // then
        verify(activeContext).getCurrentNamespace()
    }

    @Test
    fun `#invalidate(model) should clear contexts`() {
        // given
        // when
        model.invalidate(model)
        // then
        verify(contexts).clear()
    }

    @Test
    fun `#invalidate(model) should cause #allContexts to (drop cache and) load again`() {
        // given
        model.getAllContexts()
        verify(contexts, times(1)).all
        clearInvocations(contexts)
        // when
        model.invalidate(model)
        model.getAllContexts()
        // then
        verify(contexts, times(1)).all
    }

    @Test
    fun `#invalidate(class) should call context#invalidate(class)`() {
        // given
        // when
        model.invalidate(NamespacedPodsProvider.KIND)
        // then
        verify(activeContext).invalidate(NamespacedPodsProvider.KIND)
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
        verify(activeContext).invalidate(resource)
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
        verify(contexts).all
    }

    @Test
    fun `#allContexts should return contexts for all contexts in kube config`() {
        // given
        // when
        val numOf = model.getAllContexts().size
        // then
        assertThat(numOf).isEqualTo(contexts.all.size)
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

/*
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
*/
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

    private fun createContexts(currentContext: IActiveContext<*,*>?, allContexts: List<IContext>): IContexts {
        return mock {
            on { mock.all } doReturn allContexts
            on { mock.current } doReturn currentContext
        }
    }
}
