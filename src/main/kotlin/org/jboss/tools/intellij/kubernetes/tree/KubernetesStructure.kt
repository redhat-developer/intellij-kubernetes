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
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException

class KubernetesStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val NAMESPACES = TreeStructure.Folder("Namespaces", Namespace::class.java)
        val NODES = TreeStructure.Folder("Nodes", null)
        val WORKLOADS = TreeStructure.Folder("Workloads", null)
        val PODS = TreeStructure.Folder("Pods", Pod::class.java)
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
            NODES ->
                listOf<Any>()
            WORKLOADS ->
                listOf<Any>(PODS)
            NAMESPACES,
            PODS ->
                getResources((element as TreeStructure.Folder).kind)
            else ->
                listOf()
        }
    }

    override fun getParentElement(element: Any): Any? {
        try {
            return when (element) {
                getRootElement() ->
                    null
                is Namespace ->
                    NAMESPACES
                is Pod ->
                    PODS
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
            is NamespacedKubernetesClient -> KubernetesClusterDescriptor(element)
            is Namespace -> NamespaceDescriptor(element, model, parent)
            is Pod -> PodDescriptor(element, parent)
            else -> null
        }
    }

    private class KubernetesClusterDescriptor(element: NamespacedKubernetesClient) : TreeStructure.Descriptor<NamespacedKubernetesClient>(
        element, null,
        { element.masterUrl.toString() },
        IconLoader.getIcon("/icons/kubernetes-cluster.svg")
    )

    private class NamespaceDescriptor(element: Namespace, model: IResourceModel, parent: NodeDescriptor<*>?) : TreeStructure.Descriptor<Namespace>(
        element,
        parent,
        {
            var label = it.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            label
        },
        IconLoader.getIcon("/icons/project.png")
    )

    private class PodDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?) : TreeStructure.Descriptor<HasMetadata>(
        element,
        parent,
        { it.metadata.name },
        IconLoader.getIcon("/icons/project.png")
    )

}