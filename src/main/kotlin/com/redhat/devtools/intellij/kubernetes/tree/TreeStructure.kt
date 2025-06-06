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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.hasDeletionTimestamp
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.isWillBeDeleted
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.HasMetadata
import javax.swing.Icon

/**
 * A factory that creates nodes (PresentableNodeDescriptor) for a (tree-) model.
 *
 * @see PresentableNodeDescriptor
 * @see AbstractTreeStructure
 * @see MultiParentTreeStructure
 */
open class TreeStructure(
        private val project: Project,
        private val model: IResourceModel,
        private val extensionPoint: ExtensionPointName<ITreeStructureContributionFactory> =
                ExtensionPointName("com.redhat.devtools.intellij.kubernetes.structureContribution"))
    : AbstractTreeStructure(), MultiParentTreeStructure {

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
            rootElement -> getAllContexts()
            else -> getValidContributions()
                .flatMap { getChildElements(element, it) }
                .toTypedArray()
        }
    }

    private fun getAllContexts(): Array<Any> {
        return try {
            model.getAllContexts().toTypedArray()
        } catch (e: Exception) {
            arrayOf(e)
        }
    }

    private fun getChildElements(element: Any, contribution: ITreeStructureContribution): Collection<Any> {
        return try {
            contribution.getChildElements(element)
        } catch (e: Exception) {
            logger<TreeStructure>().warn(e)
            listOf(e)
        }
    }

    override fun getParentElement(element: Any): Any? {
        return getValidContributions().stream()
            .map { contribution -> getParentElement(element, contribution) }
            .filter { parentElement -> parentElement != null }
            .findAny()
            .orElse(null)
    }

    private fun getParentElement(element: Any, contribution: ITreeStructureContribution): Any? {
        return try {
            contribution.getParentElement(element)
        } catch (e: java.lang.Exception) {
            logger<TreeStructure>().warn("Could not get parent for element $element", e)
            null
        }
    }

    override fun isParentDescriptor(descriptor: NodeDescriptor<*>?, element: Any): Boolean {
        return getValidContributions()
            .any { contribution ->
                descriptor is Descriptor<*>
                        && contribution.isParentDescriptor(descriptor, element) }
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*> {
        return try {
            val descriptor: NodeDescriptor<*>? = getValidContributions()
                    .map { it.createDescriptor(element, parent, project) }
                    .find { it != null }
            descriptor ?: when (element) {
                    is IContext -> ContextDescriptor(element, parent, model, project)
                    is Exception -> ErrorDescriptor(element, parent, model, project)
                    is Folder -> FolderDescriptor(element, parent, model, project)
                    else -> Descriptor(element, null, parent, model, project)
                }
        } catch (e: Exception) {
            ErrorDescriptor(e, parent, model, project)
        }
    }

    private fun getValidContributions(): Collection<ITreeStructureContribution> {
        return contributions
            .filter {
                try {
                    it.canContribute()
                } catch (e: Exception) {
                    false
                }
            }
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

    override fun isAlwaysLeaf(element: Any): Boolean {
        return false
    }

    override fun getLeafState(element: Any): LeafState {
        return if (element is IContext
                && element !is IActiveContext<*, *>) {
            LeafState.ALWAYS
        } else if (element is Exception) {
            LeafState.ALWAYS
        } else {
            val leafState = contributions.find { it.getLeafState(element) != null }?.getLeafState(element)
            return leafState ?: LeafState.NEVER
        }
    }

    open class ContextDescriptor<C : IContext>(
        context: C,
        parent: NodeDescriptor<*>? = null,
        model: IResourceModel,
        project: Project
    ) : Descriptor<C>(
        context,
        null,
        parent,
        model,
        project
    ) {
        override fun getLabel(element: C?): String {
            return element?.name ?: "<unknown context>"
        }

        override fun getIcon(element: C): Icon? {
            return KubernetesDescriptors.KUBERNETES_CLUSTER_ICON
        }
    }

    open class ResourcePropertyDescriptor<T>(
        element: T,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : Descriptor<T>(
        element,
        null,
        parent,
        model,
        project
    ) {
        /**
         * Returns {@code null} so that resource property descriptor has no children
         *
         * @see KubernetesStructure#createPodElements
         */
        override fun getElement(): T? {
            return null
        }
    }

    open class FolderDescriptor(
        element: Folder,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : Descriptor<Folder>(
        element,
        element.kind,
        parent,
        model,
        project
    ) {
        override fun hasElement(element: Any?): Boolean {
            // change in resource category is notified as change of resource kind
            return this.element?.kind == element
        }

        override fun setElement(element: Any): Boolean {
            return if (element is ResourceKind<*>) {
                val current = this.element ?: return false
                super.setElement(Folder(current, element))
            } else {
                false
            }
        }

        override fun invalidate() {
            model.invalidate(element?.kind)
        }

        override fun getLabel(element: Folder?): String {
            return element?.label ?: "<unknown folder>"
        }
    }

    private class ErrorDescriptor(
        exception: Exception,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : Descriptor<Exception>(
        exception,
        null,
        parent,
        model,
        project
    ) {
        override fun getLabel(element: Exception?): String {
            return toMessage(element)
        }

        override fun getIcon(element: Exception): Icon {
            return AllIcons.General.BalloonError
        }
    }

    open class ResourceDescriptor<T : HasMetadata>(
        element: T,
        childrenKind: ResourceKind<out HasMetadata>?,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : Descriptor<T>(element, childrenKind, parent, model, project) {

        override fun getLabel(element: T?): String {
            return element?.metadata?.name ?: "<unknown resource>"
        }

        override fun update(presentation: PresentationData) {
            super.update(presentation)
            when {
                isWillBeDeleted(element) -> {
                    presentation.presentableText += " (deleted)"
                    presentation.setAttributesKey(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
                }
                hasDeletionTimestamp(element) -> {
                    presentation.presentableText += " (terminating)"
                    presentation.setAttributesKey(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
                }
            }
        }

        override fun hasElement(element: Any?): Boolean {
            return if (element is HasMetadata) {
                this.element?.isSameResource(element) ?: false
            } else {
                super.hasElement(element)
            }
        }
    }

    open class Descriptor<T>(
            private var element: T,
            val childrenKind: ResourceKind<out HasMetadata>?,
            parent: NodeDescriptor<*>?,
            protected val model: IResourceModel,
            project: Project
    ) : PresentableNodeDescriptor<T>(project, parent) {

        override fun update(presentation: PresentationData) {
            updateLabel(getLabel(element), presentation)
            updateSubLabel(getSubLabel(element), presentation)
            updateIcon(getIcon(element), presentation)
        }

        private fun updateLabel(label: String?, presentation: PresentationData) {
            presentation.addText(label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }

        protected open fun updateSubLabel(subLabel: String?, presentation: PresentationData) {
            if (subLabel.isNullOrEmpty()) {
                return
            }
            presentation.addText(" $subLabel", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        open fun hasElement(element: Any?): Boolean {
            return this.element == element
        }

        open fun setElement(element: Any): Boolean {
            @Suppress("UNCHECKED_CAST")
            val typed: T = element as? T ?: return false
            this.element = typed
            return true
        }

        override fun getElement(): T? {
            return element
        }

        open fun invalidate() {
            model.invalidate(element)
        }

        protected open fun getLabel(element: T?): String {
            return element?.toString() ?: "<unknown element>"
        }

        protected open fun getSubLabel(element: T): String? {
            return null
        }

        protected open fun getIcon(element: T): Icon? {
            return null
        }

        private fun updateIcon(icon: Icon?, presentation: PresentationData) {
            if (icon != null) {
                presentation.setIcon(icon)
            }
        }

        open fun watchChildren() {
            val kind = childrenKind ?: return
            model.watch(kind)
        }

        open fun stopWatchChildren() {
            val kind = childrenKind ?: return
            model.stopWatch(kind)
        }

        override fun toString(): String {
            return getLabel(element)
        }
    }

    open class Folder(val label: String, val kind: ResourceKind<out HasMetadata>?) {
        constructor(folder: Folder, kind: ResourceKind<out HasMetadata>?) : this(folder.label, kind)
    }
}


