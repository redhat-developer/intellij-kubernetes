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
import com.intellij.openapi.util.IconLoader
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.KubernetesContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.getHighestPriorityVersion
import com.redhat.devtools.intellij.kubernetes.tree.AbstractTreeStructureContribution.DescriptorFactory
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.NamespacesFolder
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ContextDescriptor
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.FolderDescriptor
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourceDescriptor
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourcePropertyDescriptor
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.kubernetes.client.utils.PodStatusUtil
import javax.swing.Icon

object KubernetesDescriptors {

	fun createDescriptor(element: Any, childrenKind: ResourceKind<out HasMetadata>?, parent: NodeDescriptor<*>?, model: IResourceModel, project: Project): NodeDescriptor<*>? {
		return when {
			element is DescriptorFactory<*> ->
				element.create(parent, model, project)

			element is NamespacesFolder
					&& !isOpenShift(model) ->
				NamespacesFolderDescriptor(element, parent, model, project)

			element is KubernetesContext ->
				KubernetesContextDescriptor(element, model, project)
			element is Namespace ->
				NamespaceDescriptor(element, parent, model, project)
			element is Node ->
				ResourceDescriptor(element, childrenKind, parent, model, project)
			element is Pod ->
				PodDescriptor(element, parent, model, project)

			element is Deployment
					|| element is StatefulSet
					|| element is DaemonSet
					|| element is Job
					|| element is CronJob
					|| element is Service
					|| element is Endpoints
					|| element is Ingress
					|| element is PersistentVolume
					|| element is PersistentVolumeClaim
					|| element is StorageClass
					|| element is ConfigMap
					|| element is Secret
					|| element is GenericKubernetesResource
					|| element is ReplicaSet
					|| element is ReplicationController ->
					ResourceDescriptor(element as HasMetadata, childrenKind, parent, model, project)
			element is CustomResourceDefinition ->
				CustomResourceDefinitionDescriptor(element, parent, model, project)

			else ->
				null
		}
	}

	private fun isOpenShift(model: IResourceModel): Boolean {
		return true == model.getCurrentContext()?.isOpenShift()
	}

	private class KubernetesContextDescriptor(
		element: KubernetesContext,
		model: IResourceModel,
		project: Project
	) : ContextDescriptor<KubernetesContext>(
		context = element,
		model = model,
		project = project
	) {
		override fun getIcon(element: KubernetesContext): Icon {
			return IconLoader.getIcon("/icons/kubernetes-cluster.svg", javaClass)
		}
	}

	private class NamespacesFolderDescriptor(
		element: NamespacesFolder,
		parent: NodeDescriptor<*>?,
		model: IResourceModel,
		project: Project
	) : FolderDescriptor(
		element,
		parent,
		model,
		project
	) {
		override fun getSubLabel(element: Folder): String {
			val current = model.getCurrentNamespace()
			return "current: ${
				if (current.isNullOrEmpty()) {
					"<none>"
				} else {
					current
				}
			}"
		}
	}

	private class NamespaceDescriptor(
		element: Namespace,
		parent: NodeDescriptor<*>?,
		model: IResourceModel,
		project: Project
	) : ResourceDescriptor<Namespace>(
		element,
		null,
		parent,
		model,
		project
	) {
		override fun getLabel(element: Namespace?): String {
			var label = element?.metadata?.name
			if (label == model.getCurrentNamespace()) {
				label = "* $label"
			}
			return label ?: "unknown"
		}

		override fun getIcon(element: Namespace): Icon {
			return IconLoader.getIcon("/icons/namespace.svg", javaClass)
		}
	}

	private class PodDescriptor(
		pod: Pod, parent: NodeDescriptor<*>?, model: IResourceModel,
		project: Project
	) : ResourceDescriptor<Pod>(
		pod,
		null,
		parent,
		model,
		project
	) {
		override fun getLabel(element: Pod?): String {
			return element?.metadata?.name ?: "unknown"
		}

		override fun getIcon(element: Pod): Icon {
			return if (PodStatusUtil.isRunning(element)) {
				IconLoader.getIcon("/icons/running-pod.svg", javaClass)
			} else {
				IconLoader.getIcon("/icons/error-pod.svg", javaClass)
			}
		}
	}

	private class CustomResourceDefinitionDescriptor(
		definition: CustomResourceDefinition,
		parent: NodeDescriptor<*>?,
		model: IResourceModel,
		project: Project
	) : ResourceDescriptor<CustomResourceDefinition>(
		definition,
		ResourceKind.create(definition.spec),
		parent,
		model,
		project
	) {
		override fun getLabel(element: CustomResourceDefinition?): String {
			return when {
				element == null ->
					"unknown"
				element.spec?.names?.plural?.isNotBlank()  ?: false ->
					element.spec!!.names!!.plural
				else ->
					element.metadata.name
			}
		}

		override fun getSubLabel(element: CustomResourceDefinition): String? {
			return " (${element.spec.group}/${getHighestPriorityVersion(element.spec)})"
		}

		override fun watchChildren() {
			val toWatch = element ?: return
			model.watch(toWatch)
		}
	}

	fun createPodDescriptorFactories(pod: Pod)
			: List<DescriptorFactory<Pod>> {
		return listOf(
			PodContainersDescriptorFactory(pod),
			PodIpDescriptorFactory(pod))
	}

	class PodContainersDescriptorFactory(pod: Pod) : DescriptorFactory<Pod>(pod) {

		override fun create(
			parent: NodeDescriptor<*>?, model: IResourceModel,
			project: Project
		): NodeDescriptor<Pod> {
			return PodContainersDescriptor(resource, parent, model, project)
		}

		private class PodContainersDescriptor(
			element: Pod, parent: NodeDescriptor<*>?, model: IResourceModel,
			project: Project
		) : ResourcePropertyDescriptor<Pod>(
			element,
			parent,
			model,
			project
		) {
			override fun getLabel(element: Pod?): String {
				val total = PodStatusUtil.getContainerStatus(element).size
				val ready = PodStatusUtil.getContainerStatus(element).filter { it.ready }.size
				val state = element?.status?.phase ?: "unknown"
				return "$state ($ready/$total)"
			}
		}
	}

	class PodIpDescriptorFactory(pod: Pod) : DescriptorFactory<Pod>(pod) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel, project: Project): NodeDescriptor<Pod> {
			return PodIpDescriptor(resource, parent, model, project)
		}

		private class PodIpDescriptor(
			element: Pod,
			parent: NodeDescriptor<*>?,
			model: IResourceModel,
			project: Project
		) :
			ResourcePropertyDescriptor<Pod>(
				element,
				parent,
				model,
				project
			) {
			override fun getLabel(element: Pod?): String {
				return element?.status?.podIP ?: "<No IP>"
			}
		}
	}

	fun <R : HasMetadata> createDataDescriptorFactories(data: Map<String, String>?, element: R)
			: List<DescriptorFactory<R>> {
		return if (data == null
				|| data.isEmpty()) {
			listOf(EmptyDataDescriptorFactory(element))
		} else {
			data.keys.map { DataEntryDescriptorFactory(it, element) }
		}
	}

	private class DataEntryDescriptorFactory<R : HasMetadata>(private val key: String, resource: R)
		: DescriptorFactory<R>(resource) {


		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel, project: Project): NodeDescriptor<R> {
			return ConfigMapDataDescriptor(key, resource, parent, model, project)
		}

		private class ConfigMapDataDescriptor<R : HasMetadata>(
			private val key: String,
			element: R,
			parent: NodeDescriptor<*>?,
			model: IResourceModel,
			project: Project
		) : ResourcePropertyDescriptor<R>(
			element,
			parent,
			model,
			project
		) {
			override fun getLabel(element: R?): String {
				return key
			}
		}
	}

	private class EmptyDataDescriptorFactory<R : HasMetadata>(resource: R)
		: DescriptorFactory<R>(resource) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel,
							project: Project): NodeDescriptor<R> {
			return ConfigMapDataDescriptor(resource, parent, model, project)
		}

		private class ConfigMapDataDescriptor<R : HasMetadata>(
			element: R,
			parent: NodeDescriptor<*>?,
			model: IResourceModel,
			project: Project
		) : ResourcePropertyDescriptor<R>(
			element,
			parent,
			model,
			project
		) {
			override fun getLabel(element: R?): String {
				return "no data entries"
			}
		}
	}

}