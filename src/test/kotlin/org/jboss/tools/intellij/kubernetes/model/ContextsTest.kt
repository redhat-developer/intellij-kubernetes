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
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.assertj.core.api.Assertions
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks
import org.junit.Test

class ContextsTest {

	private val client: NamespacedKubernetesClient = mock()
	private val observable: IModelChangeObservable = mock()
	private val namespace: Namespace = ClientMocks.resource("papa smurf")
	private val currentContext: IActiveContext<HasMetadata, KubernetesClient> = Mocks.activeContext(client, namespace)
	private val contextFactory: (IModelChangeObservable, NamedContext?) -> IActiveContext<HasMetadata, KubernetesClient> =
			Mocks.contextFactory(currentContext)
	private val namedContext1 =
			ClientMocks.namedContext("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
			ClientMocks.namedContext("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
			ClientMocks.namedContext("ctx3", "namespace3", "cluster3", "user3")
	private val config = createKubeConfig(namedContext2, listOf(namedContext1, namedContext2, namedContext3))
	private val contexts = spy(TestableContext(observable, contextFactory, config))

	@Test
	fun `#clear() should close existing context`() {
		// given
		// when
		contexts.clear()
		// then
		verify(currentContext).close()
	}

	@Test
	fun `#clear() should notify change of current context`() {
		// given
		// when
		contexts.clear()
		// then
		verify(observable).fireModified(currentContext)
	}

	@Test
	fun `#clear() should NOT notify change of current context if context is null`() {
		// given
		val config = createKubeConfig(null, listOf(namedContext1, namedContext2, namedContext3))
		val contexts = spy(TestableContext(observable, contextFactory, config))
		// when
		contexts.clear()
		// then
		verify(observable, never()).fireModified(any())
	}

	@Test
	fun `#clear() should clear current context`() {
		// given
		// when
		contexts.clear()
		// then
		verify(contexts).current = null
	}

	@Test
	fun `#clear() should clear all contexts`() {
		// given
		// when
		contexts.clear()
		// then
		verify(contexts.all).clear()
	}

	@Test
	fun `#refresh() should close existing context`() {
		// given
		// when
		contexts.refresh()
		// then
		verify(currentContext).close()
	}

	@Test
	fun `#refresh() should notify change of Contexts`() {
		// given
		// when
		contexts.refresh()
		// then
		verify(observable).fireModified(contexts)
	}

	@Test
	fun `#refresh() should clear #current`() {
		// given
		// when
		contexts.refresh()
		// then
		verify(contexts).current = null
	}

	@Test
	fun `#refresh() should clear #all`() {
		// given
		// when
		contexts.refresh()
		// then
		verify(contexts.all).clear()
	}

    @Test
    fun `#all should not load twice`() {
        // given
        clearInvocations(contexts)
        // when
        contexts.all
		contexts.all
        // then
        verify(config, times(1)).contexts
    }

    @Test
    fun `#current should create new context if there's no context yet`() {
        // given
        // when
        contexts.current
        // then
        verify(contextFactory).invoke(any(), anyOrNull())
    }

    @Test
    fun `#current() should create new context if context#clear() was called before`() {
        // given
        contexts.current // trigger creation of context
        clearInvocations(contextFactory)
		contexts.clear()
        // when
		contexts.current
        // then
        verify(contextFactory).invoke(any(), anyOrNull()) // anyOrNull() bcs NamedContext is nullable
    }

	@Test
	fun `#setCurrentContext(context) should create new active context for given context`() {
		// given
		contexts.current
		clearInvocations(contextFactory)
		// when
		contexts.setCurrent(mock())
		// then
		verify(contextFactory).invoke(any(), anyOrNull())
	}

	@Test
	fun `#setCurrentContext(context) should replace context in #allContexts`() {
		// given
		val newCurrentContext = Mocks.activeContext(client, namespace, namedContext3)
		contexts.all // create all contexts
		val currentContext = contexts.current
		Assertions.assertThat(currentContext).isNotEqualTo(newCurrentContext)
		Assertions.assertThat(contexts.all).contains(currentContext)
		Assertions.assertThat(contexts.all).doesNotContain(newCurrentContext)
		doReturn(newCurrentContext)
				.whenever(contextFactory).invoke(any(), anyOrNull()) // returned on 2nd call
		// when
		contexts.setCurrent(newCurrentContext)
		// then
		Assertions.assertThat(contexts.all).contains(newCurrentContext)
	}

    private fun createKubeConfig(currentContext: NamedContext?, allContexts: List<NamedContext>): KubeConfig {
        return mock {
            on { contexts } doReturn allContexts
            on { this.currentContext } doReturn currentContext
            if (currentContext != null) {
                on { isCurrent(eq(currentContext)) } doReturn true
            }
        }
    }

	private class TestableContext(observable: IModelChangeObservable,
								  factory: (IModelChangeObservable, NamedContext) -> IActiveContext<out HasMetadata, out KubernetesClient>,
								  override val config: KubeConfig
	): Contexts(observable, factory) {

		override val all: MutableList<IContext> = spy(super.all)

		public override fun refresh() {
			super.refresh()
		}
	}
}