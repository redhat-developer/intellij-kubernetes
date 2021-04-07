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

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.common.utils.ConfigWatcher
import io.fabric8.kubernetes.api.model.ContextBuilder
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.NamedContextBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigAware
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.apiConfig
import org.junit.Test

class ClientConfigTest {

	private val namedContext1 =
		createContext("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
		createContext("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
		createContext("ctx3", "namespace3", "cluster3", "user3")
	private val currentContext = namedContext2
	private val allContexts = listOf(namedContext1, namedContext2, namedContext3)
	private val config: Config = ClientMocks.config(currentContext, allContexts)
	private val client: ConfigAware<Config> = createClient(config)
	private val refreshOperation: () -> Unit = mock()
	private val clientConfig = spy(TestableClientConfig(refreshOperation, client))

	@Test
	fun `#currentContext should return config#currentContext`() {
		// given
		// when
		clientConfig.currentContext
		// then
		verify(config).currentContext
	}

	@Test
	fun `#contexts should return config#contexts`() {
		// given
		// when
		clientConfig.contexts
		// then
		verify(config).contexts
	}

	@Test
	fun `#currentContext should call #createClient once`() {
		// given
		// when
		clientConfig.currentContext
		clientConfig.currentContext
		// then
		verify(clientConfig, times(1)).createClient()
	}

	@Test
	fun `#contexts should call #createClient once`() {
		// given
		// when
		clientConfig.contexts
		clientConfig.contexts
		// then
		verify(clientConfig, times(1)).createClient()
	}

	@Test
	fun `#currentContext should call #initWatcher once`() {
		// given
		// when
		clientConfig.currentContext
		clientConfig.currentContext
		// then
		verify(clientConfig, times(1)).initWatcher()
	}

	@Test
	fun `#contexts should call #initWatcher once`() {
		// given
		// when
		clientConfig.contexts
		clientConfig.contexts
		// then
		verify(clientConfig, times(1)).initWatcher()
	}

	@Test
	fun `#isCurrent should return true if context is equal`() {
		// given
		// when
		val isCurrent = clientConfig.isCurrent(currentContext)
		// then
		assertThat(isCurrent).isTrue()
	}

	@Test
	fun `#isCurrent should return false if context isn't equal`() {
		// given
		// when
		val isCurrent = clientConfig.isCurrent(namedContext3)
		// then
		assertThat(isCurrent).isFalse()
	}

	@Test
	fun `refreshOperation should be called if currentContext name changed`() {
		// given
		val newConfig =
			apiConfig(namedContext3.name, allContexts)
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should be called if inexistent currentContext is replaced by new existent currentContext`() {
		// given
		val config: Config = ClientMocks.config(null, allContexts)
		val client: ConfigAware<Config> = createClient(config)
		val refreshOperation: () -> Unit = mock()
		val kubeConfig = spy(TestableClientConfig(refreshOperation, client))
		val newConfig =
			apiConfig(null, namedContext3, allContexts)
		// when
		kubeConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should be called if currentContext cluster changed`() {
		// given
		val newCurrentContext = createContext(
			currentContext.name,
			currentContext.context.namespace,
			"endor",
			currentContext.context.user
		)
		val newConfig = apiConfig(currentContext, newCurrentContext, allContexts)
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should be called if currentContext namespace changed`() {
		// given
		val newCurrentContext = createContext(
			currentContext.name,
			"rebel army",
			currentContext.context.cluster,
			currentContext.context.user
		)
		val newConfig = apiConfig(currentContext, newCurrentContext, allContexts)
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should be called if currentContext user changed`() {
		// given
		val newCurrentContext = createContext(
			currentContext.name,
			currentContext.context.namespace,
			currentContext.context.cluster,
			"luke skywalker"
		)
		val newConfig = apiConfig(currentContext, newCurrentContext, allContexts)
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should be called if allContexts changed`() {
		// given
		val newConfig =
			apiConfig(currentContext.name, listOf(mock(), *allContexts.toTypedArray()))
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should NOT be called if neither currentContext nor allContexts changed`() {
		// given
		val currentContext = createContext("name2",
			"namespace2",
			"cluster2",
			"user2")
		val allContexts = listOf(namedContext1, currentContext, namedContext3)
		val newCurrentContext = createContext(
			currentContext.name,
			currentContext.context.namespace,
			currentContext.context.cluster,
			currentContext.context.user
		)
		val clientConfig = createClientConfig(currentContext, allContexts, refreshOperation)
		val newConfig = apiConfig(currentContext, newCurrentContext, allContexts)
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, never()).invoke()
	}

	private fun createClientConfig(
		currentContext: NamedContext,
		allContexts: List<NamedContext>,
		refreshOperation: () -> Unit
	): TestableClientConfig {
		val config: Config = ClientMocks.config(currentContext, allContexts)
		val client: ConfigAware<Config> = createClient(config)
		return spy(TestableClientConfig(refreshOperation, client))
	}

	private fun createContext(name: String, namespace: String, cluster: String, user: String): NamedContext {
		val context = ContextBuilder()
			.withNamespace(namespace)
			.withCluster(cluster)
			.withUser(user)
			.build()
		return NamedContextBuilder()
			.withName(name)
			.withContext(context)
			.build()
	}


	private fun createClient(config: Config): ConfigAware<Config> {
		return mock {
			on { mock.configuration } doReturn config
		}
	}

	/**
	 * Returns a {@link io.fabric8.kubernetes.api.model.Config} where the given currentContext is replaced
	 * by the given newCurrentContext. If the given currentContext doesn't exist, the newCurrentContext is added.
	 */
	private fun apiConfig(
		currentContext: NamedContext?,
		newCurrentContext: NamedContext,
		allContexts: List<NamedContext>
	): io.fabric8.kubernetes.api.model.Config {
		val index = allContexts.indexOf(currentContext)
		val newAllContexts = allContexts.toMutableList()
		if (index >= 0) {
			newAllContexts[index] = newCurrentContext
		} else {
			newAllContexts.add(newCurrentContext)
		}
		return apiConfig(newCurrentContext.name, newAllContexts)
	}


	private class TestableClientConfig(
			refreshOperation: () -> Unit,
			private val client: ConfigAware<Config>
	) : ClientConfig(refreshOperation) {

		public override fun initWatcher() {
			// test fake, should not watch config file
		}

		public override fun createClient(): ConfigAware<Config> {
			return client
		}

		public override fun onConfigChange(watcher: ConfigWatcher, fileConfig: io.fabric8.kubernetes.api.model.Config) {
			super.onConfigChange(watcher, fileConfig)
		}
	}
}
