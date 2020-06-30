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
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.CronJob
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.storage.StorageClass
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.resource.PodForDaemonSet
import org.jboss.tools.intellij.kubernetes.model.resource.PodForDeployment
import org.jboss.tools.intellij.kubernetes.model.resource.PodForService
import org.jboss.tools.intellij.kubernetes.model.resource.PodForStatefulSet
import org.jboss.tools.intellij.kubernetes.model.resourceName
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIGURATION
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIG_MAPS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CRONJOBS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CUSTOM_RESOURCES_DEFINITIONS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.DAEMONSETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.DEPLOYMENTS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.ENDPOINTS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.INGRESS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.JOBS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NAMESPACES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUMES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUME_CLAIMS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PODS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SECRETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SERVICES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STATEFULSETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE_CLASSES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.Folder

class KubernetesStructure(model: IResourceModel) : AbstractTreeStructureContribution(model) {
    object Folders {
        val NAMESPACES = Folder("Namespaces", Namespace::class.java)
        val NODES = Folder("Nodes", Node::class.java)
        val WORKLOADS = Folder("Workloads", null)
			val DEPLOYMENTS = Folder("Deployments", Deployment::class.java) //  Workloads / StatefulSets
			val STATEFULSETS = Folder("StatefulSets", StatefulSet::class.java) //  Workloads / StatefulSets
			val DAEMONSETS = Folder("DaemonSets", DaemonSet::class.java) //  Workloads / Pods
			val JOBS = Folder("Jobs", Job::class.java) //  Workloads / StatefulSets
			val CRONJOBS = Folder("CronJobs", CronJob::class.java) //  Workloads / StatefulSets
            val PODS = Folder("Pods", Pod::class.java) //  Workloads / Pods
        val NETWORK = Folder("Network", null)
            val SERVICES = Folder("Services", Service::class.java) // Network / Services
            val ENDPOINTS = Folder("Endpoints", Endpoints::class.java) // Network / Endpoints
			val INGRESS = Folder("Ingress", Ingress::class.java) // Network / Ingress
		val STORAGE = Folder("Storage", Endpoints::class.java)
			val PERSISTENT_VOLUMES = Folder("Persistent Volumes", PersistentVolume::class.java) // Storage / Persistent Volumes
			val PERSISTENT_VOLUME_CLAIMS = Folder("Persistent Volume Claims", PersistentVolumeClaim::class.java) // Storage / Persistent Volume Claims
			val STORAGE_CLASSES = Folder("Storage Classes", StorageClass::class.java) // Storage / Storage Classes
		val CONFIGURATION = Folder("Configuration", null)
			val CONFIG_MAPS = Folder("Config Maps", ConfigMap::class.java) // Configuration / Config Maps
			val SECRETS = Folder("Secrets", Secret::class.java) // Configuration / Secrets
		val CUSTOM_RESOURCES_DEFINITIONS = Folder("Custom Resources", CustomResourceDefinition::class.java)
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
							CONFIGURATION,
							CUSTOM_RESOURCES_DEFINITIONS
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
			*createConfigurationElements(),
			*createCustomResourcesElements()
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
		return KubernetesDescriptors.createDescriptor(element, parent, model)
	}

	override fun canContribute() = true

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
						KubernetesDescriptors.createPodDescriptorsFactories(it)
					}
					parentElements {
						listOf(PODS,
								NODES,
								SERVICES,
								STATEFULSETS,
								DAEMONSETS)
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
						listOf<Any>(DEPLOYMENTS,
								STATEFULSETS,
								DAEMONSETS,
								JOBS,
								CRONJOBS,
								PODS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == DEPLOYMENTS }
					childElements {
						model.resources(Deployment::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Deployment> {
					anchor { it is Deployment }
					childElements {
						model.resources(Pod::class.java)
								.inCurrentNamespace()
								.filtered(PodForDeployment(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { DEPLOYMENTS }
				},
				element<Any> {
					anchor { it == STATEFULSETS }
					childElements {
						model.resources(StatefulSet::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<StatefulSet> {
					anchor { it is StatefulSet }
					childElements {
						model.resources(Pod::class.java)
								.inCurrentNamespace()
								.filtered(PodForStatefulSet(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == DAEMONSETS }
					childElements {
						model.resources(DaemonSet::class.java)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<DaemonSet> {
					anchor { it is DaemonSet }
					childElements {
						model.resources(Pod::class.java)
								.inCurrentNamespace()
								.filtered(PodForDaemonSet(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
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
				element<Any> {
					anchor { it == CRONJOBS }
					childElements {
						model.resources(CronJob::class.java)
								.inCurrentNamespace()
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
						KubernetesDescriptors.createDataDescriptorFactories(it.data, it)
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
						KubernetesDescriptors.createDataDescriptorFactories(it.data, it)
					}
					parentElements { CONFIGURATION }
				}
		)
	}

	private fun createCustomResourcesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == CUSTOM_RESOURCES_DEFINITIONS }
					childElements {
						model.resources(CustomResourceDefinition::class.java)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<CustomResourceDefinition> {
					anchor { it is CustomResourceDefinition }
					childElements {
						model.getCustomResources(it, IActiveContext.ResourcesIn.CURRENT_NAMESPACE)
					}
					parentElements { getRootElement() }
				}
		)
	}
}