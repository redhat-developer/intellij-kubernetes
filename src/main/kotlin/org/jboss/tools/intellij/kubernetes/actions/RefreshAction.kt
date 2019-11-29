/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.actions

import com.intellij.icons.AllIcons.Actions.Refresh
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourcesModel

class RefreshAction: AnAction(Refresh) {

    override fun actionPerformed(e: AnActionEvent?) {
        KubernetesResourcesModel.refresh()
    }

}