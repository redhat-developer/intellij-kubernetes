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

class KubernetesTreeStructure : AbstractTreeStructure() {
    override fun getParentElement(element: Any?): Any? {
        return when(element) {
            element == rootElement -> null
            element is NodeDescriptor<*> -> (element as NodeDescriptor<*>).parentDescriptor;
            element is HasMetadata -> (element as HasMetadata).metadata.namespace;
            else -> null;
        }
    }

    override fun getChildElements(element: Any): Array<Any> {
        try {
            return when(element) {
                rootElement ->
                    KubernetesResourceModel.getNamespaces().toTypedArray()
                is Namespace ->
                    KubernetesResourceModel.getPods(element.metadata.name).toTypedArray()
                else ->
                    emptyArray()
            }
        } catch(e: RuntimeException) {
            return arrayOf(e)
        }
    }

    override fun commit() = Unit

    override fun getRootElement() = KubernetesResourceModel.getCluster()

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any?): Boolean {
        return true
    }

    override fun isAlwaysLeaf(element: Any?): Boolean {
        return false
    }

    override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> {
        return when(element) {
            is NamespacedKubernetesClient -> ClusterNode(element)
            is HasMetadata -> HasMetaDataNode(element)
            is Exception -> ErrorNode(element)
            else -> KubernetesNode(element, parentDescriptor);
        }
    }

    class ClusterNode(element: NamespacedKubernetesClient): KubernetesNode(element, null) {
        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = (element as NamespacedKubernetesClient).masterUrl.toString()
            presentation?.setIcon(IconLoader.getIcon("/icons/kubernetes-cluster.svg"))
        }
    }

    class HasMetaDataNode(element: HasMetadata): KubernetesNode(element, null) {
        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = ((element as HasMetadata).metadata.name);
            presentation?.setIcon(IconLoader.getIcon("/icons/project.png"))
        }
    }

    class ErrorNode(element: java.lang.Exception): KubernetesNode(element, null) {

        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = "Error: " + (element as java.lang.Exception).message;
            presentation?.setIcon(AllIcons.General.BalloonError)
        }
    }

    open class KubernetesNode(element: Any, parentNode: NodeDescriptor<*>?): PresentableNodeDescriptor<Any>(null, parentNode) {

        private val element = element;

        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = element.toString();
        }
        override fun getElement(): Any {
            return element;
        }

    }
}
