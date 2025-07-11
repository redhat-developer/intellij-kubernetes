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
package com.redhat.devtools.intellij.kubernetes.telemetry

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.tree.util.getResourceKind
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import io.fabric8.kubernetes.api.model.HasMetadata

object TelemetryService {

    const val NAME_PREFIX_EDITOR = "editor-"
    const val NAME_PREFIX_EDITOR_HINT = "${NAME_PREFIX_EDITOR}hint_clicked-"
    const val NAME_PREFIX_NAMESPACE = "current_namespace-"
    const val NAME_PREFIX_CONTEXT = "current_context-"

    const val PROP_RESOURCE_KIND = "resource_kind"
    const val PROP_IS_OPENSHIFT = "is_openshift"
    const val PROP_KUBERNETES_VERSION = "kubernetes_version"
    const val PROP_OPENSHIFT_VERSION = "openshift_version"
    const val PROP_PROPERTY_NAME = "property_name"

    val instance: TelemetryMessageBuilder by lazy {
        return@lazy if (!ApplicationManager.getApplication().isUnitTestMode) {
            TelemetryMessageBuilder(
                PluginManager.getPluginByClass(this.javaClass)
            )
        } else {
            TelemetryMessageBuilder(
                DefaultPluginDescriptor("")
            )
        }
    }

    fun sendTelemetry(resources: Collection<HasMetadata>, telemetry: TelemetryMessageBuilder.ActionMessage) {
        telemetry.property(PROP_RESOURCE_KIND, getKinds(resources)).send()
    }

    fun sendTelemetry(resource: HasMetadata?, telemetry: TelemetryMessageBuilder.ActionMessage) {
        sendTelemetry(getResourceKind(resource), telemetry)
    }

    fun sendTelemetry(kind: ResourceKind<*>?, telemetry: TelemetryMessageBuilder.ActionMessage) {
        telemetry.property(PROP_RESOURCE_KIND, kindOrUnknown(kind)).send()
    }

    fun sendTelemetry(info: KubernetesResourceInfo?, telemetry: TelemetryMessageBuilder.ActionMessage) {
        telemetry.property(PROP_RESOURCE_KIND, kindOrUnknown(info?.kind, info?.apiGroup)).send()
    }

    fun getKinds(resources: Collection<HasMetadata>): String {
        return resources
            .distinct()
            .groupBy { kindOrUnknown(getResourceKind(it)) }
            .keys
            .toList()
            .joinToString()
    }

    private fun kindOrUnknown(kind: ResourceKind<*>?): String {
        return kindOrUnknown(kind?.kind, kind?.version);
    }

    private fun kindOrUnknown(kind: String?, apiGroup: String?): String {
        return if (kind != null
            && apiGroup != null) {
            "${kind}/${apiGroup}"
        } else {
            "unknown"
        }
    }
}
