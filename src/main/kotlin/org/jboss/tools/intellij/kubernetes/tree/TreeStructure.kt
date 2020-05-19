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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IconLoader
import io.fabric8.kubernetes.api.model.HasMetadata
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.cluster.IContext
import java.util.Optional
import javax.swing.Icon

/**
 * A factory that creates nodes (PresentableNodeDescriptor) for a (tree-) model.
 *
 * @see PresentableNodeDescriptor
 */
open class TreeStructure(private val model: IResourceModel,
                         private val extensionPoint: ExtensionPointName<ITreeStructureContributionFactory> =
                                 ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.structureContribution")): AbstractTreeStructure() {

    private val contributions by lazy {
        listOf(
            *getTreeStructureDefaults(model).toTypedArray(),
            *getTreeStructureExtensions(model).toTypedArray()
        )
    }

    override fun getRootElement(): Any {
        return model
    }

    override fun getChildElements(element: Any): Array<Any> {
        return when (element) {
            rootElement -> model.allContexts.toTypedArray()
            else -> getValidContributions()
                    .flatMap { getChildElements(element, it) }
                    .toTypedArray()
        }
    }

    private fun getChildElements(element: Any, contribution: ITreeStructureContribution): Collection<Any> {
        try {
            return contribution.getChildElements(element)
        } catch (e:  java.lang.Exception) {
            logger<TreeStructure>().warn(e)
            return listOf(e)
        }
    }

    override fun getParentElement(element: Any): Any? {
            val parent: Optional<Any?> = getValidContributions().stream()
                    .map { getParentElement(element, it) }
                    .filter { it != null }
                    .findAny()
            return parent.orElse(rootElement)
    }

    private fun getParentElement(element: Any, contribution: ITreeStructureContribution): Any? {
        return try {
            contribution.getParentElement(element)
        } catch (e: java.lang.Exception) {
            logger<TreeStructure>().warn("Could not get parent for element $element", e)
            null
        }
    }

    override fun isAlwaysLeaf(element: Any): Boolean {
        return false
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*> {
        val descriptor: NodeDescriptor<*>? =
                getValidContributions()
                        .map { it.createDescriptor(element, parent) }
                        .find { it != null }
        if (descriptor != null) {
            return descriptor
        }
        return when(element) {
                is IContext -> ClusterDescriptor(element, parent)
                is Exception -> ErrorDescriptor(element, parent)
                is Folder -> FolderDescriptor(element, parent)
                else -> Descriptor(element, parent)
            }
    }

    private fun getValidContributions(): Collection<ITreeStructureContribution> {
        return contributions
            .filter { it.canContribute() }
    }

    private fun getTreeStructureExtensions(model: IResourceModel): List<ITreeStructureContribution> {
        return extensionPoint.extensionList
                .map { it.create(model) }
    }

    protected open fun getTreeStructureDefaults(model: IResourceModel): List<ITreeStructureContribution> {
        return listOf(
                OpenShiftStructure(model),
                KubernetesStructure(model))
    }

    override fun commit() = Unit

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any) = true

    private class ClusterDescriptor(context: IContext, parent: NodeDescriptor<*>?) : Descriptor<IContext>(
            context,
            parent,
            {
                val label = if (it.context == null
                        || it.context?.context == null) {
                    "<unknown context>"
                } else {
                    var label = ""
                    val namespace = it.context?.context?.namespace
                    if (namespace != null && !namespace.isBlank()) {
                        label += "$namespace/"
                    }
                    val cluster = it.context?.context?.cluster
                    if (cluster != null && !cluster.isBlank()) {
                        label += "$cluster"
                    }
                    val user = it.context?.context?.user
                    user?.apply {
                        if (!user.isBlank()) {
                            label += "/${user.split("/")[0]}"
                        }
                    }
                    if (label.isBlank()) {
                        label += it.context?.name
                    }
                    label
                }
                label
            },
            IconLoader.getIcon("/icons/kubernetes-cluster.svg")
    )

    private class FolderDescriptor(category: Folder, parent: NodeDescriptor<*>?) : Descriptor<Folder>(
        category, parent,
        { it.label }

    ) {
        override fun isMatching(element: Any?): Boolean {
            // change in resource cathegory is notified as change of resource kind
            return this.element.kind == element
        }
    }

    private class ErrorDescriptor(exception: java.lang.Exception, parent: NodeDescriptor<*>?) : Descriptor<java.lang.Exception>(
        exception,
        parent,
        { "Error: " + it.message },
        AllIcons.General.BalloonError
    )

    open class Descriptor<T>(
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

        open fun isMatching(element: Any?): Boolean {
            return this.element == element
        }

        override fun getElement(): T {
            return element;
        }

        open fun invalidate() {
            getResourceModel().invalidate(element)
        }

        protected fun getResourceModel(): IResourceModel {
            return ServiceManager.getService(myProject, IResourceModel::class.java)
        }
    }

    data class Folder(val label: String, val kind: Class<out HasMetadata>?)
}
