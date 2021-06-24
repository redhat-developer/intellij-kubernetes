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
package com.redhat.devtools.intellij.kubernetes.model

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftNotAvailableException

fun createClients(
	context: String?
): Clients<out KubernetesClient> {
	val config = Config.autoConfigure(context)
	val k8Client = DefaultKubernetesClient(config)
	return try {
		val osClient = k8Client.adapt(NamespacedOpenShiftClient::class.java)
		Clients(osClient)
	} catch (e: RuntimeException) {
		when (e) {
			is KubernetesClientException,
			is OpenShiftNotAvailableException ->
				Clients(k8Client)
			else -> throw e
		}
	}
}

fun createClients(config: ClientConfig): Clients<out KubernetesClient>? {
	val cluster =  config.currentContext?.context?.cluster
	return createClients(cluster)
}
