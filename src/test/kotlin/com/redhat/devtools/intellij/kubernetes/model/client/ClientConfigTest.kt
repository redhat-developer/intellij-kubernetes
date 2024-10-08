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

import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.config
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import io.fabric8.kubernetes.api.model.ConfigBuilder
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class ClientConfigTest {

	private val namedContext1 =
		namedContext("ctx1", "namespace1", "cluster1", "user1")
	private val namedContext2 =
		namedContext("ctx2", "namespace2", "cluster2", "user2")
	private val namedContext3 =
		namedContext("ctx3", "namespace3", "cluster3", "user3")
	private val namedContext4 =
		namedContext("ctx4", "namespace4", "cluster4", "user4")
	private val currentContext = namedContext2
	private val allContexts = listOf(namedContext1, namedContext2, namedContext3)
	private val kubeConfigFile: File = mock()
	private val kubeConfig: io.fabric8.kubernetes.api.model.Config = apiConfig(currentContext.name, allContexts)
	private val kubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
		false, // setCurrentContext changed the current context name
		false) // setCurrentNamespace didnt change the current namespace
	private val config: Config = config(currentContext, allContexts)
	private val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter = { _, _ -> kubeConfigAdapter }
	private val clientConfig = clientConfig(
        config,
		createKubeConfigAdapter,
		{ Pair(kubeConfigFile, kubeConfig) },
		{ _ -> kubeConfigFile }
    )

	@Test
	fun `#currentContext should return config#currentContext`() {
		// given
		// when
		clientConfig.currentContext
		// then
		verify(config).currentContext
	}

	@Test
	fun `#allContexts should return config#contexts`() {
		// given
		// when
		clientConfig.allContexts
		// then
		verify(config).contexts
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
	fun `#isEqualConfig should return true if context is equal`() {
		// given
		// when
		val isEqualConfig = clientConfig.isEqualConfig(config)
		// then
		assertThat(isEqualConfig).isTrue()
	}

	@Test
	fun `#isEqualConfig should return false if context has different current context`() {
		// given
		val config = config(namedContext4, allContexts)
		// when
		val isEqualConfig = clientConfig.isEqualConfig(config)
		// then
		assertThat(isEqualConfig).isFalse()
	}

	@Test
	fun `#isEqualConfig should return false if context has different cluster`() {
		// given
		val config = config(currentContext, allContexts, "https://deathstar.com")
		// when
		val isEqualConfig = clientConfig.isEqualConfig(config)
		// then
		assertThat(isEqualConfig).isFalse()
	}

	@Test
	fun `#isEqualConfig should return false if context has different token`() {
		// given
		val config = config(currentContext, allContexts)
		doReturn("the force")
			.whenever(config).autoOAuthToken
		// when
		val isEqualConfig = clientConfig.isEqualConfig(config)
		// then
		assertThat(isEqualConfig).isFalse()
	}

	@Test
	fun `#isEqualConfig should return false if context has additional existing context`() {
		// given
		val allContexts = listOf(namedContext1, namedContext2, namedContext3, namedContext4)
		val config = config(currentContext, allContexts)
		// when
		val isEqualConfig = clientConfig.isEqualConfig(config)
		// then
		assertThat(isEqualConfig).isFalse()
	}

	@Test
	fun `#save should NOT save if kubeConfig has same current context same namespace and same current context as client config`() {
		// given
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter, never()).save()
	}

	@Test
	fun `#save should save if kubeConfig has different current context as client config`() {
		// given
		val kubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			true, // setCurrentContext changed the current context name
			false) // setCurrentNamespace didnt change the current namespace
		val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter = { _, _ -> kubeConfigAdapter }
		val clientConfig = clientConfig(
			config,
			createKubeConfigAdapter,
			{ Pair(kubeConfigFile, kubeConfig) },
			{ _ -> kubeConfigFile }
		)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save()
	}

	@Test
	fun `#save should NOT save if kubeConfig has different current context as client config but file is null`() {
		// given
		val kubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			true, // setCurrentContext changed the current context name
			false) // setCurrentNamespace didnt change the current namespace
		val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter = { _, _ -> kubeConfigAdapter }
		val clientConfig = clientConfig(
			config,
			createKubeConfigAdapter,
			{ null }, // no file with current context name was found
			{ _ -> kubeConfigFile }
		)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter, never()).save()
	}

	@Test
	fun `#save should save if kubeConfig has same current context but current namespace that differs from client config`() {
		// given
		val kubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			false, // setCurrentContext didnt change the current context name
			true // setCurrentNamespace changed the current namespace
		)
		val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter =
			{ _, _ -> kubeConfigAdapter }
		val clientConfig = clientConfig(
			config,
			createKubeConfigAdapter,
			{ Pair(kubeConfigFile, kubeConfig) },
			{ _ -> kubeConfigFile }
		)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter).save()
	}

	@Test
	fun `#save should NOT save if kubeConfig has same current context, current namespace that differs from client config but file is null`() {
		// given
		val kubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			false, // setCurrentContext didnt change the current context name
			true // setCurrentNamespace changed the current namespace
		)
		val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter =
			{ _, _ -> kubeConfigAdapter }
		val clientConfig = clientConfig(
			config,
			createKubeConfigAdapter,
			{ Pair(kubeConfigFile, kubeConfig) },
			{ _ -> null }
		)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter, never()).save()
	}

	@Test
	fun `#save should save only 1 file if current context name and current namespace are changed but in same file`() {
		// given
		val kubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			true, // setCurrentContext changed the current context name
			true // setCurrentNamespace changed the current namespace
		)
		val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter =
			{ _, _ -> kubeConfigAdapter }
		val clientConfig = clientConfig(
			config,
			createKubeConfigAdapter,
			{ Pair(kubeConfigFile, kubeConfig) },
			{ _ -> kubeConfigFile }
		)
		// when
		clientConfig.save().join()
		// then
		verify(kubeConfigAdapter, times(1)).save()
	}

	@Test
	fun `#save should save 2 file if current context name and current namespace are changed and in different files`() {
		// given
		val currentContextFile = mock<File>()
		val currentContextKubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			true, // setCurrentContext changed the current context name
			false // // setCurrentNamespace didnt change the current namespace
		)
		val currentNamespaceFile = mock<File>()
		val currentNamespaceKubeConfigAdapter: KubeConfigAdapter = kubeConfigAdapter(true, kubeConfig,
			false, // setCurrentContext didnt change the current context name
			true // setCurrentNamespace changed the current namespace
		)
		val createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter =
			{ file, _ ->
				if (file == currentContextFile) {
					currentContextKubeConfigAdapter
				} else {
					currentNamespaceKubeConfigAdapter
				}
			}
		val clientConfig = clientConfig(
			config,
			createKubeConfigAdapter,
			{ Pair(currentContextFile, kubeConfig) },
			{ _ -> currentNamespaceFile }
		)
		// when
		clientConfig.save().join()
		// then
		verify(currentContextKubeConfigAdapter, times(1)).save()
		verify(currentNamespaceKubeConfigAdapter, times(1)).save()
	}

	private fun kubeConfigAdapter(
		exists: Boolean,
		config: io.fabric8.kubernetes.api.model.Config,
		setCurrentContext: Boolean,
		setCurrentNamespace: Boolean,
	): KubeConfigAdapter {
		return mock<OverridableKubeConfigAdapter> {
			on { exists() } doReturn exists
			on { load() } doReturn config
			on { setCurrentContext(anyOrNull()) } doReturn setCurrentContext
			on { setCurrentNamespace(anyOrNull(), anyOrNull()) } doReturn setCurrentNamespace
		}
	}

	class OverridableKubeConfigAdapter(file: File): KubeConfigAdapter(file) {
		public override fun load(): io.fabric8.kubernetes.api.model.Config? {
			return super.load()
		}

		public override fun exists(): Boolean {
			return super.exists()
		}
	}

	private fun apiConfig(currentContext: String, allContexts: List<NamedContext>): io.fabric8.kubernetes.api.model.Config {
		return ConfigBuilder()
			.withCurrentContext(currentContext)
			.withContexts(allContexts)
			.build()
	}

	private fun clientConfig(
		config: Config,
		createKubeConfigAdapter: (file: File, kubeConfig: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter,
		getFileWithCurrentContextName: () -> Pair<File, io.fabric8.kubernetes.api.model.Config>?,
		getFileWithCurrentContext: (config: Config) -> File?,
	) : ClientConfig {
		return ClientConfig(
			config,
			createKubeConfigAdapter,
			getFileWithCurrentContextName,
			getFileWithCurrentContext,
			{ it.run() }
		)
	}
}
