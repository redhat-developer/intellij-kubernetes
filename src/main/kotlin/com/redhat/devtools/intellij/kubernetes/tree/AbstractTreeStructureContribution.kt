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
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.LeafState
import io.fabric8.kubernetes.api.model.HasMetadata
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind

abstract class AbstractTreeStructureContribution(override val model: IResourceModel): ITreeStructureContribution {

    protected fun getRootElement(): Any? {
        return model.getCurrentContext()
    }

    fun <T> element(initializer: ElementNode<T>.() -> Unit): ElementNode<T> {
        return ElementNode<T>().apply(initializer)
    }

    override fun getLeafState(element: Any): LeafState? {
        return null
    }

    class ElementNode<T> {

        private lateinit var applicableExpression: (element: Any) -> Boolean
        private var childrenKind: ResourceKind<out HasMetadata>? = null
        private var childElementsProvider: ((element: T) -> Collection<Any>)? = null

        fun applicableIf(provider: (element: Any) -> Boolean): ElementNode<T> {
            this.applicableExpression = provider
            return this
        }

        fun childrenKind(provider: () -> ResourceKind<out HasMetadata>): ElementNode<T> {
            this.childrenKind = provider.invoke()
            return this
        }

        fun children(provider: (element: T) -> Collection<Any>): ElementNode<T> {
            this.childElementsProvider = provider
            return this
        }

        fun isApplicableFor(element: Any): Boolean {
            return applicableExpression.invoke(element)
        }

        fun getChildrenKind(): ResourceKind<out HasMetadata>? {
            return childrenKind
        }

        fun getChildElements(element: Any): Collection<Any> {
            @Suppress("UNCHECKED_CAST")
            val typedElement = element as? T ?: return emptyList()
            return childElementsProvider?.invoke(typedElement) ?: return emptyList()
        }
    }

    abstract class DescriptorFactory<R : HasMetadata>(protected val resource: R) {
        abstract fun create(parent: NodeDescriptor<*>?, model: IResourceModel, project: Project): NodeDescriptor<R>?
    }
}
