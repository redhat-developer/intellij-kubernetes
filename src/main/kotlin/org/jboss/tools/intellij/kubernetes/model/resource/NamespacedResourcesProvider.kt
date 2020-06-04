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
package org.jboss.tools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient

abstract class NamespacedResourcesProvider<R : HasMetadata, C : KubernetesClient>(
    protected val client: C
) : AbstractResourcesProvider<R>(), INamespacedResourcesProvider<R> {

    protected var namespace: String? = null

    override fun getAllResources(namespace: String): Collection<R> {
        if (namespace != this.namespace) {
            this.namespace = namespace
            invalidate()
        }
        if (allResources.isEmpty()) {
            allResources.addAll(loadAllResources(namespace))
        }
        return allResources
    }

    protected abstract fun loadAllResources(namespace: String): List<R>
}
