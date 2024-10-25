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
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Config
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
	private val clientKubeConfig: Config = ClientMocks.config(currentContext, allContexts)
	private val client: Client = createClient(clientKubeConfig)
	private val fileKubeConfig: io.fabric8.kubernetes.api.model.Config = apiConfig(currentContext.name, allContexts)
	private val kubeConfigAdapter: KubeConfigAdapter = kubeConfig(true, fileKubeConfig)
	private val clientConfig = spy(TestableClientConfig(client, kubeConfigAdapter))

	@Test
	fun `#currentContext should return config#currentContext`() {
		// given
		// when
		clientConfig.currentContext
		// then
		verify(clientKubeConfig).currentContext
	}

	@Test
	fun `#allContexts should return config#contexts`() {
		// given
		// when
		clientConfig.allContexts
		// then
		verify(clientKubeConfig).contexts
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
			.whenever(kubeConfigAdapter).exists()
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter, never()).save(any())
	}

	@Test
	fun `#save should NOT save if kubeConfig has same current context same namespace and same current context as client config`() {
		// given
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter, never()).save(any())
	}

	@Test
	fun `#save should save if kubeConfig has different current context as client config`() {
		// given
		clientKubeConfig.currentContext.name = namedContext3.name
		assertThat(fileKubeConfig.currentContext).isNotEqualTo(clientKubeConfig.currentContext.name)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save(any())
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
		fileKubeConfig.contexts = newAllContexts
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save(any())
	}

	@Test
	fun `#save should update current context in kube config if differs from current context in client config`() {
		// given
		val newCurrentContext = namedContext3
		doReturn(newCurrentContext)
			.whenever(clientKubeConfig).currentContext
		assertThat(KubeConfigUtils.getCurrentContext(fileKubeConfig))
			.isNotEqualTo(clientKubeConfig.currentContext)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save(argThat {
			this.currentContext == newCurrentContext.name
		})
	}

	@Test
	fun `#save should leave current namespace in old context untouched when updating current context in kube config`() {
		// given
		val newCurrentContext = namedContext3
		doReturn(newCurrentContext)
			.whenever(clientKubeConfig).currentContext
		assertThat(KubeConfigUtils.getCurrentContext(fileKubeConfig))
			.isNotEqualTo(clientKubeConfig.currentContext)
		val context = KubeConfigUtils.getCurrentContext(fileKubeConfig)
		val currentBeforeSave = context.name
		val namespaceBeforeSave = context.context.namespace
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save(argThat {
			val afterSave = fileKubeConfig.contexts.find {
				namedContext -> namedContext.name == currentBeforeSave }
			afterSave!!.context.namespace == namespaceBeforeSave
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
		doReturnCurrentContextAndAllContexts(newCurrentContext, newAllContexts, clientKubeConfig)
		assertThat(KubeConfigUtils.getCurrentContext(fileKubeConfig).context.namespace)
			.isNotEqualTo(clientKubeConfig.currentContext.context.namespace)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save(argThat {
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
		val existingContext = clientKubeConfig.contexts.find { it.name == currentContext }
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

	private fun createClient(config: Config): Client {
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

	private class TestableClientConfig(client: Client, override val kubeConfig: KubeConfigAdapter)
		: ClientConfig(client, { it.run() })
}
