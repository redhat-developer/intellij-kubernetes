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
import com.intellij.openapi.util.IconLoader
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.KubernetesContext
import org.jboss.tools.intellij.kubernetes.model.resourceName
import org.jboss.tools.intellij.kubernetes.model.util.isRunning
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.*
import javax.swing.Icon

class KubernetesStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val NAMESPACES = Folder("Namespaces", Namespace::class.java)
        val NODES = Folder("Nodes", Node::class.java)
        val WORKLOADS = Folder("Workloads", null)
        val PODS = Folder("Pods", Pod::class.java)
    }

    override fun canContribute() = true

    override fun getChildElements(element: Any): Collection<Any> {
        return when (element) {
            getRootElement() ->
                mutableListOf<Any>(
                    NAMESPACES,
                    NODES,
                    WORKLOADS
                )
            NAMESPACES ->
                model.resources(Namespace::class.java)
                        .inNoNamespace()
                        .list()
                        .sortedBy(resourceName)
            NODES ->
                model.resources(Node::class.java)
                        .inNoNamespace()
                        .list()
                        .sortedBy(resourceName)
            is Node ->
                model.resources(Pod::class.java)
                        .inAnyNamespace()
                        .list()
                        .sortedBy(resourceName)
            WORKLOADS ->
                listOf<Any>(PODS)
            PODS ->
                model.resources(Pod::class.java)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
            else ->
                listOf()
        }
    }

    override fun getParentElement(element: Any): Any? {
        try {
            return when (element) {
                getRootElement() ->
                    model
                is Namespace ->
                    NAMESPACES
                is Pod ->
                    listOf(PODS, NODES)
                NAMESPACES,
                NODES,
                WORKLOADS ->
                    getRootElement()
                PODS ->
                    WORKLOADS
                else ->
                    getRootElement()
            }
        } catch(e: ResourceException) {
            return null
        }
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*>? {
        return when(element) {
            is KubernetesContext -> KubernetesContextDescriptor(element, model)
            is Namespace -> NamespaceDescriptor(element, parent, model)
            is Node -> KubernetesNodeDescriptor(element, parent, model)
            is Pod -> PodDescriptor(element, parent, model)
            else -> null
        }
    }

    private class KubernetesContextDescriptor(element: KubernetesContext, model: IResourceModel) : ContextDescriptor<KubernetesContext>(
            context = element,
            model = model
    ) {
        override fun getIcon(context: KubernetesContext): Icon? {
            return IconLoader.getIcon("/icons/kubernetes-cluster.svg")
        }
    }

    private class NamespaceDescriptor(element: Namespace, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<Namespace>(
            element,
            parent,
            model
    ) {
        override fun getLabel(namespace: Namespace): String {
            var label = namespace.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            return label
        }

        override fun getIcon(namespace: Namespace): Icon? {
            return IconLoader.getIcon("/icons/project.png")
        }
    }

    private class PodDescriptor(element: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<Pod>(
            element,
            parent,
            model
    ) {
        override fun getLabel(element: Pod): String {
            return element.metadata.name
        }

        override fun getIcon(pod: Pod): Icon? {
            return IconLoader.getIcon("/icons/project.png")
        }
    }

    private class KubernetesNodeDescriptor(element: Node, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<Node>(
            element,
            parent,
            model
    ) {
        override fun getLabel(element: Node): String {
            return element.metadata.name
        }

        override fun getIcon(node: Node): Icon? {
            return IconLoader.getIcon("/icons/project.png")
        }
    }
}