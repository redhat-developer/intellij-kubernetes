/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
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
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.impl.AppsAPIGroupClient

class ReplicaSetsOperator(client: ClientAdapter<out KubernetesClient>)
    : NamespacedResourceOperator<ReplicaSet, AppsAPIGroupClient>(client.getApps()) {

    companion object {
        val KIND = ResourceKind.create(ReplicaSet::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<ReplicaSet> {
        return client.replicaSets()
    }
}
