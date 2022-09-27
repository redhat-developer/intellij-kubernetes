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
package com.redhat.devtools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.activeContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.context
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import org.junit.Test
import java.util.function.Predicate

class ResourceModelTest {

    private val modelChange: IResourceModelObservable = mock()
    private val namespace: Namespace = resource("papa smurf", null, "papaUid", "v1")
    private val activeContext: IActiveContext<HasMetadata, KubernetesClient> = activeContext(namespace, mock())

    private val namedContext1 =
            namedContext("ctx1", "namespace1", "cluster1", "user1")
    private val namedContext2 =
            namedContext("ctx2", "namespace2", "cluster2", "user2")
    private val namedContext3 =
            namedContext("ctx3", "namespace3", "cluster3", "user3")

    private val allContexts = createContexts(activeContext, listOf(
            context(namedContext1),
            activeContext,
            context(namedContext3)
    ))

    private val model: ResourceModel = object : ResourceModel() {
        override val modelChange: IResourceModelObservable = this@ResourceModelTest.modelChange
        override val allContexts: IAllContexts = this@ResourceModelTest.allContexts
    }

    @Test
    fun `#getResources(kind, CURRENT_NAMESPACE) should call context#getResources(kind, CURRENT_NAMESPACE)`() {
        // given
        // when
        model.getAllResources(NamespacedPodsOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
        // then
        verify(activeContext).getAllResources(NamespacedPodsOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
    }

    @Test
    fun `#getResources(kind, NO_NAMESPACE) should call context#getResources(kind, NO_NAMESPACE)`() {
        // given
        // when
        model.getAllResources(NamespacedPodsOperator.KIND, ResourcesIn.NO_NAMESPACE)
        // then
        verify(activeContext).getAllResources(NamespacedPodsOperator.KIND, ResourcesIn.NO_NAMESPACE)
    }

    @Test
    fun `#getResources(kind) should call predicate#test for each resource that is returned from context`() {
        // given
        val filter = mock<Predicate<Pod>>()
        whenever(activeContext.getAllResources(any<ResourceKind<HasMetadata>>(), any()))
                .thenReturn(listOf(
                        POD1,
                        POD2,
                        POD3))
        // when
        model.getAllResources(NamespacedPodsOperator.KIND, ResourcesIn.NO_NAMESPACE, filter)
        // then
        verify(filter, times(3)).test(any())
    }

    @Test
    fun `#getCustomResources should call activeContext#getCustomResources`() {
        // given
        val definition = mock<CustomResourceDefinition>()
        // when
        model.getAllResources(definition)
        // then
        verify(activeContext).getAllResources(definition)
    }

    @Test
    fun `#setCurrentNamespace(name) should call contexts#setCurrentNamespace(name)`() {
        // given
        // when
        model.setCurrentNamespace("papa-smurf")
        // then
        verify(allContexts).setCurrentNamespace("papa-smurf")
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
        verify(allContexts).refresh()
    }

    @Test
    fun `#invalidate(model) should cause #allContexts to (drop cache and) load again`() {
        // given
        model.getAllContexts()
        verify(allContexts, times(1)).all
        clearInvocations(allContexts)
        // when
        model.invalidate(model)
        model.getAllContexts()
        // then
        verify(allContexts, times(1)).all
    }

    @Test
    fun `#invalidate(kind) should call context#invalidate(class)`() {
        // given
        // when
        model.invalidate(NamespacedPodsOperator.KIND)
        // then
        verify(activeContext).invalidate(NamespacedPodsOperator.KIND)
    }

    @Test
    fun `#invalidate(resource) should call context#invalidate(resource)`() {
        // given
        val resource = mock<HasMetadata>()
        // when
        model.invalidate(resource)
        // then
        verify(activeContext).replaced(resource)
    }

    @Test
    fun `#allContexts should get all contexts in AllContexts`() {
        // given
        // when
        model.getAllContexts()
        // then
        verify(allContexts).all
    }

    private fun createContexts(currentContext: IActiveContext<*,*>?, allContexts: List<IContext>): IAllContexts {
        return mock {
            on { mock.all } doReturn allContexts
            on { mock.current } doReturn currentContext
        }
    }
}
