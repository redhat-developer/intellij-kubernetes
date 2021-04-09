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
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceOperation
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.base.HasMetadataOperation
import java.util.function.Supplier

class AllPodsProvider(client: KubernetesClient)
    : NonNamespacedResourcesProvider<Pod, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(Pod::class.java)
    }

    override val kind = KIND

    override fun getOperation(): Supplier<ResourceOperation<Pod>?> {
        // explicit cast required bcs of compiler error?
        // PodsOperationsImpl#inAnyNamespace returns NonNamespaceOperation (ResourceOperation is a typealias for it)
        // so why is it not compiling without an explicit cast
        @Suppress("UNCHECKED_CAST")
        return Supplier { client.pods().inAnyNamespace() as ResourceOperation<Pod> }
    }
}
