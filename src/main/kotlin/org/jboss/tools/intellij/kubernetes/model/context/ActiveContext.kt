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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.apache.commons.lang3.BooleanUtils.or
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.*
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProviderFactory

interface IActiveContext<N: HasMetadata, C: KubernetesClient>: IContext {

    enum class ResourcesIn {
        CURRENT_NAMESPACE, ANY_NAMESPACE, NO_NAMESPACE
    }

    val client: C
    fun isOpenShift(): Boolean
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <T: HasMetadata> getResources(kind: Class<T>, resourcesIn: ResourcesIn): Collection<T>
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
    fun invalidate(kind: Class<out HasMetadata>)
    fun invalidate(resource: HasMetadata)
    fun startWatch()
    fun close()
}

abstract class ActiveContext<N: HasMetadata, C: KubernetesClient>(
    private val modelChange: IModelChangeObservable,
    override val client: C,
    context: NamedContext
) : Context(context), IActiveContext<N, C> {

    private val extensionName: ExtensionPointName<IResourcesProviderFactory<HasMetadata, C, IResourcesProvider<HasMetadata>>> =
            ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.resourceProvider")

    protected open val namespacedProviders: Map<String, INamespacedResourcesProvider<HasMetadata>> by lazy {
        val namespacedProviders = getAllResourceProviders(INamespacedResourcesProvider::class.java)
                as Map<String, INamespacedResourcesProvider<HasMetadata>>
        val namespace = getCurrentNamespace()
        namespacedProviders.forEach { it.value.namespace = namespace }
        namespacedProviders
    }

    protected open val nonNamespacedProviders: Map<String, INonNamespacedResourcesProvider<HasMetadata>> by lazy {
        val nonNamespacedProviders = getAllResourceProviders(INonNamespacedResourcesProvider::class.java)
                as Map<String, INonNamespacedResourcesProvider<HasMetadata>>
        nonNamespacedProviders
    }

    protected var namespace: String? = client.configuration.namespace

    protected open var watch: ResourceWatch = ResourceWatch(
            addOperation = { add(it) },
            removeOperation = { remove(it) })

    override fun setCurrentNamespace(namespace: String) {
        val currentNamespace = getCurrentNamespace()
        if (namespace == currentNamespace) {
            return
        }

        if (currentNamespace != null) {
            stopWatch(currentNamespace)
        }
        client.configuration.namespace = namespace
        namespacedProviders.forEach {
            it.value.invalidate()
            it.value.namespace = namespace
        }
        startWatch(namespace)
        modelChange.fireCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        var name: String? = client.configuration.namespace
        if (name == null) {
            try {
                name = getNamespaces().firstOrNull()?.metadata?.name
            } catch (e: KubernetesClientException) {
                logger<ActiveContext<N, C>>().warn("Could not determine current namespace: loading all namespaces failed.", e)
            }
        }
        return name
    }

    protected abstract fun getNamespaces(): Collection<N>

    override fun <T: HasMetadata> getResources(kind: Class<T>, resourcesIn: ResourcesIn): Collection<T> {
        val resources = when(resourcesIn) {
            ResourcesIn.CURRENT_NAMESPACE -> {
                namespacedProviders[kind.name]?.getAllResources() ?: emptyList()
            }
            ResourcesIn.ANY_NAMESPACE,
            ResourcesIn.NO_NAMESPACE ->
                nonNamespacedProviders[kind.name]?.getAllResources() ?: emptyList()
        }
        return resources as Collection<T>
    }

    override fun add(resource: HasMetadata): Boolean {
        // we need to add resource to both
        return or(
                addToNamespacedProvider(resource),
                add(nonNamespacedProviders[resource::class.java.name], resource)
        )
    }

    private fun addToNamespacedProvider(resource: HasMetadata): Boolean {
        return if (getCurrentNamespace() == resource.metadata.namespace) {
            add(namespacedProviders[resource::class.java.name], resource)
        } else {
            false
        }
    }

    private fun add(provider: IResourcesProvider<HasMetadata>?, resource: HasMetadata): Boolean {
        if (provider == null) {
            return false
        }
        val added = provider.add(resource)
        if (added) {
            modelChange.fireAdded(resource)
        }
        return added
    }

    override fun remove(resource: HasMetadata): Boolean {
        // we need to remove resource from both
        return or(
                removeFromNamespacedProvider(resource),
                remove(nonNamespacedProviders[resource::class.java.name], resource)
        )
    }

    private fun removeFromNamespacedProvider(resource: HasMetadata): Boolean {
        return if (getCurrentNamespace() == resource.metadata.namespace) {
            remove(namespacedProviders[resource::class.java.name], resource)
        } else {
            false
        }
    }

    private fun remove(provider: IResourcesProvider<HasMetadata>?, resource: HasMetadata): Boolean {
        if (provider == null) {
            return false
        }
        val removed = provider.remove(resource)
        if (removed) {
            modelChange.fireRemoved(resource)
        }
        return removed
    }

    protected open fun getWatchableResources(namespace: String): Collection<() -> Watchable<Watch, Watcher<HasMetadata>>?> {
        val resources: MutableList<() ->Watchable<Watch, Watcher<HasMetadata>>?> = mutableListOf()
        resources.addAll(namespacedProviders.values
                        .map { it.getWatchableResource() })
        resources.addAll(nonNamespacedProviders.values
                .map { it.getWatchableResource() })
        return resources
    }

    override fun invalidate() {
        namespacedProviders.values.forEach { it.invalidate() }
        nonNamespacedProviders.values.forEach { it.invalidate() }
    }

    override fun invalidate(kind: Class<out HasMetadata>) {
        namespacedProviders[kind.name]?.invalidate()
        nonNamespacedProviders[kind.name]?.invalidate()
    }

    override fun invalidate(resource: HasMetadata) {
        namespacedProviders[resource::class.java.name]?.invalidate(resource)
        nonNamespacedProviders[resource::class.java.name]?.invalidate(resource)
    }

    override fun startWatch() {
        val namespace = getCurrentNamespace() ?: return
        startWatch(namespace)
    }

    private fun startWatch(namespace: String) {
        try {
            watch.watchAll(getWatchableResources(namespace))
        } catch (e: ResourceException) {
            logger<ActiveContext<N, C>>().warn("Could not start watching resources on server ${client.masterUrl}", e)
        }
    }

    private fun stopWatch(namespace: String) {
        watch.ignoreAll(getWatchableResources(namespace))
    }

    override fun close() {
        client.close()
    }

    private fun <P: IResourcesProvider<out HasMetadata>> getAllResourceProviders(providerType: Class<P>): Map<String, P> {
        val providers = mutableMapOf<String, P>()
        providers.putAll(
                getInternalResourceProviders(client)
                        .filterIsInstance(providerType)
                        .associateBy { it.kind.name })
        providers.putAll(
                getExtensionResourceProviders(client)
                        .filterIsInstance(providerType)
                        .associateBy { it.kind.name })
        return providers
    }

    protected abstract fun getInternalResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>>

    protected open fun getExtensionResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>> {
        return extensionName.extensionList
                .map { it.create(client) }
    }
}
