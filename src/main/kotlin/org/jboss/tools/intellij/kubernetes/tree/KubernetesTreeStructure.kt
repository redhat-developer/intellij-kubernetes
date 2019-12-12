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
import com.intellij.openapi.util.IconLoader
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourceModel
import org.jboss.tools.intellij.kubernetes.model.PodsProvider

class KubernetesTreeStructure : AbstractTreeStructure() {
    override fun getParentElement(element: Any?): Any? {
        try {
            return when (element) {
                rootElement ->
                    null
                is Namespace ->
                    rootElement
                is HasMetadata ->
                    KubernetesResourceModel.getNamespace(element.metadata.namespace)
                else ->
                    rootElement
            }
        } catch(e: java.lang.RuntimeException) {
            return arrayOf(ErrorDescriptor(e))
        }
    }

    override fun getChildElements(element: Any): Array<Any> {
        try {
            return when(element) {
                rootElement ->
                    KubernetesResourceModel.getAllNamespaces().toTypedArray()
                is Namespace ->
                    KubernetesResourceModel.getResources(element.metadata.name, PodsProvider.KIND).toTypedArray()
                else ->
                    emptyArray()
            }
        } catch(e: RuntimeException) {
            return arrayOf(ErrorDescriptor(e))
        }
    }

    override fun commit() = Unit

    override fun getRootElement() = KubernetesResourceModel.getClient()

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any?): Boolean {
        return true
    }

    override fun isAlwaysLeaf(element: Any?): Boolean {
        return false
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*> {
        return when(element) {
            is NamespacedKubernetesClient -> ClusterDescriptor(element)
            is Namespace -> NamespaceDescriptor(element, parent)
            is HasMetadata -> HasMetadataDescriptor(element, parent)
            is Exception -> ErrorDescriptor(element)
            else -> ResourceDescriptor<Any>(element, parent);
        }
    }

    class ClusterDescriptor(element: NamespacedKubernetesClient): ResourceDescriptor<NamespacedKubernetesClient>(element, null) {
        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = element.masterUrl.toString()
            presentation?.setIcon(IconLoader.getIcon("/icons/kubernetes-cluster.svg"))
        }
    }

    class NamespaceDescriptor(element: Namespace, parent: NodeDescriptor<*>?): ResourceDescriptor<Namespace>(element, null) {
        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = element.metadata.name;
            presentation?.setIcon(IconLoader.getIcon("/icons/project.png"))
        }
    }

    class HasMetadataDescriptor(element: HasMetadata, parent: NodeDescriptor<*>?): ResourceDescriptor<HasMetadata>(element, parent) {
        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = element.metadata.name;
            presentation?.setIcon(IconLoader.getIcon("/icons/project.png"))
        }
    }

    class ErrorDescriptor(element: java.lang.Exception): ResourceDescriptor<java.lang.Exception>(element, null) {
        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = "Error: " + element.message;
            presentation?.setIcon(AllIcons.General.BalloonError)
        }
    }

    open class ResourceDescriptor<T>(element: T, parent: NodeDescriptor<*>?): PresentableNodeDescriptor<T>(null, parent) {

        private val element = element;

        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = element.toString();
        }
        override fun getElement(): T {
            return element;
        }

    }
}
