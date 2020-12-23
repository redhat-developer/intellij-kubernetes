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
package com.redhat.devtools.intellij.kubernetes.model.context

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftNotAvailableException
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable

fun create(
	observable: IModelChangeObservable,
	context: NamedContext
): IActiveContext<out HasMetadata, out KubernetesClient> {
	val config = Config.autoConfigure(context.name)
	val k8Client = DefaultKubernetesClient(config)
	try {
		val osClient = k8Client.adapt(NamespacedOpenShiftClient::class.java)
		return OpenShiftContext(
			observable,
			osClient,
			context
		)
	} catch (e: RuntimeException) {
		when (e) {
			is KubernetesClientException,
			is OpenShiftNotAvailableException ->
				return KubernetesContext(
					observable,
					k8Client,
					context
				)
			else -> throw e
		}
	}
}