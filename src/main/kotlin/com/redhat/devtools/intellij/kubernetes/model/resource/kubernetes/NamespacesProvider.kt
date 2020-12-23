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

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.WatchableListableDeletable
import java.util.function.Supplier

class NamespacesProvider(client: KubernetesClient)
    : NonNamespacedResourcesProvider<Namespace, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(Namespace::class.java)
    }

    override val kind = KIND

    override fun getOperation(): Supplier<WatchableListableDeletable<Namespace>> {
        return Supplier { client.namespaces() }
    }
}
