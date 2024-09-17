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

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.utils.ConfigHelper
import com.redhat.devtools.intellij.kubernetes.CompletableFutureUtils.PLATFORM_EXECUTOR
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * An adapter to access [io.fabric8.kubernetes.client.Config].
 * It also saves the kube config [KubeConfigUtils] when it changes the client config.
 */
open class ClientConfig(
	private val client: Client,
	private val executor: Executor = PLATFORM_EXECUTOR,
	private val persistence: (io.fabric8.kubernetes.api.model.Config?, String?) -> Unit = KubeConfigUtils::persistKubeConfigIntoFile
) {

	open val currentContext: NamedContext?
		get() {
			return configuration.currentContext
		}

	open val allContexts: List<NamedContext>
		get() {
			return configuration.contexts ?: emptyList()
		}

	open val configuration: Config by lazy {
		client.configuration
	}

	val files: List<File>
		get() {
			return configuration.files
		}

	fun save(): CompletableFuture<Boolean> {
		return CompletableFuture.supplyAsync(
			{
				val toSave = mutableMapOf<File, io.fabric8.kubernetes.api.model.Config>()
				val withCurrentContext = configuration.fileWithCurrentContext
				if (withCurrentContext != null
					&& setCurrentContext(withCurrentContext.config)
				) {
					toSave[withCurrentContext.file] = withCurrentContext.config
				}
				val withCurrentNamespace = configuration.getFileWithContext(currentContext?.name)
				if (withCurrentNamespace != null
					&& setCurrentNamespace(withCurrentNamespace.config)
				) {
					toSave[withCurrentNamespace.file] = withCurrentNamespace.config
				}
				toSave.forEach {
					save(it.value, it.key)
				}
				toSave.isNotEmpty()
			},
			executor
		)
	}

	private fun save(kubeConfig: io.fabric8.kubernetes.api.model.Config?, file: File?) {
		if (kubeConfig != null
			&& file?.absolutePath != null) {
			logger<ClientConfig>().debug("Saving ${file.absolutePath}.")
			persistence.invoke(kubeConfig, file.absolutePath)
		}
	}

	private fun setCurrentNamespace(kubeConfig: io.fabric8.kubernetes.api.model.Config?): Boolean {
		val currentNamespace = currentContext?.context?.namespace ?: return false
		val context = KubeConfigUtils.getContext(kubeConfig, currentContext?.name)
		return if (context?.context != null
			&& context.context.namespace != currentNamespace) {
			context.context.namespace = currentNamespace
			true
		} else {
			false
		}
	}

	private fun setCurrentContext(kubeConfig: io.fabric8.kubernetes.api.model.Config?): Boolean {
		val currentContext = currentContext?.name ?: return false
		return if (
			kubeConfig != null
			&& currentContext != kubeConfig.currentContext) {
			kubeConfig.currentContext = currentContext
			true
		} else {
			false
		}
	}

	fun isCurrent(context: NamedContext): Boolean {
		return context == currentContext
	}

	fun isEqual(config: io.fabric8.kubernetes.api.model.Config): Boolean {
		return ConfigHelper.areEqual(config, configuration)
	}
}