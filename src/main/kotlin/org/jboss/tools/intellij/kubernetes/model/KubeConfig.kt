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

import com.redhat.devtools.intellij.common.utils.ConfigHelper
import com.redhat.devtools.intellij.common.utils.ConfigWatcher
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigAware
import java.nio.file.Paths
import java.util.concurrent.Executors

open class KubeConfig(private val refreshOperation: () -> Unit) {

	private val executors = Executors.newFixedThreadPool(1)
	private var client: ConfigAware<Config>? = null

	open val currentContext: NamedContext?
		get() {
			return config?.currentContext
		}

	open val contexts: List<NamedContext>
		get() {
			return config?.contexts ?: emptyList()
		}

	private val config: Config?
		get() {
			if (client == null) {
				initWatcher()
				this.client = createClient()
			}
			return client?.configuration
		}

	fun isCurrent(context: NamedContext): Boolean {
		return context == currentContext
	}

	protected open fun initWatcher() {
		val path = Paths.get(ConfigHelper.getKubeConfigPath())
		executors.submit(ConfigWatcher(path, ::onConfigChange))
	}

	protected open fun createClient(): ConfigAware<Config> {
		return DefaultKubernetesClient()
	}

	protected open fun onConfigChange(watcher: ConfigWatcher, config: io.fabric8.kubernetes.api.model.Config) {
		val oldCurrentContext = currentContext
		val newCurrentContext = KubeConfigUtils.getCurrentContext(config)
		val currentContextChanged = (oldCurrentContext != newCurrentContext)
		val allContextsChanged = (this.config?.contexts != config.contexts)
		if (currentContextChanged
				|| allContextsChanged) {
			this.client = createClient()
			refreshOperation.invoke()
		}
	}
}