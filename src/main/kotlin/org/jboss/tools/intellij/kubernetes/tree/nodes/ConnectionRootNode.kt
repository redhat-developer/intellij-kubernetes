package org.jboss.tools.intellij.kubernetes.tree.nodes

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.tree.LazyChildren
import java.util.*
import java.util.stream.Collectors
import javax.swing.tree.DefaultMutableTreeNode

open class ConnectionRootNode: DefaultMutableTreeNode() {

    var client: NamespacedKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())
    var childrenLoader = LazyChildren {
            client.namespaces().list().items.stream()
                .map { namespace -> DefaultMutableTreeNode(namespace)}
                .collect(Collectors.toList())
    };

    init {
        var client: NamespacedKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())
        setUserObject(client.masterUrl)
    }

    override fun children(): Enumeration<Any> {
        return childrenLoader.children(this);
    }

}