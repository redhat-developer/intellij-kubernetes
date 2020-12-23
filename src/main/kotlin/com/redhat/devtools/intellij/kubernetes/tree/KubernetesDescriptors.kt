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

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.SimpleTextAttributes
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.CronJob
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.storage.StorageClass
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.KubernetesContext
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericResource
import com.redhat.devtools.intellij.kubernetes.model.util.getContainers
import com.redhat.devtools.intellij.kubernetes.model.util.getVersion
import com.redhat.devtools.intellij.kubernetes.model.util.isRunning
import com.redhat.devtools.intellij.kubernetes.tree.AbstractTreeStructureContribution.DescriptorFactory
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourceDescriptor
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourcePropertyDescriptor
import javax.swing.Icon

object KubernetesDescriptors {

	fun createDescriptor(element: Any, parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<*>? {
		return when (element) {
			is DescriptorFactory<*> -> element.create(parent, model)

			is KubernetesContext -> KubernetesContextDescriptor(element, model)
			is Namespace -> NamespaceDescriptor(element, parent, model)
			is Node -> ResourceDescriptor(element, parent, model)
			is Pod -> PodDescriptor(element, parent, model)

			is Deployment,
			is StatefulSet,
			is DaemonSet,
			is Job,
			is CronJob,
			is Service,
			is Endpoints,
			is Ingress,
			is PersistentVolume,
			is PersistentVolumeClaim,
			is StorageClass,
			is ConfigMap,
			is Secret,
			is GenericResource ->
				ResourceDescriptor(element as HasMetadata, parent, model)
			is CustomResourceDefinition ->
				CustomResourceDefinitionDescriptor(element, parent, model)
			else ->
				null
		}
	}

	private class KubernetesContextDescriptor(
			element: KubernetesContext,
			model: IResourceModel)
		: TreeStructure.ContextDescriptor<KubernetesContext>(
			context = element,
			model = model
	) {
		override fun getIcon(element: KubernetesContext): Icon? {
			return IconLoader.getIcon("/icons/kubernetes-cluster.svg")
		}
	}

	private class NamespaceDescriptor(
			element: Namespace,
			parent: NodeDescriptor<*>?,
			model: IResourceModel)
		: ResourceDescriptor<Namespace>(
			element,
			parent,
			model
	) {
		override fun getLabel(element: Namespace): String {
			var label = element.metadata.name
			if (label == model.getCurrentNamespace()) {
				label = "* $label"
			}
			return label
		}

		override fun getIcon(element: Namespace): Icon? {
			return IconLoader.getIcon("/icons/project.png")
		}
	}

	private class PodDescriptor(pod: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
		: ResourceDescriptor<Pod>(
			pod,
			parent,
			model
	) {
		override fun getLabel(element: Pod): String {
			return element.metadata.name
		}

		override fun getIcon(element: Pod): Icon? {
			return if (element.isRunning()) {
				IconLoader.getIcon("/icons/runningPod.svg")
			} else {
				IconLoader.getIcon("/icons/errorPod.svg")
			}
		}
	}

	private class CustomResourceDefinitionDescriptor(
			definition: CustomResourceDefinition,
			parent: NodeDescriptor<*>?,
			model: IResourceModel)
		: ResourceDescriptor<CustomResourceDefinition>(
			definition,
			parent,
			model
	) {
		override fun getLabel(element: CustomResourceDefinition): String {
			return when {
				element.spec.names.plural.isNotBlank() -> element.spec.names.plural
				else -> element.metadata.name
			}
		}

		override fun postprocess(presentation: PresentationData) {
			if (element == null) {
				return
			}
			presentation.addText(getLabel(element!!), SimpleTextAttributes.REGULAR_ATTRIBUTES)
			presentation.addText(" (${element!!.spec.group}/${getVersion(element!!.spec)})", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
		}

		override fun watchResources() {
			if (element == null) {
				return
			}
			model.watch(element!!)
		}
	}

	fun createPodDescriptorFactories(pod: Pod)
			: List<DescriptorFactory<Pod>> {
		return listOf(
			PodContainersDescriptorFactory(pod),
			PodIpDescriptorFactory(pod))
	}

	class PodContainersDescriptorFactory(pod: Pod) : AbstractTreeStructureContribution.DescriptorFactory<Pod>(pod) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<Pod>? {
			return PodContainersDescriptor(resource, parent, model)
		}

		private class PodContainersDescriptor(element: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
			: ResourcePropertyDescriptor<Pod>(
				element,
				parent,
				model
		) {
			override fun getLabel(element: Pod): String {
				val total = element.getContainers().size
				val ready = element.getContainers().filter { it.ready }.size
				val state = element.status.phase
				return "$state ($ready/$total)"
			}
		}
	}

	class PodIpDescriptorFactory(pod: Pod) : AbstractTreeStructureContribution.DescriptorFactory<Pod>(pod) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<Pod>? {
			return PodIpDescriptor(resource, parent, model)
		}

		private class PodIpDescriptor(element: Pod, parent: NodeDescriptor<*>?, model: IResourceModel)
			: ResourcePropertyDescriptor<Pod>(
				element,
				parent,
				model
		) {
			override fun getLabel(element: Pod): String {
				return element.status?.podIP ?: "<No IP>"
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


		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<R>? {
			return ConfigMapDataDescriptor(key, resource, parent, model)
		}

		private class ConfigMapDataDescriptor<R : HasMetadata>(
				private val key: String,
				element: R,
				parent: NodeDescriptor<*>?,
				model: IResourceModel
		) : ResourcePropertyDescriptor<R>(
				element,
				parent,
				model
		) {
			override fun getLabel(element: R): String {
				return key
			}
		}
	}

	private class EmptyDataDescriptorFactory<R : HasMetadata>(resource: R)
		: AbstractTreeStructureContribution.DescriptorFactory<R>(resource) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<R>? {
			return ConfigMapDataDescriptor(resource, parent, model)
		}

		private class ConfigMapDataDescriptor<R : HasMetadata>(
				element: R,
				parent: NodeDescriptor<*>?,
				model: IResourceModel
		) : ResourcePropertyDescriptor<R>(
				element,
				parent,
				model
		) {
			override fun getLabel(element: R): String {
				return "no data entries"
			}
		}
	}

}