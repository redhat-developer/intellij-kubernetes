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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.IconLoader
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import java.net.URL
import javax.swing.Icon

/**
 * A factory that creates nodes (PresentableNodeDescriptor) for a (tree-) model.
 *
 * @see PresentableNodeDescriptor
 */
class TreeStructure(private val project: com.intellij.openapi.project.Project) : AbstractTreeStructure() {

    override fun getRootElement(): Any {
        return getResourceModel().getClient()!!
    }

    override fun getChildElements(element: Any): Array<Any> {
        try {
            return when (element) {
                rootElement ->
                    if (rootElement is OpenShiftClient) {
                        arrayOf<Any>(
                            Categories.NAMESPACES,
                            Categories.NODES,
                            Categories.WORKLOADS,
                            Categories.PROJECTS)
                    } else {
                        arrayOf<Any>(
                            Categories.NAMESPACES,
                            Categories.NODES,
                            Categories.WORKLOADS)
                    }
                Categories.NODES ->
                    emptyArray()
                Categories.WORKLOADS ->
                    arrayOf(Categories.PODS)
                Categories.NAMESPACES,
                Categories.PROJECTS,
                Categories.PODS ->
                    getResources((element as Categories).kind)
                else ->
                    emptyArray()
            }
        } catch(e: ResourceException) {
            return arrayOf(e)
        }
    }

    private fun getResources(kind: Class<out HasMetadata>?): Array<Any> {
        if (kind == null) {
            return emptyArray()
        }
        return getResourceModel()
            .getResources(kind)
            .sortedBy { it.metadata.name }
            .toTypedArray()
    }

    override fun getParentElement(element: Any): Any? {
        try {
            return when (element) {
                rootElement ->
                    null
                is Namespace ->
                    Categories.NAMESPACES
                is Project ->
                    Categories.PROJECTS
                is Pod ->
                    Categories.PODS
                is HasMetadata ->
                    Categories.getByKind(getResourceModel().getKind(element))
                Categories.NAMESPACES,
                Categories.PROJECTS,
                Categories.NODES,
                Categories.WORKLOADS ->
                    rootElement
                Categories.PODS ->
                    Categories.WORKLOADS
                else ->
                    rootElement
            }
        } catch(e: ResourceException) {
            return null
        }
    }

    override fun isAlwaysLeaf(element: Any): Boolean {
        return false
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*> {
        return when(element) {
            is NamespacedKubernetesClient -> KubernetesClusterDescriptor(element)
            is NamespacedOpenShiftClient -> OpenShiftClusterDescriptor(element)
            is Namespace -> NamespaceDescriptor(element, getResourceModel(), parent)
            is Project -> ProjectDescriptor(element, getResourceModel(), parent)
            is Pod -> PodDescriptor(element, parent)
            is Exception -> ErrorDescriptor(element, parent)
            is Categories -> CategoryDescriptor(element, parent)
            else -> Descriptor(element, parent);
        }
    }

    private fun getResourceModel(): IResourceModel {
        return ServiceManager.getService(project, IResourceModel::class.java)
    }

    override fun commit() = Unit

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any) = true

    private fun getServerUrl(): URL? {
        return getResourceModel().getClient()?.masterUrl
    }

    enum class Categories(val label: String, val kind: Class<out HasMetadata>?) {
        NAMESPACES("Namespaces", Namespace::class.java),
        NODES("Nodes", null),
        WORKLOADS("Workloads", null),
        PODS("Pods", Pod::class.java),
        PROJECTS("Projects", Project::class.java);

        companion object {
            @JvmStatic
            fun getByKind(kind: Class<out HasMetadata>?): Categories? {
                if (kind == null) {
                    return null
                }
                return values().find { it.kind == kind }
            }
        }
    }

    companion object Descriptors {

        private class KubernetesClusterDescriptor(element: NamespacedKubernetesClient) : Descriptor<NamespacedKubernetesClient>(
            element, null,
            { element.masterUrl.toString() },
            IconLoader.getIcon("/icons/kubernetes-cluster.svg")
        )

        private class OpenShiftClusterDescriptor(element: NamespacedOpenShiftClient) : Descriptor<NamespacedOpenShiftClient>(
            element, null,
            { element.masterUrl.toString() },
            IconLoader.getIcon("/icons/openshift-cluster.svg")
        )

        private class NamespaceDescriptor(element: Namespace, model: IResourceModel, parent: NodeDescriptor<*>?) : Descriptor<Namespace>(
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

        private class ProjectDescriptor(element: Project, model: IResourceModel, parent: NodeDescriptor<*>?) : Descriptor<Project>(
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

        private class PodDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?) : Descriptor<HasMetadata>(
            element,
            parent,
            { it.metadata.name },
            IconLoader.getIcon("/icons/project.png")
        )

        private class CategoryDescriptor(category: Categories, parent: NodeDescriptor<*>?) : Descriptor<Categories>(
            category, parent,
            { it.label })

        private class ErrorDescriptor(exception: java.lang.Exception, parent: NodeDescriptor<*>?) : Descriptor<java.lang.Exception>(
            exception,
            parent,
            { "Error: " + it.message },
            AllIcons.General.BalloonError
        )

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
