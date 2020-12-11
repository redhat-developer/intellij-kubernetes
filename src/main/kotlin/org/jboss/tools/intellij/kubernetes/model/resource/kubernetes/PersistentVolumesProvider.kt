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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes

import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.client.KubernetesClient
import org.jboss.tools.intellij.kubernetes.model.resource.NonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.WatchableListableDeletable
import java.util.function.Supplier

class PersistentVolumesProvider(client: KubernetesClient)
    : NonNamespacedResourcesProvider<PersistentVolume, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(PersistentVolume::class.java)
    }

    override val kind = KIND

    override fun getOperation(): Supplier<WatchableListableDeletable<PersistentVolume>> {
        return Supplier { client.persistentVolumes() }
    }
}
