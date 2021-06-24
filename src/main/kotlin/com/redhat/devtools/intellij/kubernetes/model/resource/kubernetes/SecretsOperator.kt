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

import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.Clients
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient

class SecretsOperator(clients: Clients<out KubernetesClient>)
    : NamespacedResourceOperator<Secret, KubernetesClient>(clients.get()) {

    companion object {
        val KIND = ResourceKind.create(Secret::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<Secret>? {
        return client.secrets()
    }
}
