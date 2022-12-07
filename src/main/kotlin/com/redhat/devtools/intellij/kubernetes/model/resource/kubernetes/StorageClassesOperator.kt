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

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.impl.StorageAPIGroupClient

class StorageClassesOperator(client: ClientAdapter<out KubernetesClient>)
    : NonNamespacedResourceOperator<StorageClass, StorageAPIGroupClient>(client.getStorage()) {

    companion object {
        val KIND = ResourceKind.create(StorageClass::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NonNamespacedOperation<StorageClass> {
        return client.storageClasses()
    }

}
