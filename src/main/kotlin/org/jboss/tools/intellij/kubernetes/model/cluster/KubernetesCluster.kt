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
package org.jboss.tools.intellij.kubernetes.model.cluster

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.PodsProvider

open class KubernetesCluster(
    modelChange: IModelChangeObservable,
    client: NamespacedKubernetesClient
) : AbstractCluster<Namespace, NamespacedKubernetesClient>(modelChange, client) {


    override val resourceProviders: Map<Class<out HasMetadata>, IResourcesProvider<out HasMetadata>> =
        mapOf(
            Pair(NamespacesProvider.KIND,
                NamespacesProvider(client)),
            Pair(PodsProvider.KIND,
                PodsProvider(client, namespace)
        ))

    override fun getNamespaces(): Collection<Namespace> {
        return getResources(NamespacesProvider.KIND)
    }
}
