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
    client: C,
    namespace: String?
) : ResourcesProvider<R, C>(client), INamespacedResourcesProvider<R> {

    override var namespace: String? = namespace
        set(namespace) {
            invalidate()
            field = namespace
        }

    override val allResources: MutableSet<R> = LinkedHashSet()
        get() {
            if (field.isEmpty()) {
                if (namespace != null) {
                    field.addAll(loadAllResources(namespace!!))
                }
            }
            return field
        }

    protected abstract fun loadAllResources(namespace: String): List<R>
}
