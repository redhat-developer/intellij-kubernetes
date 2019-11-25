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
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.KubernetesResourcesModel

class KubernetesTreeStructure : AbstractTreeStructure() {
    private val root = KubernetesResourcesModel.kubeClient;

    override fun getParentElement(element: Any?): Any? {
        return when(element) {
            element === root -> null
            element is NodeDescriptor<*> -> (element as NodeDescriptor<*>).parentDescriptor;
            element is HasMetadata -> (element as HasMetadata).metadata.namespace;
            else -> null;
        }
    }

    override fun getChildElements(element: Any): Array<Any> {
        try {
            if (element == root) {
                return KubernetesResourcesModel.getNamespaces().toTypedArray();
            }
            return emptyArray();
        } catch(e: KubernetesClientException) {
            return arrayOf(e);
        }
    }

    override fun commit() {
    }

    override fun getRootElement(): Any {
        return root;
    }

    override fun hasSomethingToCommit(): Boolean {
        return false;
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
            presentation?.presentableText = (element as java.lang.Exception).message;
            presentation?.setIcon(AllIcons.General.BalloonError)
        }
    }

    open class KubernetesNode(element: Any, parentNode: NodeDescriptor<*>?): PresentableNodeDescriptor<Any>(null, parentNode) {

        override fun update(presentation: PresentationData?) {
            presentation?.presentableText = element.toString();
        }

        private val element = element;

        override fun getElement(): Any {
            return element;
        }
    }
}
