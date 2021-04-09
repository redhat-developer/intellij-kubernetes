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

import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceOperation
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.function.Supplier

class ConfigMapsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<ConfigMap, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(ConfigMap::class.java)
    }

    override val kind = KIND

    override fun getNamespacedOperation(namespace: String): Supplier<ResourceOperation<ConfigMap>?> {
        return Supplier { client.configMaps().inNamespace(namespace) }
    }

    override fun getNonNamespacedOperation(): Supplier<out ResourceOperation<ConfigMap>?> {
        return Supplier { client.configMaps().inAnyNamespace() as ResourceOperation<ConfigMap> }
    }
}
