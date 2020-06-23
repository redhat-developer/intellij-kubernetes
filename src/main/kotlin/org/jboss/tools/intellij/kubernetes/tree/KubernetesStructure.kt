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
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Service
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.KubernetesContext
import org.jboss.tools.intellij.kubernetes.model.resource.PodForService
import org.jboss.tools.intellij.kubernetes.model.resourceName
import org.jboss.tools.intellij.kubernetes.model.util.getContainers
import org.jboss.tools.intellij.kubernetes.model.util.isRunning
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.ENDPOINTS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NAMESPACES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PODS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SERVICES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.*
import javax.swing.Icon

class KubernetesStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    object Folders {
        val NAMESPACES = Folder("Namespaces", Namespace::class.java)
        val NODES = Folder("Nodes", Node::class.java)
        val WORKLOADS = Folder("Workloads", null)
        val PODS = Folder("Pods", Pod::class.java)
        val NETWORK = Folder("Network", null)
        val SERVICES = Folder("Services", Service::class.java)
        val ENDPOINTS = Folder("Endpoints", Endpoints::class.java)
    }

    override fun canContribute() = true

    override fun getChildElements(element: Any): Collection<Any> {
        return when (element) {
            getRootElement() ->
                mutableListOf<Any>(
                    NAMESPACES,
                    NODES,
                    WORKLOADS,
                    NETWORK
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
            is Pod ->
                listOf(PodContainersDescriptorFactory(element),
                        PodIpDescriptorFactory(element))
            NETWORK ->
                listOf<Any>(
                        SERVICES,
                        ENDPOINTS)
            SERVICES ->
                model.resources(Service::class.java)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
            is Service ->
                model.resources(Pod::class.java)
                        .inCurrentNamespace()
                        .filtered(PodForService(element))
                        .list()
                        .sortedBy(resourceName)
            ENDPOINTS ->
                model.resources(Endpoints::class.java)
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
                NAMESPACES ->
                    getRootElement()
                is Pod ->
                    listOf(PODS, NODES, SERVICES)
                is Node ->
                    NODES
                NODES ->
                    getRootElement()
                PODS ->
                    WORKLOADS
                WORKLOADS ->
                    getRootElement()
                is Service ->
                    SERVICES
                SERVICES ->
                    NETWORK
                is Endpoints ->
                    ENDPOINTS
                ENDPOINTS ->
                    NETWORK
                NETWORK ->
                    getRootElement()
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
            is Service -> ServiceDescriptor(element, parent, model)
            is Endpoints -> EndpointsDescriptor(element, parent, model)
            is DescriptorFactory<*> -> element.create(parent, model)
            else -> null
        }
    }

    private class KubernetesContextDescriptor(element: KubernetesContext, model: IResourceModel) : ContextDescriptor<KubernetesContext>(
            context = element,
            model = model
    ) {
        override fun getIcon(element: KubernetesContext): Icon? {
            return IconLoader.getIcon("/icons/kubernetes-cluster.svg")
        }
    }

    private class NamespaceDescriptor(element: Namespace, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<Namespace>(
            element,
            parent,
            model
    ) {
        override fun getLabel(element: Namespace): String {
            var label = element.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            return label
        }

        override fun getIcon(element: Namespace): Icon? {
            return IconLoader.getIcon("/icons/project.png")
        }
    }

    private class PodDescriptor(pod: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<Pod>(
            pod,
            parent,
            model
    ) {
        override fun getLabel(element: Pod): String {
            return element.metadata.name
        }

        override fun getIcon(element: Pod): Icon? {
            return if(element.isRunning()) {
                IconLoader.getIcon("/icons/runningPod.svg")
            } else {
                IconLoader.getIcon("/icons/errorPod.svg")
            }
        }
    }

    private class PodContainersDescriptorFactory(pod: Pod): DescriptorFactory<Pod>(pod) {

        override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<Pod>? {
            return PodContainersDescriptor(resource, parent, model)
        }

        private class PodContainersDescriptor(element: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
            : ResourcePropertyDescriptor<Pod>(
                element,
                parent,
                model
        ) {
            override fun getLabel(element: Pod): String {
                val total = element.getContainers().size
                val ready = element.getContainers().filter { it.ready }.size
                val state = element.status.phase
                return "$state ($ready/$total)"
            }
        }
    }

    private class PodIpDescriptorFactory(pod: Pod): DescriptorFactory<Pod>(pod) {

        override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<Pod>? {
            return PodIpDescriptor(resource, parent, model)
        }

        private class PodIpDescriptor(element: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
            : ResourcePropertyDescriptor<Pod>(
                element,
                parent,
                model
        ) {
            override fun getLabel(element: Pod): String {
                return element.status?.podIP ?: "<No IP>"
            }
        }
    }

    private class ServiceDescriptor(element: Service, parent: NodeDescriptor<*>?, model: IResourceModel)
        : ResourceDescriptor<Service>(
            element,
            parent,
            model
    )

    private class EndpointsDescriptor(element: Endpoints, parent: NodeDescriptor<*>?, model: IResourceModel)
        : ResourceDescriptor<Endpoints>(
            element,
            parent,
            model
    )

    private class KubernetesNodeDescriptor(element: Node, parent: NodeDescriptor<*>?, model: IResourceModel)
        : ResourceDescriptor<Node>(
            element,
            parent,
            model
    )
}