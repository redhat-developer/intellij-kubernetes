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
package com.redhat.devtools.intellij.kubernetes.model.client

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.doReturnCurrentContextAndAllContexts
import io.fabric8.kubernetes.api.model.ConfigBuilder
import io.fabric8.kubernetes.api.model.ContextBuilder
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.NamedContextBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigAware
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClientConfigTest {

	private val namedContext1 =
		context("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
		context("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
		context("ctx3", "namespace3", "cluster3", "user3")
	private val currentContext = namedContext2
	private val allContexts = listOf(namedContext1, namedContext2, namedContext3)
	private val f8clientConfig: Config = ClientMocks.config(currentContext, allContexts)
	private val client: ConfigAware<Config> = createClient(f8clientConfig)
	private val f8kubeConfig: io.fabric8.kubernetes.api.model.Config = apiConfig(currentContext.name, allContexts)
	private val kubeConfig: KubeConfigAdapter = kubeConfig(true, f8kubeConfig)
	private val clientConfig = spy(TestableClientConfig(client, kubeConfig))

	@Test
	fun `#currentContext should return config#currentContext`() {
		// given
		// when
		clientConfig.currentContext
		// then
		verify(f8clientConfig).currentContext
	}

	@Test
	fun `#allContexts should return config#contexts`() {
		// given
		// when
		clientConfig.allContexts
		// then
		verify(f8clientConfig).contexts
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
		f8clientConfig.currentContext.name = namedContext3.name
		assertThat(f8kubeConfig.currentContext).isNotEqualTo(f8clientConfig.currentContext.name)
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(any())
	}

	@Test
	fun `#save should save if kubeConfig has same current context but current namespace that differs from client config`() {
		// given
		val newCurrentContext = context(
			currentContext.name,
			"R2-D2",
			currentContext.context.cluster,
			currentContext.context.user)
		val newAllContexts = mutableListOf(*allContexts.toTypedArray())
		newAllContexts.removeIf { it.name == currentContext.name }
		newAllContexts.add(newCurrentContext)
		f8kubeConfig.contexts = newAllContexts
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
			.whenever(f8clientConfig).currentContext
		assertThat(KubeConfigUtils.getCurrentContext(f8kubeConfig))
			.isNotEqualTo(f8clientConfig.currentContext)
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(argThat {
			this.currentContext == newCurrentContext.name
		})
	}

	@Test
	fun `#save should update current namespace in kube config if only differs from current in client config but not in current context`() {
		// given
		val newCurrentContext = context(currentContext.name,
		"RD-2D",
		currentContext.context.cluster,
		currentContext.context.user)
		val newAllContexts = replaceCurrentContext(newCurrentContext, currentContext.name, allContexts)
		doReturnCurrentContextAndAllContexts(newCurrentContext, newAllContexts, f8clientConfig)
		assertThat(KubeConfigUtils.getCurrentContext(f8kubeConfig).context.namespace)
			.isNotEqualTo(f8clientConfig.currentContext.context.namespace)
		// when
		clientConfig.save()
		// then
		verify(kubeConfig).save(argThat {
			this.currentContext == this@ClientConfigTest.currentContext.name
					&& KubeConfigUtils.getCurrentContext(this).context.namespace == newCurrentContext.context.namespace
		})
	}

	private fun replaceCurrentContext(
		newContext: NamedContext,
		currentContext: String,
		allContexts: List<NamedContext>
	): List<NamedContext> {
		val newAllContexts = mutableListOf(*allContexts.toTypedArray())
		val existingContext = f8clientConfig.contexts.find { it.name == currentContext }
		newAllContexts.remove(existingContext)
		newAllContexts.add(newContext)
		return newAllContexts
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
			on { configuration } doReturn config
		}
	}

		private fun kubeConfig(exists: Boolean, config: io.fabric8.kubernetes.api.model.Config): com.redhat.devtools.intellij.kubernetes.model.client.KubeConfigAdapter {
		return mock {
			on { exists() } doReturn exists
			on { load() } doReturn config
		}
	}

	private fun apiConfig(currentContext: String, allContexts: List<NamedContext>): io.fabric8.kubernetes.api.model.Config {
		return ConfigBuilder()
			.withCurrentContext(currentContext)
			.withContexts(allContexts)
			.build()
	}

	private class TestableClientConfig(client: ConfigAware<Config>, override val kubeConfig: KubeConfigAdapter) : ClientConfig(client) {
		override fun runAsync(runnable: () -> Unit) {
			// dont use jetbrains application threadpool
			runnable.invoke()
		}
	}
}
