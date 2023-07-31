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
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.*
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import com.redhat.devtools.intellij.kubernetes.model.resourceName
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIGURATION
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIG_MAPS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CRONJOBS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CUSTOM_RESOURCES_DEFINITIONS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.DAEMONSETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.DEPLOYMENTS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.ENDPOINTS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.INGRESS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.JOBS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NAMESPACES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUMES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUME_CLAIMS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.PODS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.REPLICASETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.REPLICATIONCONTROLLERS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.SECRETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.SERVICES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.STATEFULSETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE_CLASSES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.discovery.v1beta1.Endpoint
import io.fabric8.kubernetes.api.model.storage.StorageClass

class KubernetesStructure(model: IResourceModel) : AbstractTreeStructureContribution(model) {
    object Folders {
		val NAMESPACES = NamespacesFolder("Namespaces")
		val NODES = Folder("Nodes", kind = NodesOperator.KIND)
		val WORKLOADS = Folder("Workloads", kind = null)
			val DEPLOYMENTS = Folder("Deployments", kind = DeploymentsOperator.KIND) // Workloads / Deployments
			val STATEFULSETS = Folder("Stateful Sets", kind = StatefulSetsOperator.KIND) // Workloads / StatefulSets
			val DAEMONSETS = Folder("Daemon Sets", kind = DaemonSetsOperator.KIND) // Workloads / DaemonSets
			val JOBS = Folder("Jobs", kind = JobsOperator.KIND) // Workloads / Jobs
			val CRONJOBS = Folder("Cron Jobs", kind = CronJobsOperator.KIND) // Workloads / CronJobs
			val PODS = Folder("Pods", kind = NamespacedPodsOperator.KIND) // Workloads / Pods
			val REPLICASETS = Folder("Replica Sets", kind = ReplicaSetsOperator.KIND) // Workloads / ReplicaSets
			val REPLICATIONCONTROLLERS = Folder("Replication Controllers", kind = ReplicationControllersOperator.KIND) // Workloads / ReplicationControllers
		val NETWORK = Folder("Network", kind = null)
			val SERVICES = Folder("Services", kind = ServicesOperator.KIND) // Network / Services
			val ENDPOINTS = Folder("Endpoints", kind = EndpointsOperator.KIND) // Network / Endpoints
			val INGRESS = Folder("Ingress", kind = IngressOperator.KIND) // Network / Ingress
			val STORAGE = Folder("Storage", kind = null)
			val PERSISTENT_VOLUMES = Folder("Persistent Volumes", kind = PersistentVolumesOperator.KIND) // Storage / Persistent Volumes
			val PERSISTENT_VOLUME_CLAIMS = Folder("Persistent Volume Claims", kind = PersistentVolumeClaimsOperator.KIND) // Storage / Persistent Volume Claims
			val STORAGE_CLASSES = Folder("Storage Classes", kind = StorageClassesOperator.KIND) // Storage / Storage Classes
		val CONFIGURATION = Folder("Configuration", kind = null)
			val CONFIG_MAPS = Folder("Config Maps", kind = ConfigMapsOperator.KIND) // Configuration / Config Maps
			val SECRETS = Folder("Secrets", kind = SecretsOperator.KIND) // Configuration / Secrets
		val CUSTOM_RESOURCES_DEFINITIONS = Folder("Custom Resources", kind = CustomResourceDefinitionsOperator.KIND)
    }

	override val elementsTree: List<ElementNode<*>> = listOf(
			element<IActiveContext<*,*>> {
				applicableIf { it == getRootElement() }
				children {
					listOf(
						NAMESPACES,
						NODES,
						WORKLOADS,
						NETWORK,
						STORAGE,
						CONFIGURATION,
						CUSTOM_RESOURCES_DEFINITIONS
					)
				}
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

	override fun canContribute() = true

	private fun createNamespacesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == NAMESPACES }
					children {
						model.resources(NamespacesOperator.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
		)
	}

	private fun createPodElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Pod> {
					applicableIf { it is Pod }
					children {
						KubernetesDescriptors.createPodDescriptorFactories(it)
					}
				}
		)
	}

	private fun createNodesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == NODES }
					childrenKind { NodesOperator.KIND }
					children {
						model.resources(NodesOperator.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Node> {
					applicableIf { it is Node }
					childrenKind { AllPodsOperator.KIND }
					children {
						model.resources(AllPodsOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createWorkloadElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == WORKLOADS }
					children {
						listOf(DEPLOYMENTS,
								STATEFULSETS,
								DAEMONSETS,
								JOBS,
								CRONJOBS,
								PODS,
								REPLICASETS,
								REPLICATIONCONTROLLERS)
					}
				},
				element<Folder> {
					applicableIf { it == DEPLOYMENTS }
					childrenKind { DeploymentsOperator.KIND }
					children {
						model.resources(DeploymentsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Deployment> {
					applicableIf { it is Deployment }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForDeployment(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == STATEFULSETS }
					childrenKind { StatefulSetsOperator.KIND }
					children {
						model.resources(StatefulSetsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<StatefulSet> {
					applicableIf { it is StatefulSet }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForStatefulSet(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == DAEMONSETS }
					childrenKind { DaemonSetsOperator.KIND }
					children {
						model.resources(DaemonSetsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<DaemonSet> {
					applicableIf { it is DaemonSet }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForDaemonSet(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == JOBS }
					childrenKind { JobsOperator.KIND }
					children {
						model.resources(JobsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Job> {
					applicableIf { it is Job }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
							.inCurrentNamespace()
							.filtered(PodForJob(it))
							.list()
							.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == CRONJOBS }
					childrenKind { CronJobsOperator.KIND }
					children {
						model.resources(CronJobsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == PODS }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == REPLICASETS }
					childrenKind { ReplicaSetsOperator.KIND }
					children {
						model.resources(ReplicaSetsOperator.KIND)
							.inCurrentNamespace()
							.list()
							.sortedBy(resourceName)
					}
				},
				element<ReplicaSet> {
					applicableIf { it is ReplicaSet }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
							.inCurrentNamespace()
							.filtered(PodForReplicaSet(it))
							.list()
							.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == REPLICATIONCONTROLLERS }
					childrenKind { ReplicationControllersOperator.KIND }
					children {
						model.resources(ReplicationControllersOperator.KIND)
							.inCurrentNamespace()
							.list()
							.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createNetworkElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == NETWORK }
					children {
						listOf(
								SERVICES,
								ENDPOINTS,
								INGRESS)
					}
				},
				element<Folder> {
					applicableIf { it == SERVICES }
					childrenKind { ServicesOperator.KIND }
					children {
						model.resources(ServicesOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Service> {
					applicableIf { it is Service }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForService(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == ENDPOINTS }
					childrenKind { EndpointsOperator.KIND }
					children {
						model.resources(EndpointsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == INGRESS }
					childrenKind { IngressOperator.KIND }
					children {
						model.resources(IngressOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createStorageElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == STORAGE }
					children {
						listOf(
								PERSISTENT_VOLUMES,
								PERSISTENT_VOLUME_CLAIMS,
								STORAGE_CLASSES)
					}
				},
				element<Folder> {
					applicableIf { it == PERSISTENT_VOLUMES }
					children {
						model.resources(PersistentVolumesOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == PERSISTENT_VOLUME_CLAIMS }
					childrenKind { PersistentVolumeClaimsOperator.KIND }
					children {
						model.resources(PersistentVolumeClaimsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Folder> {
					applicableIf { it == STORAGE_CLASSES }
					childrenKind { StorageClassesOperator.KIND }
					children {
						model.resources(StorageClassesOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createConfigurationElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == CONFIGURATION }
					children {
						listOf(
								CONFIG_MAPS,
								SECRETS)
					}
				},
				element<Folder> {
					applicableIf { it == CONFIG_MAPS }
					childrenKind { ConfigMapsOperator.KIND }
					children {
						model.resources(ConfigMapsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<ConfigMap> {
					applicableIf { it is ConfigMap }
					children {
						KubernetesDescriptors.createDataDescriptorFactories((it).data, it)
					}
				},
				element<Folder> {
					applicableIf { it == SECRETS }
					childrenKind { SecretsOperator.KIND }
					children {
						model.resources(SecretsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Secret>{
					applicableIf { it is Secret }
					children {
						KubernetesDescriptors.createDataDescriptorFactories((it).data, it)
					}
				}
		)
	}

	private fun createCustomResourcesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Folder> {
					applicableIf { it == CUSTOM_RESOURCES_DEFINITIONS }
					childrenKind { CustomResourceDefinitionsOperator.KIND }
					children {
						model.resources(CustomResourceDefinitionsOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<CustomResourceDefinition> {
					applicableIf { it is CustomResourceDefinition }
					children {
						model.resources(it)
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	override fun descriptorFactory(): (Any, ResourceKind<out HasMetadata>?, NodeDescriptor<*>?, IResourceModel, Project) -> NodeDescriptor<*>? {
		return KubernetesDescriptors::createDescriptor
	}

	override fun getLeafState(element: Any): LeafState? {
		return when(element) {
			is Namespace,
			is Endpoint,
			is StorageClass,
			is GenericKubernetesResource -> LeafState.ALWAYS
			else -> null
		}
	}

	class NamespacesFolder(label: String): Folder(label, NamespacesOperator.KIND)
}
