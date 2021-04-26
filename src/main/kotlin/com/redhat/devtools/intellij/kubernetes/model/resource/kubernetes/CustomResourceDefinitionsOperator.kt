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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient

class CustomResourceDefinitionsOperator(client: KubernetesClient)
    : NonNamespacedResourceOperator<CustomResourceDefinition, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(CustomResourceDefinition::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NonNamespacedOperation<CustomResourceDefinition>? {
        return client.customResourceDefinitions()
    }
}
