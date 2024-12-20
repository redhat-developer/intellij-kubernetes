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

import com.redhat.devtools.intellij.common.utils.ConfigHelper
import com.redhat.devtools.intellij.kubernetes.CompletableFutureUtils.PLATFORM_EXECUTOR
import com.redhat.devtools.intellij.kubernetes.model.util.ConfigUtils
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * An adapter to access [io.fabric8.kubernetes.client.Config].
 * It also saves the kube config [KubeConfigUtils] when it changes the client config.
 */
open class ClientConfig(private val config: Config, private val executor: Executor = PLATFORM_EXECUTOR) {

	open var currentContext: NamedContext?
		get() {
			return config.currentContext
		}
		set(context) {
			config.currentContext = context
		}

	open val allContexts: List<NamedContext>
		get() {
			return config.contexts ?: emptyList()
		}

	fun isEqualConfig(config: Config): Boolean {
		return ConfigHelper.areEqualCurrentContext(config, this.config)
				&& ConfigHelper.areEqualContexts(config, this.config)
				&& ConfigHelper.areEqualCluster(config, this.config)
				&& ConfigHelper.areEqualAuthInfo(config, this.config)
	}

	fun save(): CompletableFuture<Boolean> {
		return CompletableFuture.supplyAsync(
			{
				val modified = HashSet<KubeConfigAdapter>()

				val currentContextPair = ConfigUtils.getFileWithCurrentContext()
				if (currentContextPair != null) {
					val kubeConfig = KubeConfigAdapter(currentContextPair.first, currentContextPair.second)
					if (kubeConfig.setCurrentContext(config.currentContext?.name)) {
						modified.add(kubeConfig)
					}
				}
				val kubeConfig = KubeConfigAdapter(config.file)
				if (kubeConfig.setCurrentNamespace(config.currentContext?.name, config.namespace)) {
					modified.add(kubeConfig)
				}
				modified.forEach { kubeConfig ->
					kubeConfig.save()
				}
				modified.isNotEmpty()
			},
			executor
		)
	}

	fun isCurrent(context: NamedContext): Boolean {
		return context == currentContext
	}
}