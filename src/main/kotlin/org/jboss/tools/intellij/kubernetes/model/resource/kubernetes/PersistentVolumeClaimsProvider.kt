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

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.client.KubernetesClient
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.WatchableAndListable
import java.util.function.Supplier

class PersistentVolumeClaimsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<PersistentVolumeClaim, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.create(PersistentVolumeClaim::class.java)
    }

    override val kind = KIND

    override fun getOperation(namespace: String): Supplier<WatchableAndListable<PersistentVolumeClaim>> {
        return Supplier { client.persistentVolumeClaims().inNamespace(namespace) }
    }
}
