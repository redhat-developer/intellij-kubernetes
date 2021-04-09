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

import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.client.AppsAPIGroupClient
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceOperation
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import java.util.function.Supplier

class StatefulSetsProvider(client: AppsAPIGroupClient)
    : NamespacedResourcesProvider<StatefulSet, AppsAPIGroupClient>(client) {

    companion object {
        val KIND = ResourceKind.create(StatefulSet::class.java)
    }

    override val kind = KIND

    override fun getNamespacedOperation(namespace: String): Supplier<ResourceOperation<StatefulSet>?> {
        return Supplier { client.statefulSets().inNamespace(namespace) }
    }

    override fun getNonNamespacedOperation(): Supplier<ResourceOperation<StatefulSet>?> {
        return Supplier { client.statefulSets().inAnyNamespace() as ResourceOperation<StatefulSet> }
    }
}
