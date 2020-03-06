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
import io.fabric8.kubernetes.client.KubernetesClient

interface ICluster<N: HasMetadata, C: KubernetesClient> {
    val client: C
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <T: HasMetadata> getResources(kind: Class<T>): Collection<T>
    fun getNamespaces(): Collection<N>
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
    fun invalidate()
    fun invalidate(resource: HasMetadata)
    fun startWatch()
    fun close()
}
