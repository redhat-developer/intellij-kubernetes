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
import io.fabric8.kubernetes.api.model.Context
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * An adapter to access [io.fabric8.kubernetes.client.Config].
 * It also saves the kube config [KubeConfigUtils] when it changes the client config.
 */
open class ClientConfig(private val client: Client, private val executor: Executor = PLATFORM_EXECUTOR) {

	open var currentContext: NamedContext?
		get() {
			return configuration.currentContext
		}
		set(context) {
			configuration.currentContext = context
		}

	open val allContexts: List<NamedContext>
		get() {
			return configuration.contexts ?: emptyList()
		}

	open val configuration: Config by lazy {
		client.configuration
	}

	protected open val kubeConfig: KubeConfigAdapter by lazy {
		KubeConfigAdapter()
	}

	fun save(): CompletableFuture<Boolean> {
		return CompletableFuture.supplyAsync(
			{
				if (!kubeConfig.exists()) {
					return@supplyAsync false
				}
				val fromFile = kubeConfig.load() ?: return@supplyAsync false
				if (setCurrentContext(
						currentContext,
						KubeConfigUtils.getCurrentContext(fromFile),
						fromFile
					).or( // no short-circuit
						setCurrentNamespace(
							currentContext?.context,
							KubeConfigUtils.getCurrentContext(fromFile)?.context
						)
					)
				) {
					kubeConfig.save(fromFile)
					return@supplyAsync true
				} else {
					return@supplyAsync false
				}
			},
			executor
		)
	}

	private fun setCurrentContext(
		currentContext: NamedContext?,
		kubeConfigCurrentContext: NamedContext?,
		kubeConfig: io.fabric8.kubernetes.api.model.Config
	): Boolean {
		return if (currentContext != null
			&& !ConfigHelper.areEqual(currentContext, kubeConfigCurrentContext)
		) {
			kubeConfig.currentContext = currentContext.name
			true
		} else {
			false
		}
	}

	/**
	 * Sets the namespace in the given source [Context] to the given target [Context].
	 * Does nothing if the target config has no current context
	 * or if the source config has no current context
	 * or if setting it would not change it.
	 *
	 * @param source Context whose namespace should be copied
	 * @param target Context whose namespace should be overriden
	 * @return
	 */
	private fun setCurrentNamespace(
		source: Context?,
		target: Context?
	): Boolean {
		val sourceNamespace = source?.namespace ?: return false
		val targetNamespace = target?.namespace
		return if (target != null
			&& sourceNamespace != targetNamespace
		) {
			target.namespace = source.namespace
			true
		} else {
			false
		}
	}

	fun isCurrent(context: NamedContext): Boolean {
		return context == currentContext
	}
}