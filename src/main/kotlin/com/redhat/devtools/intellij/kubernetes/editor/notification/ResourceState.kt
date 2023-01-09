
/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.notification

import io.fabric8.kubernetes.api.model.HasMetadata

abstract class ResourceState(val resource: HasMetadata)

abstract class Different(resource: HasMetadata, val exists: Boolean, val isOutdatedVersion: Boolean): ResourceState(resource) {
    abstract fun isPush(): Boolean
}

class Error(resource: HasMetadata, val title: String, val message: String? = null): ResourceState(resource) {
    constructor(resource: HasMetadata, title: String, e: Throwable? = null) : this(resource, title, e?.message)
}

class Identical(resource: HasMetadata): ResourceState(resource)

open class Modified(resource: HasMetadata, exists: Boolean, isOutdatedVersion: Boolean): Different(resource, exists, isOutdatedVersion) {
    override fun isPush() = true
}

class DeletedOnCluster(resource: HasMetadata): Modified(resource, false, false) {
    override fun isPush() = true

}

class Outdated(resource: HasMetadata): Different(resource, true, true) {
    override fun isPush() = false

}
