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

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.common.utils.ConfigWatcher
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigAware
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks
import org.junit.Test

class KubeConfigTest {

	private val namedContext1 =
			ClientMocks.namedContext("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
			ClientMocks.namedContext("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
			ClientMocks.namedContext("ctx3", "namespace3", "cluster3", "user3")
	private val currentContext = namedContext2
	private val allContexts = listOf(namedContext1, namedContext2, namedContext3)
	private val config: Config = createConfig(currentContext, allContexts)
	private val client: ConfigAware<Config> = createClient(config)
	private val refreshOperation: () -> Unit = mock()
	private val kubeConfig = spy(TestableKubeConfig(refreshOperation, client))

	@Test
	fun `#currentContext should return config#currentContext`() {
		// given
		// when
		kubeConfig.currentContext
		// then
		verify(config).currentContext
	}

	@Test
	fun `#contexts should return config#contexts`() {
		// given
		// when
		kubeConfig.contexts
		// then
		verify(config).contexts
	}

	@Test
	fun `#currentContext should call #createClient once`() {
		// given
		// when
		kubeConfig.currentContext
		kubeConfig.currentContext
		// then
		verify(kubeConfig, times(1)).createClient()
	}

	@Test
	fun `#contexts should call #createClient once`() {
		// given
		// when
		kubeConfig.contexts
		kubeConfig.contexts
		// then
		verify(kubeConfig, times(1)).createClient()
	}

	@Test
	fun `#currentContext should call #initWatcher once`() {
		// given
		// when
		kubeConfig.currentContext
		kubeConfig.currentContext
		// then
		verify(kubeConfig, times(1)).initWatcher()
	}

	@Test
	fun `#contexts should call #initWatcher once`() {
		// given
		// when
		kubeConfig.contexts
		kubeConfig.contexts
		// then
		verify(kubeConfig, times(1)).initWatcher()
	}

	@Test
	fun `#isCurrent should return true if context is equal`() {
		// given
		// when
		val isCurrent = kubeConfig.isCurrent(currentContext)
		// then
		assertThat(isCurrent).isTrue()
	}

	@Test
	fun `#isCurrent should return false if context isn't equal`() {
		// given
		// when
		val isCurrent = kubeConfig.isCurrent(namedContext3)
		// then
		assertThat(isCurrent).isFalse()
	}

	@Test
	fun `refreshOperation should be called if currentContext changed`() {
		// given
		val newConfig = createApiConfig(namedContext3.name, allContexts)
		// when
		kubeConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should be called if allContexts changed`() {
		// given
		val newConfig = createApiConfig(currentContext.name, listOf(mock(), namedContext1, namedContext2, namedContext3))
		// when
		kubeConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, times(1)).invoke()
	}

	@Test
	fun `refreshOperation should NOT be called if neither currentContext nor allContexts changed`() {
		// given
		val newConfig = createApiConfig(currentContext.name, allContexts)
		// when
		kubeConfig.onConfigChange(mock(), newConfig)
		// then
		verify(refreshOperation, never()).invoke()
	}

	private fun createClient(config: Config): ConfigAware<Config> {
		return mock() {
			on { configuration } doReturn config
		}
	}

	private fun createConfig(currentContext: NamedContext?, contexts: List<NamedContext>): Config {
		return mock {
			on { mock.currentContext } doReturn currentContext
			on { mock.contexts } doReturn contexts
		}
	}

	private fun createApiConfig(currentContext: String, contexts: List<NamedContext>): io.fabric8.kubernetes.api.model.Config {
		return mock {
			on { mock.currentContext } doReturn currentContext
			on { mock.contexts } doReturn contexts
		}
	}

	private class TestableKubeConfig(
			refreshOperation: () -> Unit,
			private val client: ConfigAware<Config>
	) : KubeConfig(refreshOperation) {

		public override fun initWatcher() {
		}

		public override fun createClient(): ConfigAware<Config> {
			return client
		}

		public override fun onConfigChange(watcher: ConfigWatcher, config: io.fabric8.kubernetes.api.model.Config) {
			super.onConfigChange(watcher, config)
		}
	}
}