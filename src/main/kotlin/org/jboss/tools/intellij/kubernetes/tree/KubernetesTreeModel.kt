package org.jboss.tools.intellij.kubernetes.tree

import com.intellij.ui.tree.StructureTreeModel
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourcesModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class KubernetesTreeModel: StructureTreeModel(true) {
    init {
        KubernetesResourcesModel.addListener(object: KubernetesResourcesModel.ResourcesChangedListener {
            override fun removed(removed: List<Any>) {

                removed.forEach {
                        when (it) {
                        is NamespacedKubernetesClient
                            -> {
                                invoker.invokeLater { invalidate(TreePath(root), true) }
                            }
                        }
                    };
            }

            override fun added(removed: List<Any>) {
            }

            override fun modified(removed: List<Any>) {
            }
        })
    }
}