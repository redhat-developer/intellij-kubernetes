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
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.client.NativeHelm
import com.redhat.devtools.intellij.kubernetes.model.helm.HelmRelease
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.hasDeletionTimestamp
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_RESOURCE_KIND
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.getKinds
import com.redhat.devtools.intellij.kubernetes.tree.ResourceWatchController
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.tree.TreePath

class DeleteResourceAction : StructureTreeAction() {

    override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selected: Any?) {
        // not called
    }

    override fun actionPerformed(event: AnActionEvent?, path: Array<out TreePath>?, selected: Array<out Any>?) {
        val model = getResourceModel() ?: return
        val toDeletes = selected?.map { it.getDescriptor()?.element as HasMetadata } ?: return
        if (!userConfirmed(toDeletes)) {
            return
        }
        run("Deleting ${toMessage(toDeletes, 30)}...", true) {
            val telemetry =
                TelemetryService.instance.action("delete resource").property(PROP_RESOURCE_KIND, getKinds(toDeletes))
            try {
                val deleting =
                    toDeletes.groupBy { if (it.kind == HelmRelease.KIND.kind) ExtendedResourceKind.HELM_RELEASE else ExtendedResourceKind.RESOURCE }
                if (deleting.containsKey(ExtendedResourceKind.RESOURCE)) {
                    model.delete(deleting[ExtendedResourceKind.RESOURCE]!!)
                }
                if (deleting.containsKey(ExtendedResourceKind.HELM_RELEASE)) {
                    deleting[ExtendedResourceKind.HELM_RELEASE]?.forEach {
                        NativeHelm.delete(it.metadata.name, it.metadata.namespace)
                    }
                }
                Notification().info("Resources Deleted", toMessage(toDeletes, 30))
                telemetry.success().send()
            } catch (e: MultiResourceException) {
                val resources = e.causes.flatMap { it.resources }
                Notification().error("Could not delete resource(s)", toMessage(resources, 30))
                logger<ResourceWatchController>().warn("Could not delete resources.", e)
                telemetry.error(e).send()
            }
        }
    }

    override fun isVisible(selected: Array<out Any>?): Boolean {
        return selected?.any { isVisible(it) }
            ?: false
    }

    override fun isVisible(selected: Any?): Boolean {
        val element = selected?.getElement<HasMetadata>()
        return element != null
                && !hasDeletionTimestamp(element)
    }
}