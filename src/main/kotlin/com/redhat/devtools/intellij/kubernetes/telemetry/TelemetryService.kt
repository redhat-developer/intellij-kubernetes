/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.telemetry;

import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind;
import com.redhat.devtools.intellij.kubernetes.model.util.API_GROUP_VERSION_DELIMITER
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;

object TelemetryService {

    const val NAME_PREFIX_EDITOR = "editor-"
    const val NAME_PREFIX_NAMESPACE = "current_namespace-"
    const val NAME_PREFIX_CONTEXT = "current_context-"

    const val PROP_RESOURCE_KIND = "resource_kind"
    const val PROP_IS_OPENSHIFT = "is_openshift"
    const val PROP_KUBERNETES_VERSION = "kubernetes_version"
    const val PROP_OPENSHIFT_VERSION = "openshift_version"

    val instance: TelemetryMessageBuilder by lazy {
        TelemetryMessageBuilder(TelemetryService::class.java.classLoader)
    }

    fun sendTelemetry(resource: HasMetadata?, telemetry: TelemetryMessageBuilder.ActionMessage) {
        val kind = if (resource != null) {
            getKind(resource)
        } else {
            "unknown"
        }
        telemetry.property(PROP_RESOURCE_KIND, kind).send()
    }

    fun getKinds(resources: Collection<HasMetadata>): String {
        return resources
            .distinct()
            .groupBy { getKind(it) }
            .keys
            .toList()
            .joinToString()
    }

    fun getKind(resource: HasMetadata): String {
        val kind = ResourceKind.create(resource)
        return "${kind.version}/${kind.kind}"
    }

}
