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
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.LeafState
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Descriptor

interface ITreeStructureContribution {

    val model: IResourceModel
    fun canContribute(): Boolean
    fun getChildElements(element: Any): Collection<Any>
    fun getParentElement(element: Any): Any?
    fun createDescriptor(element: Any, parent: NodeDescriptor<*>?, project: Project): NodeDescriptor<*>?
    fun isParentDescriptor(descriptor: Descriptor<*>?, element: Any): Boolean
    /**
     * Returns the leaf state for the given element.
     * Returns {@code null} if this contribution has no answer for the given element.
     */
    fun getLeafState(element: Any): LeafState?
}