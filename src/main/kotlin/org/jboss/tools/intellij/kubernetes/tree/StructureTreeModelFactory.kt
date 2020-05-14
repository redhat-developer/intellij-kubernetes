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

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

import com.intellij.ui.tree.StructureTreeModel
import java.lang.reflect.Constructor

import java.lang.reflect.InvocationTargetException

class StructureTreeModelFactory {

	companion object {

		/**
		 * Builds the {@link StructureTreeModel} through reflection as StructureTreeModel does not have a stable API.
		 *
		 * @param structure the structure to associate
		 * @param project the IJ project
		 * @return the build model
		 * @throws IllegalAccessException
		 * @throws InvocationTargetException
		 * @throws InstantiationException
		 * @throws NoSuchMethodException
		 */
		@JvmStatic
		@Throws(IllegalAccessException::class,
				InvocationTargetException::class,
				InstantiationException::class,
				NoSuchMethodException::class)
		fun create(structure: AbstractTreeStructure, project: Project): StructureTreeModel<AbstractTreeStructure> {
			return try {
				val constructor: Constructor<StructureTreeModel<*>> =
						StructureTreeModel::class.java.getConstructor(AbstractTreeStructure::class.java)
				return constructor.newInstance(structure) as StructureTreeModel<AbstractTreeStructure>
			} catch (e: NoSuchMethodException) {
				// IC 2019.3+
				val constructor: Constructor<StructureTreeModel<*>> =
						StructureTreeModel::class.java.getConstructor(AbstractTreeStructure::class.java, Disposable::class.java)
				return constructor.newInstance(structure, project) as StructureTreeModel<AbstractTreeStructure>
			}
		}
	}
}