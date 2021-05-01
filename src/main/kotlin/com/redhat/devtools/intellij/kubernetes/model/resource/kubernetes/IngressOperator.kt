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
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.client.ExtensionsAPIGroupClient
import io.fabric8.kubernetes.client.KubernetesClient

class IngressOperator(clients: Clients<out KubernetesClient>)
    : NamespacedResourceOperator<Ingress, ExtensionsAPIGroupClient>(clients.getExtensions()) {

    companion object {
        val KIND = ResourceKind.create(Ingress::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<Ingress>? {
        return client.ingresses()
    }
}
