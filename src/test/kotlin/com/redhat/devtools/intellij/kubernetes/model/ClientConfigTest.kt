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
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.utils.ConfigWatcher
import io.fabric8.kubernetes.api.model.ContextBuilder
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.NamedContextBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigAware
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import io.fabric8.kubernetes.api.model.ConfigBuilder
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import org.junit.Test
import org.mockito.ArgumentCaptor

class ClientConfigTest {

	private val namedContext1 =
		context("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
		context("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
		context("ctx3", "namespace3", "cluster3", "user3")
	private val currentContext = namedContext2
	private val allContexts = listOf(namedContext1, namedContext2, namedContext3)
	private val config: Config = ClientMocks.config(currentContext, allContexts)
	private val client: ConfigAware<Config> = createClient(config)
	private val refreshOperation: () -> Unit = mock()
	private val apiConfig: io.fabric8.kubernetes.api.model.Config = apiConfig(currentContext.name, allContexts)
	private val kubeConfig: KubeConfig = kubeConfig(true, apiConfig)
	private val clientConfig = spy(TestableClientConfig(refreshOperation, client, kubeConfig))

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
	fun `refreshOperation should be called if nonexistent currentContext is replaced by new existent currentContext`() {
		// given
		val config: Config = ClientMocks.config(null, allContexts)
		val client: ConfigAware<Config> = createClient(config)
		val refreshOperation: () -> Unit = mock()
		val kubeConfig = spy(TestableClientConfig(refreshOperation, client, kubeConfig))
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
		val newCurrentContext = context(
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
		val newCurrentContext = context(
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
		val newCurrentContext = context(
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
		val newConfig = apiConfig(currentContext.name, listOf(mock(), *allContexts.toTypedArray()))
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should NOT be called if neither currentContext nor allContexts changed`() {
		// given
		val currentContext = context("name2",
			"namespace2",
			"cluster2",
			"user2")
		val allContexts = listOf(namedContext1, currentContext, namedContext3)
		val newCurrentContext = context(
			currentContext.name,
			currentContext.context.namespace,
			currentContext.context.cluster,
			currentContext.context.user
		)
		val clientConfig = clientConfig(currentContext, allContexts, refreshOperation, kubeConfig)
		val newConfig = apiConfig(currentContext, newCurrentContext, allContexts)
		// when
		clientConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, never()).invoke()
	}

	@Test
	fun `#save should NOT save if kubeConfig doesnt exist`() {
		// given
		doReturn(false)
			.whenever(kubeConfig).exists()
		// when
		clientConfig.save()
		// then
		verify(kubeConfig, never()).save(any())
	}

	@Test
	fun `#save should NOT save if kubeConfig has same current context same namespace and same current context as client config`() {
		// given
		// when
		clientConfig.save()
		// then
		verify(kubeConfig, never()).save(any())
	}

	@Test
	fun `#save should save if kubeConfig has different current context as client config`() {
		// given
		assertThat(currentContext).isNotEqualTo(namedContext3)
		apiConfig.currentContext = namedContext3.name
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(any())
	}

	@Test
	fun `#save should save if kubeConfig has same current context but different current namespace as client config`() {
		// given
		val newCurrentContext = context(
			currentContext.name,
			"R2-D2",
			currentContext.context.cluster,
			currentContext.context.user)
		val newAllContexts = mutableListOf(*allContexts.toTypedArray())
		newAllContexts.removeIf { it.name == currentContext.name }
		newAllContexts.add(newCurrentContext)
		apiConfig.contexts = newAllContexts
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(any())
	}

	@Test
	fun `#save should update current context in kube config if differs from current context in client config`() {
		// given
		val newCurrentContext = namedContext3
		doReturn(newCurrentContext)
			.whenever(config).currentContext
		assertThat(KubeConfigUtils.getCurrentContext(apiConfig))
			.isNotEqualTo(config.currentContext)
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(argThat {
			this.currentContext == newCurrentContext.name
		})
	}

	@Test
	fun `#save should update current namespace in kube config if only differs from current name in client config but not in current context`() {
		// given
		val newCurrentContext = context(currentContext.name,
		"RD-2D",
		currentContext.context.cluster,
		currentContext.context.user)
		val newAllContexts = replaceCurrentContext(newCurrentContext, currentContext.name, allContexts)
		ClientMocks.changeConfig(newCurrentContext, newAllContexts, config)
		assertThat(KubeConfigUtils.getCurrentContext(apiConfig).context.namespace)
			.isNotEqualTo(config.currentContext.context.namespace)
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(argThat {
			this.currentContext == this@ClientConfigTest.currentContext.name
			KubeConfigUtils.getCurrentContext(this).context.namespace == newCurrentContext.context.namespace
		})
	}

	private fun replaceCurrentContext(
		newContext: NamedContext,
		currentContext: String,
		allContexts: List<NamedContext>
	): List<NamedContext> {
		val newAllContexts = mutableListOf(*allContexts.toTypedArray())
		val existingContext = config.contexts.find { it.name == currentContext }
		newAllContexts.remove(existingContext)
		newAllContexts.add(newContext)
		return newAllContexts
	}

	private fun clientConfig(
		currentContext: NamedContext,
		allContexts: List<NamedContext>,
		refreshOperation: () -> Unit,
		kubeConfig: KubeConfig
	): TestableClientConfig {
		val config: Config = ClientMocks.config(currentContext, allContexts)
		val client: ConfigAware<Config> = createClient(config)
		return spy(TestableClientConfig(refreshOperation, client, kubeConfig))
	}

	private fun context(name: String, namespace: String, cluster: String, user: String): NamedContext {
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

	private fun kubeConfig(exists: Boolean, config: io.fabric8.kubernetes.api.model.Config): KubeConfig {
		return mock {
			on { mock.exists() } doReturn exists
			on { mock.get() } doReturn config
		}
	}

	private fun apiConfig(currentContext: String, allContexts: List<NamedContext>): io.fabric8.kubernetes.api.model.Config {
		return ConfigBuilder()
			.withCurrentContext(currentContext)
			.withContexts(allContexts)
			.build()
	}

	private class TestableClientConfig(
		refreshOperation: () -> Unit,
		private val client: ConfigAware<Config>,
		override val kubeConfig: KubeConfig
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
