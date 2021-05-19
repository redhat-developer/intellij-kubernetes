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

import com.nhaarman.mockitokotlin2.*
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.activeContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.context
import org.junit.Test
import org.mockito.Mockito

class ContextsTest {

	private val modelChange: IModelChangeObservable = mock()
	private val namespace: Namespace = ClientMocks.resource("papa smurf", null, "someNamespaceUid", "v1")
	private val namedContext1 =
			ClientMocks.namedContext("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
			ClientMocks.namedContext("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
			ClientMocks.namedContext("ctx3", "namespace3", "cluster3", "user3")
	private val config = createClientConfig(namedContext2, listOf(namedContext1, namedContext2, namedContext3))
	private val currentContext: IActiveContext<HasMetadata, KubernetesClient> = activeContext(namespace, namedContext2)
	private val contextFactory: (IModelChangeObservable, NamedContext?) -> IActiveContext<HasMetadata, KubernetesClient> =
		Mocks.contextFactory(currentContext)
	private val contexts = spy(TestableContext(modelChange, contextFactory, config))

	@Test
	fun `#clear() should close existing context`() {
		// given
		// when
		contexts.clear()
		// then
		verify(currentContext).close()
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
		verify(modelChange).fireModified(contexts)
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
	fun `#setCurrentContext(context) should NOT create new active context if existing context is set`() {
		// given
		contexts.current // create current context
		clearInvocations(contextFactory) // clear invocation so that it's not counted
		// when
		contexts.setCurrent(currentContext)
		// then
		verify(contextFactory, never()).invoke(any(), anyOrNull())
	}

	@Test
	fun `#setCurrentContext(context) should create new active context if new context is set`() {
		// given
		contexts.current // create current context
		clearInvocations(contextFactory) // clear invocation so that it's not counted
		// when
		contexts.setCurrent(context(namedContext3))
		// then
		verify(contextFactory).invoke(any(), anyOrNull())
	}

	@Test
	fun `#setCurrentContext(context) should set new current context in #allContexts`() {
		// given
		val config = createClientConfig(null, listOf(namedContext1, namedContext2, namedContext3))
		val contexts = spy(TestableContext(modelChange, contextFactory, config))
		assertThat(contexts.current).isNull()
		val newCurrentContext = activeContext(namespace, namedContext3)
		contexts.all // create all contexts
		/**
		 * Trying to use {@code com.nhaarman.mockitokotlin2.doReturn} leads to
		 * "Overload Resolution Ambiguity" with {@code org.mockito.Mockito.doReturn} in intellij.
		 * Gradle compiles it just fine
		 *
		 * @see <a href="https://youtrack.jetbrains.com/issue/KT-22961">KT-22961</a>
		 * @see <a href="https://stackoverflow.com/questions/38779666/how-to-fix-overload-resolution-ambiguity-in-kotlin-no-lambda">fix-overload-resolution-ambiguity</a>
		 */
		Mockito.doReturn(newCurrentContext)
			.`when`(contextFactory).invoke(any(), anyOrNull()) // returned on 2nd call
		// when
		contexts.setCurrent(newCurrentContext)
		// then
		assertThat(contexts.all).contains(newCurrentContext)
	}

	@Test
	fun `#setCurrentContext(context) should return 'true' if new context was set`() {
		// given
		val config = createClientConfig(null, listOf(namedContext1, namedContext2, namedContext3))
		val contexts = spy(TestableContext(modelChange, contextFactory, config))
		assertThat(contexts.current).isNull()
		val newCurrentContext = activeContext(namespace, namedContext3)
		contexts.all // create all contexts
		Mockito.doReturn(newCurrentContext)
			.`when`(contextFactory).invoke(any(), anyOrNull()) // returned on 2nd call
		// when
		val currentContextIsSet = contexts.setCurrent(newCurrentContext)
		// then
		assertThat(contexts.current).isEqualTo(newCurrentContext)
		assertThat(currentContextIsSet).isTrue()
	}

	@Test
	fun `#setCurrentContext(context) should replace context in #allContexts`() {
		// given
		val newCurrentContext = activeContext(namespace, namedContext3)
		contexts.all // create all contexts
		val currentContext = contexts.current
		assertThat(currentContext).isNotEqualTo(newCurrentContext)
		assertThat(contexts.all).contains(currentContext)
		assertThat(contexts.all).doesNotContain(newCurrentContext)
		Mockito.doReturn(newCurrentContext)
				.`when`(contextFactory).invoke(any(), anyOrNull()) // returned on 2nd call
		// when
		contexts.setCurrent(newCurrentContext)
		// then
		assertThat(contexts.all).contains(newCurrentContext)
		assertThat(contexts.all).doesNotContain(currentContext)
	}

	@Test
	fun `#setCurrentContext(context) should close current context`() {
		// given
		val newCurrentContext = activeContext(namespace, namedContext3)
		contexts.all // create all contexts
		val currentContext = contexts.current!!
		Mockito.doReturn(newCurrentContext)
			.`when`(contextFactory).invoke(any(), anyOrNull()) // returned on 2nd call
		// when
		contexts.setCurrent(newCurrentContext)
		// then
		verify(currentContext).close()
	}

	@Test
	fun `#setCurrentNamespace(namespace) should call config#setCurrentNamespace`() {
		// given
		contexts.current // create current context
		whenever(currentContext.setCurrentNamespace(any()))
			.doReturn(true)
		clearInvocations(contextFactory) // clear invocation so that it's not counted
		val newNamespace = "42"
		// when
		contexts.setCurrentNamespace(newNamespace)
		// then
		verify(config).setCurrentNamespace(newNamespace)
	}

	@Test
	fun `#setCurrentNamespace(namespace) should return true if new namespace was set`() {
		// given
		contexts.current // create current context
		whenever(currentContext.setCurrentNamespace(any()))
			.doReturn(true)
		clearInvocations(contextFactory) // clear invocation so that it's not counted
		// when
		val success = contexts.setCurrentNamespace("42")
		// then
		assertThat(success).isTrue()
	}

	@Test
	fun `#setCurrentNamespace(namespace) should call clientConfig#save if new namespace was set`() {
		// given
		whenever(currentContext.setCurrentNamespace(any()))
			.doReturn(true)
		contexts.current // create current context
		clearInvocations(contextFactory) // clear invocation so that it's not counted
		// when
		contexts.setCurrentNamespace("azrael")
		// then
		verify(config).save()
	}

	@Test
	fun `#setCurrentNamespace(namespace) should NOT call config#setCurrentNamespace if currentContext#setCurrentNamespace returned false`() {
		// given
		whenever(currentContext.setCurrentNamespace(any()))
			.doReturn(false)
		contexts.current // create current context
		val newNamespace = "42"
		// when
		contexts.setCurrentNamespace(newNamespace)
		// then
		verify(config, never()).setCurrentNamespace(newNamespace)
	}

	@Test
	fun `#setCurrentNamespace(namespace) should return false if new namespace was not set`() {
		// given
		whenever(currentContext.setCurrentNamespace(any()))
			.doReturn(false)
		contexts.current // create current context
		// when
		val success = contexts.setCurrentNamespace("42")
		// then
		assertThat(success).isFalse()
	}

	@Test
	fun `#setCurrentNamespace(namespace) should not save client config if new namespace was not set`() {
		// given
		whenever(currentContext.setCurrentNamespace(any()))
			.doReturn(false)
		contexts.current // create current context
		// when
		contexts.setCurrentNamespace("42")
		// then
		verify(config, never()).save()
	}

	private fun createClientConfig(currentContext: NamedContext?, allContexts: List<NamedContext>): ClientConfig {
        return mock {
            on { contexts } doReturn allContexts
            on { this.currentContext } doReturn currentContext
            if (currentContext != null) {
                on { isCurrent(eq(currentContext)) } doReturn true
            }
        }
    }

	private class TestableContext(
		modelChange: IModelChangeObservable,
		factory: (IModelChangeObservable, NamedContext) -> IActiveContext<out HasMetadata, out KubernetesClient>,
		override val config: ClientConfig
	) : Contexts(modelChange, factory) {

		override val all: MutableList<IContext> = spy(super.all)

		public override fun refresh() {
			super.refresh()
		}
	}
}