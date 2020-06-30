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
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.Project
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.OpenShiftContext
import org.jboss.tools.intellij.kubernetes.model.resource.DeploymentConfigFor
import org.jboss.tools.intellij.kubernetes.model.resource.ReplicationControllerFor
import org.jboss.tools.intellij.kubernetes.model.resourceName
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.Folder
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.ResourceDescriptor
import javax.swing.Icon

class OpenShiftStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val PROJECTS = Folder("Projects", Project::class.java)
        val IMAGESTREAMS = Folder("ImageStreams", ImageStream::class.java)
        val DEPLOYMENTCONFIGS = Folder("DeploymentConfigs", DeploymentConfig::class.java)
    }

    override fun canContribute(): Boolean {
        return model.getCurrentContext()?.isOpenShift() ?: false
    }

    override fun getChildElements(element: Any): Collection<Any> {
        return when (element) {
            getRootElement() ->
                listOf(PROJECTS)
            WORKLOADS ->
                listOf<Any>(
                        IMAGESTREAMS,
                        DEPLOYMENTCONFIGS)
            is DeploymentConfig ->
                model.resources(ReplicationController::class.java)
                        .inCurrentNamespace()
                        .filtered(ReplicationControllerFor(element))
                        .list()
                        .sortedBy(resourceName)
            PROJECTS ->
                model.resources(Project::class.java)
                        .inNoNamespace()
                        .list()
                        .sortedBy(resourceName)
            IMAGESTREAMS ->
                model.resources(ImageStream::class.java)
                        .inCurrentNamespace()
                        .list()
            DEPLOYMENTCONFIGS ->
                model.resources(DeploymentConfig::class.java)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
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
                is Node ->
                    NODES
                NODES ->
                    getRootElement()
                is ImageStream ->
                    IMAGESTREAMS
                IMAGESTREAMS ->
                    WORKLOADS
                is DeploymentConfig ->
                    DEPLOYMENTCONFIGS
                DEPLOYMENTCONFIGS ->
                    WORKLOADS
                is ReplicationController ->
                    model.resources(DeploymentConfig::class.java)
                            .inCurrentNamespace()
                            .filtered(DeploymentConfigFor(element))
                            .list()
                            .sortedBy(resourceName)
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
            is ImageStream,
            is DeploymentConfig,
            is ReplicationController -> ResourceDescriptor(element as HasMetadata, parent, model)
            else -> null
        }
    }

    private class OpenShiftContextDescriptor(context: OpenShiftContext, model: IResourceModel)
        : TreeStructure.ContextDescriptor<OpenShiftContext>(
            context = context,
            model = model
    ) {
        override fun getIcon(element: OpenShiftContext): Icon? {
            return IconLoader.getIcon("/icons/openshift-cluster.svg")
        }
    }

    private class ProjectDescriptor(
            element: Project,
            parent: NodeDescriptor<*>?,
            model: IResourceModel)
        : ResourceDescriptor<Project>(element, parent, model) {

        override fun getLabel(element: Project): String {
            var label = element.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            return label
        }
    }
}