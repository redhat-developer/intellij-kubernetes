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

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceOperation
import java.util.function.Supplier

open class NamespacedPodsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<Pod, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(Pod::class.java)
    }

    override val kind = KIND


    override fun getNamespacedOperation(namespace: String): Supplier<ResourceOperation<Pod>?> {
        return Supplier { client.pods().inNamespace(namespace) }
    }

    override fun getNonNamespacedOperation(): Supplier<ResourceOperation<Pod>?> {
        return Supplier { client.pods().inAnyNamespace() as ResourceOperation<Pod> }
    }

}
