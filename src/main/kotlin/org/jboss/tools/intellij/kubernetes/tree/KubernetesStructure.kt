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
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.storage.StorageClass
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.KubernetesContext
import org.jboss.tools.intellij.kubernetes.model.resource.PodForJob
import org.jboss.tools.intellij.kubernetes.model.resource.PodForService
import org.jboss.tools.intellij.kubernetes.model.resourceName
import org.jboss.tools.intellij.kubernetes.model.util.getContainers
import org.jboss.tools.intellij.kubernetes.model.util.isRunning
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIGURATION
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIG_MAPS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.ENDPOINTS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.INGRESS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NAMESPACES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUMES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUME_CLAIMS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PODS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SECRETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SERVICES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.JOBS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE_CLASSES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.*
import javax.swing.Icon

class KubernetesStructure(model: IResourceModel) : AbstractTreeStructureContribution(model) {
    object Folders {
        val NAMESPACES = Folder("Namespaces", Namespace::class.java)
        val NODES = Folder("Nodes", Node::class.java)
        val WORKLOADS = Folder("Workloads", null)
			val JOBS = Folder("Jobs", Job::class.java) //  Workloads / StatefulSets
            val PODS = Folder("Pods", Pod::class.java) //  Workloads / Pods
        val NETWORK = Folder("Network", null)
            val SERVICES = Folder("Services", Service::class.java) // Network / Services
            val ENDPOINTS = Folder("Endpoints", Endpoints::class.java) // Network / Endpoints
			val INGRESS = Folder("Ingress", Ingress::class.java) // Network / Ingress
        val STORAGE = Folder("Storage", Endpoints::class.java)
            val PERSISTENT_VOLUMES = Folder("Persistent Volumes", PersistentVolume::class.java)
            val PERSISTENT_VOLUME_CLAIMS = Folder("Persistent Volume Claims", PersistentVolumeClaim::class.java)
            val STORAGE_CLASSES = Folder("Storage Classes", StorageClass::class.java)
		val CONFIGURATION = Folder("Configuration", null)
			val CONFIG_MAPS = Folder("Config Maps", ConfigMap::class.java) // Network / Services
			val SECRETS = Folder("Secrets", Secret::class.java) // Network / Endpoints
    }

	private val elementsTree: List<ElementNode<*>> = listOf(
			element<Any> {
				anchor { it == getRootElement() }
				childElements {
					listOf(NAMESPACES,
							NODES,
							WORKLOADS,
							NETWORK,
							STORAGE,
							CONFIGURATION
					)
				}
				parentElements { model }
			},
			*createPodElements(), // pods are in several places (NODES, WORKLOADS, NETWORK, etc)
			*createNamespacesElements(),
			*createNodesElements(),
			*createWorkloadElements(),
			*createNetworkElements(),
			*createStorageElements(),
			*createConfigurationElements()
	)

	override fun getChildElements(element: Any): Collection<Any> {
		val node = elementsTree.find { it.isAnchor(element) }
		return node?.getChildElements(element) ?: emptyList()
	}

	override fun getParentElement(element: Any): Any? {
		try {
			val node = elementsTree.find { it.isAnchor(element) } ?: return getRootElement()
			return node.getParentElements(element)
		} catch (e: ResourceException) {
			return null
		}
	}

	override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*>? {
        return when (element) {
			is KubernetesContext -> KubernetesContextDescriptor(element, model)
			is Namespace -> NamespaceDescriptor(element, parent, model)
			is Node -> ResourceDescriptor(element, parent, model)
			is Pod -> PodDescriptor(element, parent, model)
			is DescriptorFactory<*> -> element.create(parent, model)
			is Job,
            is Service,
            is Endpoints,
            is Ingress,
            is PersistentVolume,
            is PersistentVolumeClaim,
            is StorageClass,
			is ConfigMap,
			is Secret ->
				ResourceDescriptor(element as HasMetadata, parent, model)
			else ->
				null
        }
    }

	override fun canContribute() = true

	private class KubernetesContextDescriptor(element: KubernetesContext, model: IResourceModel) : ContextDescriptor<KubernetesContext>(
			context = element,
			model = model
	) {
		override fun getIcon(element: KubernetesContext): Icon? {
			return IconLoader.getIcon("/icons/kubernetes-cluster.svg")
		}
	}

	private class NamespaceDescriptor(element: Namespace, parent: NodeDescriptor<*>?, model: IResourceModel)
		: Descriptor<Namespace>(
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
		: Descriptor<Pod>(
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

	private fun <R: HasMetadata> createDataDescriptorFactories(data: Map<String, String>?, element: R): List<DescriptorFactory<R>> {
		return if (data == null
				|| data.isEmpty()) {
			listOf(EmptyDataDescriptorFactory(element))
		} else {
			data.keys.map { DataEntryDescriptorFactory(it, element) }
		}
	}

	private class PodContainersDescriptorFactory(pod: Pod) : DescriptorFactory<Pod>(pod) {

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

	private class PodIpDescriptorFactory(pod: Pod) : DescriptorFactory<Pod>(pod) {

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

	private class DataEntryDescriptorFactory<R: HasMetadata>(private val key: String, resource: R) : DescriptorFactory<R>(resource) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<R>? {
			return ConfigMapDataDescriptor(key, resource, parent, model)
		}

		private class ConfigMapDataDescriptor<R: HasMetadata>(
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

	private class EmptyDataDescriptorFactory<R: HasMetadata>(resource: R) : DescriptorFactory<R>(resource) {

		override fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<R>? {
			return ConfigMapDataDescriptor(resource, parent, model)
		}

		private class ConfigMapDataDescriptor<R: HasMetadata>(
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

	private fun createNamespacesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NAMESPACES }
					childElements {
						model.resources(Namespace::class.java)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it is Namespace }
					parentElements { NAMESPACES }
				}
		)
	}

	private fun createPodElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Pod> {
					anchor { it is Pod }
					childElements {
						listOf(PodContainersDescriptorFactory(it),
								PodIpDescriptorFactory(it))
					}
					parentElements {
						listOf(PODS,
								NODES,
								SERVICES)
					}
				}
		)
	}

	private fun createNodesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NODES }
					childElements {
						model.resources(Node::class.java)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it is Node }
					childElements {
						model.resources(Pod::class.java)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NODES }
				}
		)
	}

	private fun createWorkloadElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == WORKLOADS }
					childElements {
						listOf<Any>(PODS,
								JOBS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == JOBS }
					childElements {
						model.resources(Job::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Job> {
					anchor { it is Job }
					childElements {
						model.resources(Pod::class.java)
								.inCurrentNamespace()
								.filtered(PodForJob(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == PODS }
					childElements {
						model.resources(Pod::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				}
		)
	}

	private fun createNetworkElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NETWORK }
					childElements {
						listOf<Any>(
								SERVICES,
								ENDPOINTS,
								INGRESS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == SERVICES }
					childElements {
						model.resources(Service::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				},
				element<Service> {
					anchor { it is Service }
					childElements {
						model.resources(Pod::class.java)
								.inCurrentNamespace()
								.filtered(PodForService(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { SERVICES }
				},
				element<Any> {
					anchor { it == ENDPOINTS }
					childElements {
						model.resources(Endpoints::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				},
				element<Any> {
					anchor { it == INGRESS }
					childElements {
						model.resources(Ingress::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				}
		)
	}

	private fun createStorageElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == STORAGE }
					childElements {
						listOf<Any>(
								PERSISTENT_VOLUMES,
								PERSISTENT_VOLUME_CLAIMS,
								STORAGE_CLASSES)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == PERSISTENT_VOLUMES }
					childElements {
						model.resources(PersistentVolume::class.java)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				},
				element<Any> {
					anchor { it == PERSISTENT_VOLUME_CLAIMS }
					childElements {
						model.resources(PersistentVolumeClaim::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				},
				element<Any> {
					anchor { it == STORAGE_CLASSES }
					childElements {
						model.resources(StorageClass::class.java)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				}
		)
	}

	private fun createConfigurationElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == CONFIGURATION }
					childElements {
						listOf<Any>(
								CONFIG_MAPS,
								SECRETS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == CONFIG_MAPS }
					childElements {
						model.resources(ConfigMap::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CONFIGURATION }
				},
				element<ConfigMap> {
					anchor { it is ConfigMap }
					childElements {
						createDataDescriptorFactories(it.data, it)
					}
					parentElements { CONFIGURATION }
				},
				element<Any> {
					anchor { it == SECRETS }
					childElements {
						model.resources(Secret::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CONFIGURATION }
				},
				element<Secret>{
					anchor { it is Secret }
					childElements {
						createDataDescriptorFactories(it.data, it)
					}
					parentElements { CONFIGURATION }
				}
		)
	}
}