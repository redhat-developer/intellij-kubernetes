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
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.tree.LeafState
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.Project
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.context.OpenShiftContext
import com.redhat.devtools.intellij.kubernetes.model.resource.BuildConfigFor
import com.redhat.devtools.intellij.kubernetes.model.resource.BuildFor
import com.redhat.devtools.intellij.kubernetes.model.resource.DeploymentConfigFor
import com.redhat.devtools.intellij.kubernetes.model.resource.ReplicationControllerFor
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.DeploymentConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ImageStreamsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import com.redhat.devtools.intellij.kubernetes.model.resourceName
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourceDescriptor
import javax.swing.Icon

class OpenShiftStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val PROJECTS = Folder("Projects", ProjectsOperator.KIND)
        val IMAGESTREAMS = Folder("ImageStreams", ImageStreamsOperator.KIND)
        val DEPLOYMENTCONFIGS = Folder("DeploymentConfigs", DeploymentConfigsOperator.KIND)
        val BUILDCONFIGS = Folder("BuildConfigs", BuildConfigsOperator.KIND)
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
                        DEPLOYMENTCONFIGS,
                        BUILDCONFIGS)
            is DeploymentConfig ->
                model.resources(ReplicationControllersOperator.KIND)
                        .inCurrentNamespace()
                        .filtered(ReplicationControllerFor(element))
                        .list()
                        .sortedBy(resourceName)
            is BuildConfig ->
                model.resources(BuildsOperator.KIND)
                        .inCurrentNamespace()
                        .filtered(BuildFor(element))
                        .list()
                        .sortedBy(resourceName)
            PROJECTS ->
                model.resources(ProjectsOperator.KIND)
                        .inNoNamespace()
                        .list()
                        .sortedBy(resourceName)
            IMAGESTREAMS ->
                model.resources(ImageStreamsOperator.KIND)
                        .inCurrentNamespace()
                        .list()
            DEPLOYMENTCONFIGS ->
                model.resources(DeploymentConfigsOperator.KIND)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
            BUILDCONFIGS ->
                model.resources(BuildConfigsOperator.KIND)
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
                    model.resources(DeploymentConfigsOperator.KIND)
                            .inCurrentNamespace()
                            .filtered(DeploymentConfigFor(element))
                            .list()
                            .sortedBy(resourceName)
                is BuildConfig ->
                    BuildConfigsOperator
                BUILDCONFIGS ->
                    WORKLOADS
                is Build ->
                    model.resources(BuildConfigsOperator.KIND)
                            .inCurrentNamespace()
                            .filtered(BuildConfigFor(element))
                            .list()
                            .sortedBy(resourceName)
                else ->
                    // fallback to (calling) tree structure
                    null
            }
        } catch(e: ResourceException) {
            return null
        }
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?, project: com.intellij.openapi.project.Project
    ): NodeDescriptor<*>? {
        return when(element) {
            is OpenShiftContext -> OpenShiftContextDescriptor(element, model, project)
            is Project -> ProjectDescriptor(element, parent, model, project)
            is ImageStream,
            is DeploymentConfig,
            is ReplicationController,
            is BuildConfig,
            is Build -> ResourceDescriptor(element as HasMetadata, parent, model, project)
            else -> null
        }
    }

    private class OpenShiftContextDescriptor(
        context: OpenShiftContext,
        model: IResourceModel,
        project: com.intellij.openapi.project.Project
    ) : TreeStructure.ContextDescriptor<OpenShiftContext>(
        context = context,
        model = model,
        project = project
    ) {
        override fun getIcon(element: OpenShiftContext): Icon? {
            return IconLoader.getIcon("/icons/openshift-cluster.svg", javaClass)
        }
    }

    private class ProjectDescriptor(
        element: Project,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: com.intellij.openapi.project.Project
    ) : ResourceDescriptor<Project>(element, parent, model, project) {

        override fun getLabel(element: Project): String {
            var label = element.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            return label
        }
    }

    override fun getLeafState(element: Any): LeafState? {
        return when(element) {
            is Project -> LeafState.ALWAYS
            else -> null
        }
    }

}