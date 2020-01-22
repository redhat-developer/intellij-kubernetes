/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.tree

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.IKubernetesResourceModel
import org.jboss.tools.intellij.kubernetes.model.PodsProvider
import javax.swing.Icon

class KubernetesTreeStructure(private val project: Project) : AbstractTreeStructure() {

    override fun getRootElement() = getResourceModel().getClient()

    override fun getChildElements(element: Any): Array<Any> {
        try {
            return when(element) {
                rootElement ->
                    arrayOf(
                        Categories.NAMESPACES,
                        Categories.NODES,
                        Categories.WORKLOADS)
                Categories.NAMESPACES ->
                    getResourceModel().getAllNamespaces().toTypedArray()
                Categories.NODES ->
                    emptyArray()
                Categories.WORKLOADS ->
                    arrayOf(Categories.PODS)
                Categories.PODS -> {
                    getResourceModel().getResources(
                        getResourceModel().getCurrentNamespace()?.metadata?.name,
                        PodsProvider.KIND)
                        .toTypedArray()
                }
                else ->
                    emptyArray()
            }
        } catch(e: RuntimeException) {
            return arrayOf(e)
        }
    }

    override fun getParentElement(element: Any): Any? {
        try {
            return when (element) {
                rootElement ->
                    null
                is Namespace ->
                    rootElement
                is HasMetadata ->
                    getResourceModel().getNamespace(element.metadata.namespace)
                else ->
                    rootElement
            }
        } catch(e: RuntimeException) {
            return null
        }
    }

    override fun isAlwaysLeaf(element: Any): Boolean {
        return false
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*> {
        return when(element) {
            is NamespacedKubernetesClient -> ClusterDescriptor(element)
            is Namespace -> NamespaceDescriptor(element, parent)
            is Pod -> PodDescriptor(element, parent)
            is Exception -> ErrorDescriptor(element, parent)
            Categories.NAMESPACES -> CategoryDescriptor(Categories.NAMESPACES, parent)
            Categories.NODES -> CategoryDescriptor(Categories.NODES, parent)
            Categories.WORKLOADS -> CategoryDescriptor(Categories.WORKLOADS, parent)
            Categories.PODS -> CategoryDescriptor(Categories.PODS, parent)
            else -> Descriptor(element, parent);
        }
    }

    private fun getResourceModel(): IKubernetesResourceModel {
        return ServiceManager.getService(project, IKubernetesResourceModel::class.java)
    }

    override fun commit() = Unit

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any) = true


    enum class Categories(val label: String) {
        NAMESPACES("Namespaces"),
        NODES("Nodes"),
        WORKLOADS("Workloads"),
        PODS("Pods")
    }

    companion object Descriptors {

        private class ClusterDescriptor(element: NamespacedKubernetesClient) : Descriptor<NamespacedKubernetesClient>(
            element, null,
            { element.masterUrl.toString() },
            IconLoader.getIcon("/icons/kubernetes-cluster.svg")
        ) {
        }

        private class NamespaceDescriptor(element: Namespace, model: IKubernetesResourceModel, parent: NodeDescriptor<*>?) : Descriptor<Namespace>(
            element, parent,
            {
                var label = it.metadata.name
                if (it == model.getCurrentNamespace()) {
                    label = "* $label"
                }
                label
            },
            IconLoader.getIcon("/icons/project.png")
        ) {
        }

        private class PodDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?) : Descriptor<HasMetadata>(
            element, parent,
            { it.metadata.name },
            IconLoader.getIcon("/icons/project.png")
        ) {
        }

        private class CategoryDescriptor(category: Categories, parent: NodeDescriptor<*>?) : Descriptor<Categories>(
            category, parent,
            { it.label }) {
        }

        private class ErrorDescriptor(exception: java.lang.Exception, parent: NodeDescriptor<*>?) :
            Descriptor<java.lang.Exception>(
                exception, parent,
                { "Error: " + it.message },
                AllIcons.General.BalloonError
            ) {
        }

        private open class Descriptor<T>(
            element: T,
            parent: NodeDescriptor<*>?,
            private val labelProvider: (T) -> String = { it?.toString() ?: "" },
            private val nodeIcon: Icon? = null
        ) : PresentableNodeDescriptor<T>(null, parent) {

            private val element = element;

            override fun update(presentation: PresentationData) {
                presentation.presentableText = labelProvider.invoke(element)
                if (nodeIcon != null) {
                    presentation.setIcon(nodeIcon)
                }
            }

            override fun getElement(): T {
                return element;
            }
        }
    }
}
