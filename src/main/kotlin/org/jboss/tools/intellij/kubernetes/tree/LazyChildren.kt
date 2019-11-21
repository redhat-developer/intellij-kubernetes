package org.jboss.tools.intellij.kubernetes.tree

import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.tree.nodes.ErrorNode
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode

class LazyChildren(loader: () -> List<DefaultMutableTreeNode>) {

    private var loader = loader;
    private var loaded = false;

    fun children(node: DefaultMutableTreeNode): Enumeration<Any> {
        if (!loaded) {
            try {
                val children = loader();
                addAll(children, node)
            } catch(e: KubernetesClientException) {
                node.add(ErrorNode(e.message))
            }
            loaded = true;
        }
        return node.children();
    }

    private fun addAll(children: List<DefaultMutableTreeNode>, node: DefaultMutableTreeNode) {
        for (child in children) {
            node.add(child)
        }
    }

    fun reset () {
        loaded = false;
    }
}