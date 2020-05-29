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
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.Project
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.OpenShiftContext
import org.jboss.tools.intellij.kubernetes.model.resource.openshift.ImageStreamsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.openshift.ProjectsProvider

class OpenShiftStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val PROJECTS = TreeStructure.Folder("Projects", Project::class.java)
        val IMAGESTREAMS = TreeStructure.Folder("ImageStreams", ImageStream::class.java)
    }

    override fun canContribute(): Boolean {
        return model.currentContext?.isOpenShift() ?: false
    }

    override fun getChildElements(element: Any): Collection<Any> {
        return when (element) {
            getRootElement() ->
                listOf(PROJECTS)
            KubernetesStructure.WORKLOADS ->
                listOf<Any>(IMAGESTREAMS)
            PROJECTS,
            IMAGESTREAMS ->
                getResources((element as TreeStructure.Folder).kind)
            else -> emptyList()
        }
    }

    override fun getParentElement(element: Any): Any? {
        try {
            return when (element) {
                getRootElement() ->
                    model
                is Project ->
                    PROJECTS
                PROJECTS ->
                    getRootElement()
                is ImageStream ->
                    IMAGESTREAMS
                IMAGESTREAMS ->
                    KubernetesStructure.WORKLOADS
                else ->
                    getRootElement()
            }
        } catch(e: ResourceException) {
            return null
        }
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*>? {
        return when(element) {
            is OpenShiftContext -> OpenShiftContextDescriptor(element, model)
            is Project -> ProjectDescriptor(element, parent, model)
            is ImageStream -> ImageStreamDescriptor(element, parent, model)
            else -> null
        }
    }

    private class OpenShiftContextDescriptor(context: OpenShiftContext, model: IResourceModel) : TreeStructure.ContextDescriptor<OpenShiftContext>(
        context = context,
        icon = IconLoader.getIcon("/icons/openshift-cluster.svg"),
        model = model
    )

    private class ProjectDescriptor(element: Project, parent: NodeDescriptor<*>?, model: IResourceModel) : TreeStructure.Descriptor<Project>(
            element,
            parent,
            {
                var label = element.metadata.name
                if (label == model.getCurrentNamespace()) {
                    label = "* $label"
                }
                label
            },
            IconLoader.getIcon("/icons/project.png"),
            model
    )

    private class ImageStreamDescriptor(element: ImageStream, parent: NodeDescriptor<*>?, model: IResourceModel) : TreeStructure.Descriptor<ImageStream>(
            element,
            parent,
            { element.metadata.name },
            IconLoader.getIcon("/icons/project.png"),
            model
    )
}