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
package org.jboss.tools.intellij.kubernetes.model.context

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.ModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceProvider
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.nonNamespacedResourceProvider
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resource
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.junit.Before
import org.junit.Test

class KubernetesContextTest {

	private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
	private val currentNamespace = NAMESPACE2
	private val allPods = arrayOf(POD1, POD2, POD3)
	private val client: NamespacedKubernetesClient = client(currentNamespace.metadata.name, allNamespaces)
	private val watchable1: Watchable<Watch, Watcher<in HasMetadata>> = mock()
	private val watchable2: Watchable<Watch, Watcher<in HasMetadata>> = mock()
	private val observable: ModelChangeObservable = mock()

	private val namespacesProvider = nonNamespacedResourceProvider(
			Namespace::class.java,
			allNamespaces.toSet())
	private val namespacedPodsProvider = namespacedResourceProvider(
			Pod::class.java,
			allPods.toSet(),
			currentNamespace)
	private val allPodsProvider = nonNamespacedResourceProvider(
			Pod::class.java,
			allPods.toSet())

	private val hasMetadata1 = resource<HasMetadata>("hasMetadata1")
	private val hasMetadata2 = resource<HasMetadata>("hasMetadata2")
	private val hasMetadataProvider: INamespacedResourcesProvider<HasMetadata> = namespacedResourceProvider(
			HasMetadata::class.java,
			setOf(hasMetadata1, hasMetadata2),
			currentNamespace)

	private lateinit var context: TestableKubernetesContext

	@Before
	fun before() {
		context = createContext()
	}

	private fun createContext(): TestableKubernetesContext {
		val internalResourcesProviders = mutableListOf(
				namespacesProvider,
				namespacedPodsProvider,
				allPodsProvider)
		val extensionResourceProviders = mutableListOf(
				hasMetadataProvider)
		val context = spy(TestableKubernetesContext(
				observable,
				this@KubernetesContextTest.client,
				internalResourcesProviders,
				extensionResourceProviders))
		doReturn(
				listOf { watchable1 }, // returned on 1st call
				listOf { watchable2 }) // returned on 2nd call
				.whenever(context).getRetrieveOperations(any())
		return context
	}

	@Test
	fun `context instantiation should retrieve current namespace in client`() {
		// given
		// context created in #before
		// when
		// then
		verify(client.configuration).namespace
	}

	@Test
	fun `#setCurrentNamespace should remove watched resources for current namespace`() {
		// given
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		val captor = argumentCaptor<List<() -> Watchable<Watch, Watcher<HasMetadata>>>>()
		verify(context.watch).ignoreAll(captor.capture())
		val suppliers = captor.firstValue
		assertThat(suppliers.first().invoke()).isEqualTo(watchable1)
	}

	@Test
	fun `#setCurrentNamespace should add new watched resources for new current namespace`() {
		// given
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		val captor = argumentCaptor<List<() -> Watchable<Watch, Watcher<HasMetadata>>>>()
		verify(context.watch).watchAll(captor.capture())
		val suppliers = captor.firstValue
		assertThat(suppliers.first().invoke()).isEqualTo(watchable2)
	}

	@Test
	fun `#setCurrentNamespace should invalidate namespaced resource providers`() {
		// given
		val namespace = NAMESPACE1.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacedPodsProvider).invalidate()
	}

	@Test
	fun `#setCurrentNamespace should not invalidate nonnamespaced resource providers`() {
		// given
		val namespace = NAMESPACE1.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacesProvider, never()).invalidate()
	}

	@Test
	fun `#setCurrentNamespace should not invalidate namespaced resource providers if it didn't change`() {
		// given
		val namespace = currentNamespace.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacedPodsProvider, never()).invalidate()
	}

	@Test
	fun `#setCurrentNamespace should fire change in current namespace`() {
		// given
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(observable).fireCurrentNamespace(NAMESPACE1.metadata.name)
	}

	@Test
	fun `#setCurrentNamespace should set namespace in client`() {
		// given
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(client.configuration).namespace = NAMESPACE1.metadata.name
	}

	@Test
	fun `#getCurrentNamespace should retrieve current namespace in client`() {
		// given
		clearInvocations(client.configuration) // clear invocation when constructing context
		// when
		context.getCurrentNamespace()
		// then
		verify(client.configuration).namespace
	}

	@Test
	fun `#getCurrentNamespace should use 1st existing namespace if no namespace set in client`() {
		// given
		whenever(client.configuration.namespace)
				.thenReturn(null)
		// when
		val namespace = context.getCurrentNamespace()
		// then
		assertThat(namespace).isEqualTo(allNamespaces[0].metadata.name)
	}

	@Test
	fun `#getResources should get all resources in provider for given resource type in correct ResourcesIn type`() {
		// given
		// when
		context.getResources(Namespace::class.java, ResourcesIn.NO_NAMESPACE)
		// then
		verify(namespacesProvider).getAllResources()
	}

	@Test
	fun `#getResources should not get all resources in provider for given resource type in wrong ResourcesIn type`() {
		// given
		// when
		// there are no namespaces in current namespace
		context.getResources(Namespace::class.java, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(namespacesProvider, never()).getAllResources()
	}

	@Test
	fun `#getResources should return empty list if there's no provider for given resource type in given ResourceIn type`() {
		// given
		// when
		// namespace provider exists but for ResourceIn.NO_NAMESPACE
		context.getResources(Namespace::class.java, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(namespacesProvider, never()).getAllResources()
	}

	@Test
	fun `#getResources should return empty list if there's no provider of given resource type in given ResourceIn type`() {
		// given
		// when
		val services = context.getResources(Service::class.java, ResourcesIn.NO_NAMESPACE)
		// then
		assertThat(services).isEmpty()
	}

	@Test
	fun `#add(namespace) should add to namespaces provider but not to pods provider`() {
		// given
		val namespace = resource<Namespace>("papa smurf namespace")
		// when
		context.add(namespace)
		// then
		verify(namespacesProvider).add(namespace)
		verify(namespacedPodsProvider, never()).add(namespace)
	}

	@Test
	fun `#add(pod) should add pod in current namespace to pods provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.add(pod)
		// then
		verify(namespacedPodsProvider).add(pod)
	}

	@Test
	fun `#add(pod) should add pod to allPods provider`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace")
		// when
		context.add(pod)
		// then
		verify(allPodsProvider).add(pod)
	}

	@Test
	fun `#add(pod) should not add pod of non-current namespace to namespacedPods provider`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace")
		// when
		context.add(pod)
		// then
		verify(namespacedPodsProvider, never()).add(pod)
	}

	@Test
	fun `#add(pod) should return true if pod was added to pods provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		doReturn(true)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		val added = context.add(pod)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(namespace) should return true if namespace was added to namespace provider`() {
		// given
		val namespace = resource<Namespace>("pod")
		doReturn(true)
				.whenever(namespacesProvider).add(namespace)
		// when
		val added = context.add(namespace)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(pod) should return false if pod was not added to pods provider`() {
		// given
		val pod = resource<Pod>("pod")
		doReturn(false)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		val added = context.add(pod)
		// then
		assertThat(added).isFalse()
	}

	@Test
	fun `#add(pod) should fire if provider added pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		doReturn(true)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		context.add(pod)
		// then
		verify(observable).fireAdded(pod)
	}

	@Test
	fun `#add(pod) should not fire if provider did not add pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		doReturn(false)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		context.add(pod)
		// then
		verify(observable, never()).fireAdded(pod)
	}

	@Test
	fun `#remove(pod) should remove pod from pods provider but not from namespace provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.remove(pod)
		// then
		verify(namespacedPodsProvider).remove(pod)
		verify(namespacesProvider, never()).remove(pod)
	}

	@Test
	fun `#remove(pod) should remove pod in current namespace from allPods && namespacedPods provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.remove(pod)
		// then
		verify(namespacedPodsProvider).remove(pod)
		verify(allPodsProvider).remove(pod)
	}

	@Test
	fun `#remove(pod) should remove pod in non-current namespace from allPods provider but not from namespacedPods provider`() {
		// given
		val pod = resource<Pod>("pod", "42")
		// when
		context.remove(pod)
		// then
		verify(namespacedPodsProvider, never()).remove(pod)
		verify(allPodsProvider).remove(pod)
	}

	@Test
	fun `#remove(namespace) should remove from namespaces provider but not from pods provider`() {
		// given
		val namespace = NAMESPACE1
		// when
		context.remove(namespace)
		// then
		verify(namespacesProvider).remove(namespace)
		verify(namespacedPodsProvider, never()).remove(namespace)
	}

	@Test
	fun `#remove(pod) should fire if provider removed pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		doReturn(true)
				.whenever(namespacedPodsProvider).remove(pod)
		// when
		context.remove(pod)
		// then
		verify(observable).fireRemoved(pod)
	}

	@Test
	fun `#remove(pod) should not fire if provider did not remove pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		doReturn(false)
				.whenever(namespacedPodsProvider).remove(pod)
		// when
		context.remove(pod)
		// then
		verify(observable, never()).fireRemoved(pod)
	}

	@Test
	fun `#invalidate() should invalidate all resource providers`() {
		// given
		// when
		context.invalidate()
		// then
		verify(namespacesProvider).invalidate()
		verify(namespacedPodsProvider).invalidate()
	}

	@Test
	fun `#invalidate(resource) should invalidate resource provider`() {
		// given
		val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
		// when
		context.invalidate(pod)
		// then
		verify(namespacesProvider, never()).invalidate(any())
		verify(namespacedPodsProvider).invalidate(pod)
	}

	@Test
	fun `#close should close client`() {
		// given
		// when
		context.close()
		// then
		verify(client).close()
	}

	inner class TestableKubernetesContext(
			observable: ModelChangeObservable,
			client: NamespacedKubernetesClient,
			private val internalResourceProviders: List<IResourcesProvider<out HasMetadata>>,
			private val extensionResourcesProviders: List<IResourcesProvider<out HasMetadata>>)
		: KubernetesContext(observable, client, mock()) {

		public override var watch = mock<ResourceWatch>()

		public override fun getRetrieveOperations(namespace: String): List<() -> Watchable<Watch, Watcher<HasMetadata>>?> {
			TODO("override with mocking")
		}

		public override fun getInternalResourceProviders(client: NamespacedKubernetesClient)
				: List<IResourcesProvider<out HasMetadata>> {
			return internalResourceProviders
		}

		public override fun getExtensionResourceProviders(client: NamespacedKubernetesClient)
				: List<IResourcesProvider<out HasMetadata>> {
			return extensionResourcesProviders
		}

	}
}