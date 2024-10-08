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
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * An adapter for [io.fabric8.kubernetes.client.Config].
 * It can save it's values to kube config files that were used when the client config was created.
 *
 * @param config the (client) config that's adapted
 * @param createKubeConfigAdapter a factory that creates [KubeConfigAdapter]s. Defaults to instantiating such a class
 * @param getFileWithCurrentContextName a lambda that returns the file with the `current-context` property. Defaults to [ConfigUtils.getFileWithCurrentContext]
 * @param getFileWithCurrentContext a lambda that returns the file with the [NamedContext] whose name is set in `current-context`
 * @param executor the thread pool to execute ex. saving files. Defaults to [PLATFORM_EXECUTOR]
 *
 * @see ConfigUtils.getFileWithCurrentContext
 * @see io.fabric8.kubernetes.client.Config.getFile
 */
open class ClientConfig(
	private val config: Config,
	private val createKubeConfigAdapter: (file: File, config: io.fabric8.kubernetes.api.model.Config?) -> KubeConfigAdapter
		= { file, kubeConfig -> KubeConfigAdapter(file, kubeConfig) },
	private val getFileWithCurrentContextName: () -> Pair<File, io.fabric8.kubernetes.api.model.Config>?
		= { ConfigUtils.getFileWithCurrentContext() },
	private val getFileWithCurrentContext: (config: Config) -> File?
		= { config -> config.file },
	private val executor: Executor = PLATFORM_EXECUTOR)
{

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

	/**
	 * Returns `true` if the given config is equal in
	 *   * current context
	 *   * (existing) contexts
	 *   * current cluster
	 *   * current auth info
	 *
	 * @param config the [Config] to compare the adapted config in this class to.
	 * @return true if the given config is equal to the one that this class adapts
	 */
	fun isEqualConfig(config: Config): Boolean {
		return ConfigHelper.areEqualCurrentContext(config, this.config)
				&& ConfigHelper.areEqualContexts(config, this.config)
				&& ConfigHelper.areEqualCluster(config, this.config)
				&& ConfigHelper.areEqualAuthInfo(config, this.config)
	}

	/**
	 * Saves the values in the config (that this class is adapting) to the files involved.
	 * No file(s) are saved if neither `current-context` nor `namespace` in the current context were changed.
	 *
	 * @return a [CompletableFuture] true if values were saved to file(s). False if no files were saved.
	 */
	fun save(): CompletableFuture<Boolean> {
		return CompletableFuture.supplyAsync(
			{
				val modified = HashSet<KubeConfigAdapter>()

				setCurrentContext(modified)
				setCurrentNamespace(modified)

				modified.forEach { kubeConfig ->
					kubeConfig.save()
				}
				modified.isNotEmpty() // return true if files were saved
			},
			executor
		)
	}

	private fun setCurrentContext(modified: HashSet<KubeConfigAdapter>) {
		val fileWithCurrentContext = getFileWithCurrentContextName.invoke()
		if (fileWithCurrentContext != null) {
			val kubeConfig = createKubeConfigAdapter(fileWithCurrentContext.first, fileWithCurrentContext.second)
			if (kubeConfig.setCurrentContext(config.currentContext?.name)) {
				modified.add(kubeConfig)
			}
		}
	}

	private fun setCurrentNamespace(modified: HashSet<KubeConfigAdapter>) {
		val fileWithCurrentNamespace = getFileWithCurrentContext.invoke(config) ?: return
		val kubeConfig = createKubeConfigAdapter(fileWithCurrentNamespace, null)
		if (kubeConfig.setCurrentNamespace(config.currentContext?.name, config.namespace)) {
			modified.add(kubeConfig)
		}
	}

	/**
	 * Returns `true` if the given context is equal to the current context in the [Config] that this class is adapting.
	 * Returns `false` otherwise.
	 * Both [NamedContext]s are compared in
	 * <ul>
	 * 	<li>name</li>
	 * 	<li>cluster</li>
	 * 	<li>user</li>
	 * 	<li>current namespace</li>
	 * </ul>
	 *
	 * @param context the context to compare to the one in the config that this class adapts.
	 */
	fun isCurrent(context: NamedContext): Boolean {
		return ConfigHelper.areEqualContext(context, currentContext)
	}
}