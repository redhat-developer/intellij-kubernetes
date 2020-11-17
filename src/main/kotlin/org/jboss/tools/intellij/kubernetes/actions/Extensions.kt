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
package org.jboss.tools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.ServiceManager
import io.fabric8.kubernetes.api.model.HasMetadata
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure
import javax.swing.tree.DefaultMutableTreeNode


fun AnAction.getResourceModel(): IResourceModel? {
    return ServiceManager.getService(IResourceModel::class.java)
}

fun Any?.getDescriptor(): TreeStructure.Descriptor<*>? {
    return (this as? DefaultMutableTreeNode)?.userObject as? TreeStructure.Descriptor<*>
}

fun <T> Any.getElement(): T? {
    return (this as? DefaultMutableTreeNode)?.getDescriptor()?.element as? T
}

fun TreeStructure.Descriptor<*>.getKind(): String? {
    val element = this?.element
    return (element as? TreeStructure.Folder)?.kind?.kind ?: (element as? HasMetadata)?.kind
}

