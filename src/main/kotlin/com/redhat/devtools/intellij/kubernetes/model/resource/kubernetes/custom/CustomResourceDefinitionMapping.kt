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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import com.redhat.devtools.intellij.kubernetes.model.util.isMatching
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient

object CustomResourceDefinitionMapping {

    fun getCustomResourceDefinition(resource: HasMetadata, definitions: Collection<CustomResourceDefinition>): CustomResourceDefinition? {
        return definitions
            .firstOrNull { definition -> isMatching(resource, definition) }
    }

    fun isCustomResource(resource: HasMetadata, definitions: Collection<CustomResourceDefinition>): Boolean {
        return getCustomResourceDefinition(resource, definitions) != null
    }

    fun getDefinitions(client: KubernetesClient): Collection<CustomResourceDefinition> {
        return client.apiextensions().v1beta1().customResourceDefinitions().list().items ?: emptyList()
    }

}