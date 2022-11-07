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
package com.redhat.devtools.intellij.kubernetes.model.context

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinition
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.nonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NodesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ServicesOperator
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.model.Scope
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class KubernetesContextTest {

	private val modelChange: ResourceModelObservable = mock()
	private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
	private val currentNamespace = NAMESPACE2
	private val client = KubeClientAdapter(client(currentNamespace.metadata.name, allNamespaces))
	private val namespaceWatchOperation: (watcher: Watcher<in Namespace>) -> Watch? = { null }

	private val namespacesOperator = nonNamespacedResourceOperator<Namespace, NamespacedKubernetesClient>(
			NamespacesOperator.KIND,
			allNamespaces.toList(),
			namespaceWatchOperation)

	private val nodesOperator = nonNamespacedResourceOperator<Node, KubernetesClient>(
			NodesOperator.KIND,
			listOf(resource("node1", "ns1", "uid1", "v1"),
					resource("node2", "ns1", "uid2", "v1"),
					resource("node3", "ns1", "uid3", "v1")))

	private val allPods = arrayOf(POD1, POD2, POD3)
	private val allPodsOperator = nonNamespacedResourceOperator<Pod, KubernetesClient>(
			AllPodsOperator.KIND,
			allPods.toList())

	private val namespacedPodsOperator = namespacedResourceOperator<Pod, KubernetesClient>(
			NamespacedPodsOperator.KIND,
			allPods.toList(),
			currentNamespace)

	private val hasMetadata1 = resource<HasMetadata>("hasMetadata1", "ns", "uid1", "v1")
	private val hasMetadata2 = resource<HasMetadata>("hasMetadata2", "ns", "uid2", "v1")
	private val hasMetadataOperator: INamespacedResourceOperator<HasMetadata, NamespacedKubernetesClient> =
		namespacedResourceOperator(
			ResourceKind.create(HasMetadata::class.java),
			setOf(hasMetadata1, hasMetadata2),
			currentNamespace
		)

	private val crdKind = ResourceKind.create(CustomResourceDefinition::class.java)
	private val namespacedDefinition = customResourceDefinition(
		"namespaced crd",
		"ns1",
		"uid1",
		crdKind.version,
		"version1",
		"group1",
		"namespaced-crd",
		Scope.NAMESPACED.value())
	private val clusterwideDefinition = customResourceDefinition(
		"cluster crd",
		"ns2",
		"uid2",
		crdKind.version,
		"version2",
		"group2",
		"cluster-crd",
		Scope.CLUSTER.value())
	private val customResourceDefinitionsOperator: INonNamespacedResourceOperator<CustomResourceDefinition, NamespacedKubernetesClient> =
		nonNamespacedResourceOperator(
			crdKind,
			listOf(namespacedDefinition, clusterwideDefinition))

	private val namespacedCustomResource1 = customResource(
		"genericCustomResource1",
		"namespace1",
		namespacedDefinition)
	private val namespacedCustomResource2 = customResource(
		"genericCustomResource2",
		"namespace1",
		namespacedDefinition)

	private val namespacedCustomResourceWatch = mock<Watch>()
	private val namespacedCustomResourceWatchOp: (watcher: Watcher<in GenericKubernetesResource>) -> Watch? = { namespacedCustomResourceWatch }
	private val namespacedCustomResourceOperator: INamespacedResourceOperator<GenericKubernetesResource, KubernetesClient> =
		namespacedResourceOperator(
			ResourceKind.create(namespacedDefinition.spec),
			listOf(namespacedCustomResource1, namespacedCustomResource2),
			currentNamespace,
			namespacedCustomResourceWatchOp
		)
	private val nonNamespacedCustomResource = resource<GenericKubernetesResource>(
		"genericCustomResource1",
		"smurfington",
		"uid",
		"v1")
	private val nonNamespacedCustomResourceWatchOp: (watcher: Watcher<in GenericKubernetesResource>) -> Watch? = { null }
	private val nonNamespacedCustomResourcesOperator: INonNamespacedResourceOperator<GenericKubernetesResource, KubernetesClient> =
		nonNamespacedResourceOperator(
			ResourceKind.create(GenericKubernetesResource::class.java),
			listOf(nonNamespacedCustomResource),
			nonNamespacedCustomResourceWatchOp
		)

	private val resourceWatch: ResourceWatch<ResourceKind<out HasMetadata>> = mock()
	private val notification: Notification = mock()

	private lateinit var context: TestableKubernetesContext

	@Before
	fun before() {
		val internalResourcesOperators = mutableListOf(
			namespacesOperator,
			nodesOperator,
			namespacedPodsOperator,
			allPodsOperator,
			customResourceDefinitionsOperator)
		val extensionResourceOperators = mutableListOf(
			hasMetadataOperator)
		context = createContext(internalResourcesOperators, extensionResourceOperators)
	}

	private fun createContext(internalResourcesOperators: List<IResourceOperator<*>>, extensionResourceOperators: List<IResourceOperator<*>>): TestableKubernetesContext {
		return spy(
			TestableKubernetesContext(
				mock(),
				modelChange,
				this@KubernetesContextTest.client,
				internalResourcesOperators,
				extensionResourceOperators,
				Pair(namespacedCustomResourceOperator, nonNamespacedCustomResourcesOperator),
				resourceWatch,
				notification
			)
		)
	}

	@Test
	fun `#getCurrentNamespace should retrieve current namespace in client`() {
		// given
		// when
		context.getCurrentNamespace()
		// then
		verify(client.get()).namespace
	}

	@Test
	fun `#getCurrentNamespace should use null if no namespace set in client`() {
		// given
		whenever(client.get().namespace)
				.thenReturn(null)
		// when
		val namespace = context.getCurrentNamespace()
		// then
		assertThat(namespace).isNull()
	}

	@Test
	fun `#getCurrentNamespace should return null if current namespace is set but doesnt exist`() {
		// given
		whenever(client.get().namespace)
				.thenReturn("inexistent")

		// when
		val namespace = context.getCurrentNamespace()
		// then
		assertThat(namespace).isNull()
	}

	@Test
	fun `#isCurrentNamespace should return false if given namespace is not in existing namespaces`() {
		// given
		// when
		val isCurrent = context.isCurrentNamespace(mock<HasMetadata>())
		// then
		assertThat(isCurrent).isFalse
	}

	@Test
	fun `#isCurrentNamespace should return false if given namespace is not current namespaces`() {
		// given
		assertThat(currentNamespace).isNotEqualTo(NAMESPACE3)
		// when
		val isCurrent = context.isCurrentNamespace(NAMESPACE3)
		// then
		assertThat(isCurrent).isFalse
	}

	@Test
	fun `#getResources should get all resources in operator for given resource type in correct ResourcesIn type`() {
		// given
		// when
		context.getAllResources(NodesOperator.KIND, ResourcesIn.NO_NAMESPACE)
		// then
		verify(nodesOperator).allResources
	}

	@Test
	fun `#getResources should not get all resources in operator for given resource type in wrong ResourcesIn type`() {
		// given
		// when
		// there are no namespaces in current namespace
		context.getAllResources(NamespacesOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(nodesOperator, never()).allResources
	}

	@Test
	fun `#getResources should return empty list if there's no operator for given resource type in given ResourceIn type`() {
		// given
		// when
		// namespace operator exists but for ResourceIn.NO_NAMESPACE
		val resources = context.getAllResources(NamespacesOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		assertThat(resources).isEmpty()
	}

	@Test
	fun `#getResources should return empty list if there's no operator of given resource type in given ResourceIn type`() {
		// given
		// when
		// services operator was not registered to context, it doesn't exist in context
		val services = context.getAllResources(ServicesOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		assertThat(services).isEmpty()
	}

	@Test
	fun `#getCustomResources should query CustomResourceOperator`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		// then
		verify(namespacedCustomResourceOperator).allResources
	}

	@Test
	fun `#getCustomResources should create CustomResourceOperator once and reuse it`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		context.getAllResources(namespacedDefinition)
		// then
		verify(context, times(1)).createCustomResourcesOperator(eq(namespacedDefinition), any())
	}

	@Test
	fun `#getCustomResources should create CustomResourceOperator for each unique definition`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		context.getAllResources(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesOperator(eq(namespacedDefinition), any())
		verify(context, times(1)).createCustomResourcesOperator(eq(clusterwideDefinition), any())
	}

	@Test(expected = ResourceException::class)
	fun `#getCustomResources should throw if scope is unknown`() {
		// given
		val bogusScope = customResourceDefinition(
				"bogus scope",
			"ns1",
			"uid",
			"v1",
			"version1",
			"group1",
			"bogus-scope-crd",
			"Bogus")
		// when
		context.getAllResources(bogusScope)
		// then
	}

	@Test
	fun `#delete should call operator#delete`() {
		// given
		val toDelete = listOf(POD2)
		// when
		context.delete(toDelete)
		// then
		verify(namespacedPodsOperator).delete(toDelete)
	}

	@Test
	fun `#delete should not call operator#delete if there is no operator for it`() {
		// given
		val toDelete = listOf(resource<HasMetadata>("lord sith", "ns1", "uid", "v1"))
		// when
		context.delete(toDelete)
		// then
		verify(namespacedPodsOperator, never()).delete(toDelete)
	}

	@Test
	fun `#delete should only delete 1 resource if it is given twice the same resource`() {
		// given
		val toDelete = listOf(POD2, POD2)
		// when
		context.delete(toDelete)
		// then
		verify(namespacedPodsOperator, times(1)).delete(eq(listOf(POD2)))
	}

	@Test
	fun `#delete should delete in 2 separate operators if resources of 2 kinds are given`() {
		// given
		val toDelete = listOf(POD2, NAMESPACE2)
		// when
		context.delete(toDelete)
		// then
		verify(namespacedPodsOperator, times(1)).delete(eq(listOf(POD2)))
		verify(namespacesOperator, times(1)).delete(eq(listOf(NAMESPACE2)))
	}

	@Test
	fun `#delete should fire if resource was deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(allPodsOperator.delete(any()))
				.thenReturn(true)
		// when
		context.delete(toDelete)
		// then
		verify(modelChange).fireModified(toDelete)
	}

	@Test
	fun `#delete should set resource deletionTimestamp if resource was deleted`() {
		// given
		val toDelete = listOf(POD2, POD3)
		// when
		context.delete(toDelete)
		// then
		verify(POD2.metadata, atLeastOnce()).deletionTimestamp = any()
		verify(POD3.metadata, atLeastOnce()).deletionTimestamp = any()
	}

	@Test(expected=MultiResourceException::class)
	fun `#delete should throw if resource was NOT deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(namespacedPodsOperator.delete(any()))
			.thenReturn(false)
		// when
		context.delete(toDelete)
		// then
		// expect exception
	}

	@Test
	fun `#delete should throw for operator that failed, not successful ones`() {
		// given
		val toDelete = listOf(POD2, NAMESPACE2)
		whenever(namespacedPodsOperator.delete(any()))
			.thenReturn(false)
		// when
		val ex = try {
			context.delete(toDelete)
			null
		} catch (e: MultiResourceException) {
			e
		}
		// then
		assertThat(ex?.causes?.size).isEqualTo(1)
		val resourceEx = ex?.causes?.toTypedArray()!![0]
		assertThat(resourceEx.resources).containsExactly(POD2)
	}

	@Test
	fun `#delete should NOT fire if resource was NOT deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(namespacedPodsOperator.delete(any()))
			.thenReturn(false)
		// when
		try {
			context.delete(toDelete)
		} catch (e: MultiResourceException) {
			// ignore expected exception
		}
		// then
		verify(modelChange, never()).fireModified(toDelete)
	}

	@Test
	fun `#watch(kind) should call watch on namespaced resource operator`() {
		// given
		// create custom resource operator
		givenCustomResourceOperatorInContext(namespacedDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		// trigger watch on namespaces
		context.namespacedOperators
		clearInvocations(context.watch)
		// when
		context.watch(ResourceKind.create(namespacedDefinition.spec)!!)
		// then
		verify(context.watch).watch(
			eq(namespacedCustomResourceOperator.kind),
			argThat { function ->
				// cannot compare functions directly, mockito creates proxies,
				// identical kotlin lambdas may not be equal
				// kotlin method references are KFunction, lambdas are KClassImpl
				function.invoke(mock()) ==
						namespacedCustomResourceWatchOp.invoke(mock())
			},
			any())
	}

	@Test
	fun `#watch(kind) should call watch on non-namespaced resource operator`() {
		// given
		// create custom resource operator
		givenCustomResourceOperatorInContext(clusterwideDefinition,
				customResourceDefinitionsOperator,
				nonNamespacedCustomResourcesOperator)
		// trigger watch on namespaces
		context.namespacedOperators
		clearInvocations(context.watch)
		// when
		context.watch(ResourceKind.create(clusterwideDefinition.spec)!!)
		// then
		verify(context.watch).watch(
			eq(nonNamespacedCustomResourcesOperator.kind),
			argThat { function ->
				// cannot compare functions directly, mockito creates proxies,
				// identical kotlin lambdas may not be equal
				// kotlin method references are KFunction, lambdas are KClassImpl
				function.invoke(mock()) ==
						nonNamespacedCustomResourceWatchOp.invoke(mock())
			},
			any())
	}

	@Test
	fun `#watch(definition) should create custom resource operator if none exists yet`() {
		// given
		givenCustomResourceOperatorInContext(clusterwideDefinition,
			customResourceDefinitionsOperator,
			null) // no resource operator for definition
		// when
		context.watch(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesOperator(
			eq(clusterwideDefinition),
			any()
		)
	}

	@Test
	fun `#watch(definition) should NOT create custom resource operator if already exists`() {
		// given
		givenCustomResourceOperatorInContext(clusterwideDefinition,
			customResourceDefinitionsOperator,
			nonNamespacedCustomResourcesOperator)
		// when
		context.watch(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesOperator(
			eq(clusterwideDefinition),
			any()
		)
	}

	@Test
	fun `#stopWatch(kind) should stop watch`() {
		// given
		// when
		context.stopWatch(NamespacesOperator.KIND)
		// then
		verify(resourceWatch, times(1)).stopWatch(NamespacesOperator.KIND)
	}

	@Test
	fun `#stopWatch(kind) should clear operators`() {
		// given
		// when
		context.stopWatch(NamespacesOperator.KIND)
		// then
		verify(namespacesOperator, times(1)).invalidate()
	}

	@Test
	fun `#stopWatch(kind) should NOT notify of changes in operator`() {
		// given
		// when
		context.stopWatch(NamespacesOperator.KIND)
		// then
		// dont notify invalidation change because this would cause UI to reload
		// and therefore to repopulate the cache immediately.
		// Any resource operation that eventually happens while the watch is not active would cause the cache
		// to become out-of-sync and it would therefore return invalid resources when asked to do so
		verify(modelChange, never()).fireModified(NamespacesOperator.KIND)
	}

	@Test
	fun `#stopWatch(definition) should stop watching) kind specified by definition`() {
		// given
		val definition = clusterwideDefinition
		val kind = ResourceKind.create(definition.spec)
		// when
		context.stopWatch(definition)
		// then
		verify(context, times(1)).stopWatch(kind!!)
	}

	@Test
	fun `#added(namespace) should add to namespaces operator but not to pods operator`() {
		// given
		val namespace = resource<Namespace>("papa smurf namespace", "ns1", "someNamespaceUid", "v1")
		// when
		context.added(namespace)
		// then
		verify(namespacesOperator).added(namespace)
		verify(namespacedPodsOperator, never()).added(namespace)
	}

	@Test
	fun `#added(pod) should add pod in current namespace to pods operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name, "somePodUid", "v1")
		// when
		context.added(pod)
		// then
		verify(namespacedPodsOperator).added(pod)
	}

	@Test
	fun `#added(pod) should add pod to allPods operator`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace", "gargamelUid", "v1")
		// when
		context.added(pod)
		// then
		verify(allPodsOperator).added(pod)
	}

	@Test
	fun `#added(pod) should not add pod of non-current namespace to namespacedPods operator`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace", "gargamelUid", "v1")
		// when
		context.added(pod)
		// then
		verify(namespacedPodsOperator, never()).added(pod)
	}

	@Test
	fun `#added(pod) should return true if pod was added to pods operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name, "somePodUid", "v1")
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(true)

		// when
		val added = context.added(pod)
		// then
		assertThat(added).isTrue
	}

	@Test
	fun `#added(namespace) should return true if namespace was added to namespace operator`() {
		// given
		val namespace = resource<Namespace>("pod", null, "someNamespaceUid", "v1")
		whenever(namespacesOperator.added(namespace))
			.thenReturn(true)
		// when
		val added = context.added(namespace)
		// then
		assertThat(added).isTrue
	}

	@Test
	fun `#added(pod) should return false if pod was not added to pods operator`() {
		// given
		val pod = resource<Pod>("pod", "ns1", "somePodUid", "v1")
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(false)
		// when
		val added = context.added(pod)
		// then
		assertThat(added).isFalse
	}

	@Test
	fun `#added(pod) should fire if operator added pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name, "gargamelUid", "v1")
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(true)
		// when
		context.added(pod)
		// then
		verify(modelChange).fireAdded(pod)
	}

	@Test
	fun `#added(pod) should not fire if operator did not add pod`() {
		// given
		val pod = resource<Pod>("gargamel", "ns1", "gargamelUid", "v1")
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(false)
		// when
		context.added(pod)
		// then
		verify(modelChange, never()).fireAdded(pod)
	}

	@Test
	fun `#added(CustomResourceDefinition) should create namespaced custom resources operator if definition was added`() {
		// given
		val currentNamespace = currentNamespace.metadata.name
		setNamespaceForResource(currentNamespace, namespacedDefinition)
		whenever(customResourceDefinitionsOperator.added(namespacedDefinition))
				.doReturn(true)
		clearInvocations(context)
		// when
		context.added(namespacedDefinition)
		// then
		verify(context)
				.createCustomResourcesOperator(
						eq(namespacedDefinition),
						eq(ResourcesIn.CURRENT_NAMESPACE))
	}

	@Test
	fun `#added(CustomResourceDefinition) should create clusterwide custom resources operator if definition was added`() {
		// given
		whenever(customResourceDefinitionsOperator.added(clusterwideDefinition))
				.doReturn(true)
		// when
		context.added(clusterwideDefinition)
		// then
		verify(context, times(1))
				.createCustomResourcesOperator(
						eq(clusterwideDefinition),
						eq(ResourcesIn.NO_NAMESPACE))
	}

	@Test
	fun `#added(CustomResourceDefinition) should NOT create custom resources operator if definition was NOT added`() {
		// given
		whenever(namespacedCustomResourceOperator.added(clusterwideDefinition))
				.doReturn(false)
		// when
		context.added(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesOperator(eq(clusterwideDefinition), any())
	}

	@Test
	fun `#removed(pod) should remove pod from pods operator but not from namespace operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name, "somePodUid", "v1")
		// when
		context.removed(pod)
		// then
		verify(namespacedPodsOperator).removed(pod)
		verify(namespacesOperator, never()).removed(pod)
	}

	@Test
	fun `#removed(pod) should remove pod in current namespace from allPods && namespacedPods operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name, "somePodUid", "v1")
		// when
		context.removed(pod)
		// then
		verify(namespacedPodsOperator).removed(pod)
		verify(allPodsOperator).removed(pod)
	}

	@Test
	fun `#removed(pod) should remove pod in non-current namespace from allPods operator but not from namespacedPods operator`() {
		// given
		val pod = resource<Pod>("pod", "42", "somePodUid", "v1")
		// when
		context.removed(pod)
		// then
		verify(namespacedPodsOperator, never()).removed(pod)
		verify(allPodsOperator).removed(pod)
	}

	@Test
	fun `#removed(namespace) should remove from namespaces operator but not from pods operator`() {
		// given
		val namespace = NAMESPACE1
		// when
		context.removed(namespace)
		// then
		verify(namespacesOperator).removed(namespace)
		verify(namespacedPodsOperator, never()).removed(namespace)
	}

	@Test
	fun `#removed(pod) should fire if operator removed pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name, "gargamelUid", "v1")
		whenever(namespacedPodsOperator.removed(pod))
			.thenReturn(true)
		// when
		context.removed(pod)
		// then
		verify(modelChange).fireRemoved(pod)
	}

	@Test
	fun `#removed(pod) should not fire if operator did not remove pod`() {
		// given
		val pod = resource<Pod>("gargamel", "ns1", "gargamelUid", "v1")
		whenever(namespacedPodsOperator.removed(pod))
			.thenReturn(false)
		// when
		context.removed(pod)
		// then
		verify(modelChange, never()).fireRemoved(pod)
	}

	@Test
	fun `#removed(CustomResourceDefinition) should remove clusterwide custom resource operator when definition is removed`() {
		// given
		givenCustomResourceOperatorInContext(clusterwideDefinition,
				customResourceDefinitionsOperator,
				nonNamespacedCustomResourcesOperator)
		// when
		context.removed(clusterwideDefinition)
		// then
		assertThat(context.nonNamespacedOperators)
				.doesNotContainValue(nonNamespacedCustomResourcesOperator)
	}

	@Test
	fun `#removed(CustomResourceDefinition) should stop watch when definition is removed `() {
		// given
		givenCustomResourceOperatorInContext(clusterwideDefinition,
				customResourceDefinitionsOperator,
				nonNamespacedCustomResourcesOperator)
		clearInvocations(resourceWatch)
		// when
		context.removed(clusterwideDefinition)
		// then
		verify(resourceWatch).stopWatch(nonNamespacedCustomResourcesOperator.kind)
	}

	@Test
	fun `#removed(CustomResourceDefinition) should remove namespaced custom resource operator when definition is removed`() {
		// given
		givenCustomResourceOperatorInContext(namespacedDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		// when
		context.removed(namespacedDefinition)
		// then
		assertThat(context.namespacedOperators)
				.doesNotContainValue(namespacedCustomResourceOperator)
	}

	@Test
	fun `#removed(CustomResourceDefinition) should NOT remove namespaced custom resource operator when definition is NOT removed`() {
		// given
		givenCustomResourceOperatorInContext(clusterwideDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		whenever(customResourceDefinitionsOperator.removed(clusterwideDefinition))
				.doReturn(false) // was not removed
		// when
		context.removed(clusterwideDefinition)
		// then
		assertThat(context.namespacedOperators)
				.containsValue(namespacedCustomResourceOperator)
	}

	@Test
	fun `#invalidate() should invalidate all resource operators`() {
		// given
		// when
		context.invalidate()
		// then
		verify(namespacesOperator).invalidate()
		verify(namespacedPodsOperator).invalidate()
	}

	@Test
	fun `#invalidate(kind) should invalidate resource operator for this kind`() {
		// given
		// when
		context.invalidate(namespacedPodsOperator.kind)
		// then
		verify(namespacesOperator, never()).invalidate()
		verify(namespacedPodsOperator).invalidate()
	}

	@Test
	fun `#replace(resource) should replace in namespaced resource operator`() {
		// given
		val pod = allPods[0]
		// when
		context.replaced(pod)
		// then
		verify(namespacesOperator, never()).replaced(pod)
		verify(namespacedPodsOperator).replaced(pod)
	}

	@Test
	fun `#replace(resource) should replace in non-namespaced resource operator`() {
		// given
		val namespace = allNamespaces[0]
		// when
		context.replaced(namespace)
		// then
		verify(namespacesOperator).replaced(namespace)
		verify(namespacedPodsOperator, never()).replaced(namespace)
	}

	@Test
	fun `#replaced(customResource) should replace in custom resource operator`() {
		// given
		givenCustomResourceOperatorInContext(namespacedDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		// when
		context.replaced(namespacedCustomResource1)
		// then
		verify(namespacedCustomResourceOperator).replaced(namespacedCustomResource1)
	}

	@Test
	fun `#replaced(pod) should fire if operator replaced pod`() {
		// given
		val pod = resource<Pod>("gargamel", "ns1", "gargamelUid", "v1")
		whenever(namespacedPodsOperator.replaced(pod))
			.thenReturn(true)
		// when
		context.replaced(pod)
		// then
		verify(modelChange).fireModified(pod)
	}

	@Test
	fun `#replaced(pod) should not fire if operator did NOT replace pod`() {
		// given
		val pod = resource<Pod>("gargamel", "ns1", "gargamelUid", "v1")
		whenever(namespacedPodsOperator.replaced(pod))
			.thenReturn(false)
		// when
		context.replaced(pod)
		// then
		verify(modelChange, never()).fireModified(pod)
	}

	@Test
	fun `#close should NOT close client (client belongs to AllContext, is only passed to context)`() {
		// given
		// when
		context.close()
		// then
		verify(client.get(), never()).close()
	}

	private fun givenCustomResourceOperatorInContext(
		definition: CustomResourceDefinition,
		definitionOperator: IResourceOperator<CustomResourceDefinition>,
		resourceOperator: IResourceOperator<GenericKubernetesResource>?) {
		whenever(definitionOperator.removed(definition))
			.doReturn(true)

		val kind = ResourceKind.create(definition.spec)
		if (resourceOperator != null) {
			whenever(resourceOperator.kind)
				.doReturn(kind!!)
		}
		if (resourceOperator is INamespacedResourceOperator<*, *>) {
			@Suppress("UNCHECKED_CAST")
			context.namespacedOperators[kind!!] =
				resourceOperator as INamespacedResourceOperator<*, KubernetesClient>
		} else if (resourceOperator is INonNamespacedResourceOperator<*, *>) {
			@Suppress("UNCHECKED_CAST")
			context.nonNamespacedOperators[kind!!] =
				resourceOperator as INonNamespacedResourceOperator<*, KubernetesClient>
		}
	}

	private fun setNamespaceForResource(namespace: String?, resource: HasMetadata) {
		whenever(resource.metadata.namespace)
				.doReturn(namespace)
	}

	class TestableKubernetesContext(
		context: NamedContext,
        observable: ResourceModelObservable,
        client: KubeClientAdapter,
        private val internalResourceOperators: List<IResourceOperator<out HasMetadata>>,
        private val extensionResourceOperators: List<IResourceOperator<out HasMetadata>>,
        private val customResourcesOperators: Pair<
					INamespacedResourceOperator<GenericKubernetesResource, KubernetesClient>,
					INonNamespacedResourceOperator<GenericKubernetesResource, KubernetesClient>>,
        public override var watch: ResourceWatch<ResourceKind<out HasMetadata>>,
        override val notification: Notification)
		: KubernetesContext(context, observable, client) {

		public override val namespacedOperators
				: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourceOperator<out HasMetadata, KubernetesClient>>
			get() {
				return super.namespacedOperators
			}

		public override val nonNamespacedOperators
				: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourceOperator<*, *>>
			get() {
				return super.nonNamespacedOperators
			}

		override fun getInternalResourceOperators(client: ClientAdapter<out KubernetesClient>)
				: List<IResourceOperator<out HasMetadata>> {
			return internalResourceOperators
		}

		override fun getExtensionResourceOperators(client: ClientAdapter<out KubernetesClient>)
				: List<IResourceOperator<out HasMetadata>> {
			return extensionResourceOperators
		}

		public override fun createCustomResourcesOperator(
			definition: CustomResourceDefinition,
			resourceIn: ResourcesIn)
				: IResourceOperator<GenericKubernetesResource> {
			return when(resourceIn) {
				ResourcesIn.CURRENT_NAMESPACE ->
					customResourcesOperators.first
				ResourcesIn.NO_NAMESPACE,
				ResourcesIn.ANY_NAMESPACE ->
					customResourcesOperators.second
			}
		}

	}
}
