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

class KubernetesTreeStructure(private val project: Project) : AbstractTreeStructure() {

    enum class Categories(val label: String) {
        NAMESPACES("Namespaces"),
        NODES("Nodes"),
        WORKLOADS("Workloads"),
        PODS("Pods")
    }

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
            else -> ResourceDescriptor(element, parent);
        }
    }

    class ClusterDescriptor(element: NamespacedKubernetesClient): ResourceDescriptor<NamespacedKubernetesClient>(element, null) {
        override fun update(presentation: PresentationData) {
            presentation.presentableText = element.masterUrl.toString()
            presentation.setIcon(IconLoader.getIcon("/icons/kubernetes-cluster.svg"))
        }
    }

    class NamespaceDescriptor(element: Namespace, parent: NodeDescriptor<*>?)
        : ResourceDescriptor<Namespace>(element, parent) {
        override fun update(presentation: PresentationData) {
            presentation.presentableText = element.metadata.name;
            presentation.setIcon(IconLoader.getIcon("/icons/project.png"))
        }
    }

    class PodDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?)
        : ResourceDescriptor<HasMetadata>(element, parent) {
        override fun update(presentation: PresentationData) {
            presentation.presentableText = element.metadata.name;
            presentation.setIcon(IconLoader.getIcon("/icons/project.png"))
        }
    }

    class HasMetadataDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?)
        : ResourceDescriptor<HasMetadata>(element, parent) {
        override fun update(presentation: PresentationData) {
            presentation.presentableText = element.metadata.name;
            presentation.setIcon(IconLoader.getIcon("/icons/project.png"))
        }
    }

    abstract class ResourceCategoryDescriptor<T: HasMetadata>(private val label: String, element: Class<T>, parent: NodeDescriptor<*>?)
        : ResourceDescriptor<Class<T>>(element, parent) {

        override fun update(presentation: PresentationData) {
            presentation.presentableText = label;
        }
    }

    class CategoryDescriptor(private val category: Categories, parent: NodeDescriptor<*>?)
        : PresentableNodeDescriptor<Categories>(null, parent) {

        override fun update(presentation: PresentationData) {
            presentation.presentableText = category.label;
        }

        override fun getElement(): Categories {
            return category
        }
    }

    class ErrorDescriptor(private var element: java.lang.Exception, parent: NodeDescriptor<*>?)
        : PresentableNodeDescriptor<java.lang.Exception>(null, parent) {

        override fun update(presentation: PresentationData) {
            presentation.presentableText = "Error: " + element.message;
            presentation.setIcon(AllIcons.General.BalloonError)
        }

        override fun getElement(): java.lang.Exception {
            return element
        }
    }

    open class ResourceDescriptor<T>(element: T, parent: NodeDescriptor<*>?): PresentableNodeDescriptor<T>(null, parent) {

        private val element = element;

        override fun update(presentation: PresentationData) {
            presentation.presentableText = element.toString();
        }

        override fun getElement(): T {
            return element;
        }

    }

    private fun getResourceModel(): IKubernetesResourceModel {
        return ServiceManager.getService(project, IKubernetesResourceModel::class.java)
    }

    override fun commit() = Unit

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any) = true
}
