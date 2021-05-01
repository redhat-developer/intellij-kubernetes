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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl
import com.redhat.devtools.intellij.kubernetes.model.util.createContext

class CustomResourceRawOperation(
	private val client: KubernetesClient,
	definition: CustomResourceDefinition,
	private val context: CustomResourceDefinitionContext = createContext(definition)
) {

	fun get(): RawCustomResourceOperationsImpl {
		return client.customResource(context)
	}
}
