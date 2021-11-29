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
package com.redhat.devtools.intellij.kubernetes.tree.util

import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure
import io.fabric8.kubernetes.api.model.HasMetadata

fun getResourceKind(element: Any?): ResourceKind<*>? {
    return when (element) {
        is HasMetadata ->
            ResourceKind.create(element)
        is TreeStructure.Folder ->
            element.kind
        else -> {
            null
        }
    }
}
