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
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.cluster.OpenShiftCluster
import org.jboss.tools.intellij.kubernetes.model.resource.ProjectsProvider

class OpenShiftStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val PROJECTS = TreeStructure.Folder("Projects", Project::class.java)
    }

    override fun canContribute(): Boolean {
        return model.currentCluster?.isOpenShift() ?: false
    }

    override fun getChildElements(element: Any): Collection<Any> {
        return when (element) {
            getRootElement() ->
                listOf(PROJECTS)
            PROJECTS ->
                model.getResources(ProjectsProvider.KIND)
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
                else ->
                    getRootElement()
            }
        } catch(e: ResourceException) {
            return null
        }
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*>? {
        return when(element) {
            is OpenShiftCluster -> OpenShiftClusterDescriptor(element)
            is Project -> ProjectDescriptor(element, model, parent)
            else -> null
        }
    }

    private class OpenShiftClusterDescriptor(element: OpenShiftCluster) : TreeStructure.Descriptor<OpenShiftCluster>(
        element, null,
        { element.client.masterUrl.toString() },
        IconLoader.getIcon("/icons/openshift-cluster.svg")
    )

    private class ProjectDescriptor(element: Project, model: IResourceModel, parent: NodeDescriptor<*>?) : TreeStructure.Descriptor<Project>(
        element,
        parent,
        {
            var label = element.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            label
        },
        IconLoader.getIcon("/icons/project.png")
    )

}