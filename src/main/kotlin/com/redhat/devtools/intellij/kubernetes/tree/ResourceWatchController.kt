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

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.actions.getDescriptor
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode

/**
 * A controller that is watching resources when a given descriptor is expanded
 */
object ResourceWatchController {

	fun install(tree: JTree) {
		tree.addTreeExpansionListener(object: TreeExpansionListener {
			override fun treeExpanded(event: TreeExpansionEvent) {
				val descriptor = getDescriptor(event)
				try {
					descriptor?.watchResources()
				} catch(e: Exception) {
					logger<ResourceWatchController>().warn("Could not watch ${descriptor?.element} resources.", e)
				}
			}

			override fun treeCollapsed(event: TreeExpansionEvent?) {
				val descriptor = getDescriptor(event)
				try {
					descriptor?.stopWatchResources()
				} catch(e: Exception) {
					logger<ResourceWatchController>().warn("Could not watch ${descriptor?.element} resources.", e)
				}
			}

			private fun getDescriptor(event: TreeExpansionEvent?): TreeStructure.Descriptor<*>? {
				return (event?.path?.lastPathComponent as? DefaultMutableTreeNode)?.getDescriptor()
			}

		})
	}
}