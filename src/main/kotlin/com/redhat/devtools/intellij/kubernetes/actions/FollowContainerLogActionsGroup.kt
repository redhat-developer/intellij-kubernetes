/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DynamicActionGroup
import com.intellij.util.ui.tree.TreeUtil
import io.fabric8.kubernetes.api.model.Pod

class FollowContainerLogActionsGroup: ActionGroup("Follow Container Log", true), DynamicActionGroup {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val tree = e?.getTree() ?: return emptyArray()
        val pod = TreeUtil.collectSelectedUserObjects(tree).firstOrNull()?.getElement<Pod>() ?: return emptyArray()
        val containers = pod.spec.containers
        return containers.map { container ->
            FollowLogsAction(container, pod).apply {
                val index = containers.indexOf(container)
                e.presentation.text = "Container $index: ${container.name}"
            }
        }.toTypedArray()
    }
}