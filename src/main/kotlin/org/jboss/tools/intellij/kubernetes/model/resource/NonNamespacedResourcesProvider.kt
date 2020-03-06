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
import org.jboss.tools.intellij.kubernetes.model.WatchableResourceSupplier

abstract class NonNamespacedResourcesProvider<R : HasMetadata, C : KubernetesClient>(client: C) :
    ResourcesProvider<R, C>(client), INonNamespacedResourcesProvider<R> {

    override val allResources: MutableSet<R> = LinkedHashSet()
        get() {
            if (field.isEmpty()) {
                field.addAll(loadAllResources())
            }
            return field
        }

    protected abstract fun loadAllResources(): List<R>
}
