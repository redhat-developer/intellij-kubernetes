package org.jboss.tools.intellij.kubernetes

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.StructureTreeModel
import java.lang.reflect.InvocationTargetException

object StructureTreeModelFactory {
	@Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class, NoSuchMethodException::class)
	fun create(structure: AbstractTreeStructure?, project: Project?): StructureTreeModel<AbstractTreeStructure> {
		return try {
			val constructor = StructureTreeModel::class.java.getConstructor(AbstractTreeStructure::class.java)
			constructor.newInstance(structure) as StructureTreeModel<AbstractTreeStructure>
		} catch (var4: NoSuchMethodException) {
			val constructor = StructureTreeModel::class.java.getConstructor(AbstractTreeStructure::class.java, Disposable::class.java)
			constructor.newInstance(structure, project) as StructureTreeModel<AbstractTreeStructure>
		}
	}
}
