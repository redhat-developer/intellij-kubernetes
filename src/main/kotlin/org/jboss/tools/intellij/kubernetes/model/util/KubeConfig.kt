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
package org.jboss.tools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.DefaultKubernetesClient

class KubeConfig {

	private val client: DefaultKubernetesClient by lazy {
		DefaultKubernetesClient()
	}

	private val config: io.fabric8.kubernetes.client.Config?
		get() {
			return client.configuration
		}

	val currentContext: NamedContext? by lazy {
		config?.currentContext ?: null
	}

	val contexts: List<NamedContext>
		get() {
			return config?.contexts ?: emptyList()
		}

	fun isCurrent(context: NamedContext): Boolean {
		return context == currentContext
	}
}