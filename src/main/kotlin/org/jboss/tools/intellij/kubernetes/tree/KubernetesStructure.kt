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
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.KubernetesContext

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
                listOf()
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
                    model
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
            is KubernetesContext -> KubernetesContextDescriptor(element, model)
            is Namespace -> NamespaceDescriptor(element, parent, model)
            is Pod -> PodDescriptor(element, parent, model)
            else -> null
        }
    }

    private class KubernetesContextDescriptor(element: KubernetesContext, model: IResourceModel) : TreeStructure.ContextDescriptor<KubernetesContext>(
            context = element,
            icon = IconLoader.getIcon("/icons/kubernetes-cluster.svg"),
            model = model
    )

    private class NamespaceDescriptor(element: Namespace, parent: NodeDescriptor<*>?, model: IResourceModel)
        : TreeStructure.Descriptor<Namespace>(
            element,
            parent,
            {
                var label = it.metadata.name
                if (label == model.getCurrentNamespace()) {
                    label = "* $label"
                }
                label
            },
            IconLoader.getIcon("/icons/project.png"),
            model
    )

    private class PodDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?, model: IResourceModel) : TreeStructure.Descriptor<HasMetadata>(
        element,
        parent,
        { it.metadata.name },
        IconLoader.getIcon("/icons/project.png"),
        model
    )

}