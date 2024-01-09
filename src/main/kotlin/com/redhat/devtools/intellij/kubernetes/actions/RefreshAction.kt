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
import com.intellij.openapi.progress.Progressive
import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.sendTelemetry
import com.redhat.devtools.intellij.kubernetes.tree.util.getResourceKind
import javax.swing.tree.TreePath

class RefreshAction : StructureTreeAction(IActiveContext::class.java) {

	override fun actionPerformed(event: AnActionEvent?, path: TreePath?, selectedNode: Any?) {
		val descriptor = selectedNode?.getDescriptor() ?: return
		run("Refreshing $selectedNode...", true,
			Progressive {
				val telemetry = TelemetryService.instance.action("refresh resource")
				try {
					descriptor.invalidate()
					sendTelemetry(getResourceKind(descriptor.element), telemetry)
				} catch (e: Exception) {
					logger<RefreshAction>().warn("Could not refresh $descriptor resources.", e)
					telemetry.error(e).send()
				}
			})
	}
}
