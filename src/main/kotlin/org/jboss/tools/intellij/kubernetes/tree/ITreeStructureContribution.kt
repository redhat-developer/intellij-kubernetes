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
package org.jboss.tools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.LeafState
import org.jboss.tools.intellij.kubernetes.model.IResourceModel

interface ITreeStructureContribution {

    val model: IResourceModel
    fun canContribute(): Boolean
    fun getChildElements(element: Any): Collection<Any>
    fun getParentElement(element: Any): Any?
    fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*>?

    /**
     * Returns the leaf state for the given element.
     * Returns {@code null} if this contribution has no answer for the given element.
     */
    fun getLeafState(element: Any): LeafState?
}