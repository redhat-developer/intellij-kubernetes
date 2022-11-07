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
package com.redhat.devtools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import java.io.Closeable

/**
 * A class that can watch, get, create and replace resources
 */
interface IResourceOperator<R: HasMetadata>: Closeable {
    val kind: ResourceKind<R>
    val allResources: Collection<R>
    fun watchAll(watcher: Watcher<in R>): Watch?
    fun watch(resource: HasMetadata, watcher: Watcher<in R>): Watch?
    fun invalidate()
    fun replaced(resource: HasMetadata): Boolean
    fun added(resource: HasMetadata): Boolean
    fun removed(resource: HasMetadata): Boolean
    fun delete(resources: List<HasMetadata>): Boolean
    fun replace(resource: HasMetadata): HasMetadata?
    fun create(resource: HasMetadata): HasMetadata?
    fun get(resource: HasMetadata): HasMetadata?
}
