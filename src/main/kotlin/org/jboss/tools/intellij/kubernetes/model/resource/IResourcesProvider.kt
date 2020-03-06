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
import org.jboss.tools.intellij.kubernetes.model.WatchableResourceSupplier

interface IResourcesProvider<T: HasMetadata> {
    val kind: Class<T>
    fun getAllResources(): Collection<T>
    fun hasResource(resource: T): Boolean
    fun invalidate()
    fun invalidate(resource: HasMetadata)
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
}