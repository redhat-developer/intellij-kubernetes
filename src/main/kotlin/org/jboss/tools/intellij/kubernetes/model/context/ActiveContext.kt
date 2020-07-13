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
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.apache.commons.lang3.BooleanUtils
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProviderFactory
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.CustomResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.GenericCustomResource

interface IActiveContext<N: HasMetadata, C: KubernetesClient>: IContext {

    enum class ResourcesIn {
        CURRENT_NAMESPACE, ANY_NAMESPACE, NO_NAMESPACE
    }

    val client: C
    fun isOpenShift(): Boolean
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R>
    fun getCustomResources(definition: CustomResourceDefinition, resourcesIn: ResourcesIn): Collection<GenericCustomResource>
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
    fun invalidate(kind: ResourceKind<*>)
    fun invalidate(resource: HasMetadata)
    fun startWatch()
    fun close()
}

abstract class ActiveContext<N : HasMetadata, C : KubernetesClient>(
        private val modelChange: IModelChangeObservable,
        final override val client: C,
        context: NamedContext
) : Context(context), IActiveContext<N, C> {

    private val extensionName: ExtensionPointName<IResourcesProviderFactory<HasMetadata, C, IResourcesProvider<HasMetadata>>> =
            ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.resourceProvider")
    protected open val namespacedProviders: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourcesProvider<out HasMetadata>> by lazy {
        val providers = getAllResourceProviders(INamespacedResourcesProvider::class.java)
        val namespace = getCurrentNamespace()
        providers.forEach { it.value.namespace = namespace }
        providers
    }
    protected open val nonNamespacedProviders: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourcesProvider<out HasMetadata>> by lazy {
        getAllResourceProviders(INonNamespacedResourcesProvider::class.java)
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

    override fun <R: HasMetadata> getResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R> {
        val provider: IResourcesProvider<R>? = getProvider(kind, resourcesIn)
        return provider?.getAllResources() ?: return emptyList()
    }

    private fun <R: HasMetadata> getProvider(kind: ResourceKind<R>, resourcesIn: ResourcesIn): IResourcesProvider<R>? {
        return when(resourcesIn) {
            ResourcesIn.CURRENT_NAMESPACE -> {
                namespacedProviders[kind] as? INamespacedResourcesProvider<R>
            }
            ResourcesIn.ANY_NAMESPACE,
            ResourcesIn.NO_NAMESPACE ->
                nonNamespacedProviders[kind] as? INonNamespacedResourcesProvider<R>
        }
    }

    override fun getCustomResources(definition: CustomResourceDefinition, resourcesIn: ResourcesIn)
            : Collection<GenericCustomResource> {
        val kind = ResourceKind.new(definition)
        val provider: IResourcesProvider<GenericCustomResource> = getCustomResourceProvider(kind, resourcesIn, definition)
        return provider.getAllResources()
    }

    private fun getCustomResourceProvider(
            kind: ResourceKind<GenericCustomResource>,
            resourcesIn: ResourcesIn,
            definition: CustomResourceDefinition)
            : IResourcesProvider<GenericCustomResource> {
        var provider: IResourcesProvider<GenericCustomResource>? = getProvider(kind, resourcesIn)
        if (provider !is CustomResourcesProvider) {
            provider = createCustomResourcesProvider(definition, getCurrentNamespace())
            setResourcesProvider(provider, kind, resourcesIn)
        }
        return provider
    }

    private fun setResourcesProvider(
            provider: IResourcesProvider<out HasMetadata>,
            kind: ResourceKind<GenericCustomResource>,
            resourcesIn: ResourcesIn
    ) {
        when (resourcesIn) {
            ResourcesIn.CURRENT_NAMESPACE ->
                namespacedProviders[kind] = provider as INamespacedResourcesProvider<out HasMetadata>
            ResourcesIn.ANY_NAMESPACE,
            ResourcesIn.NO_NAMESPACE ->
                nonNamespacedProviders[kind] = provider as INonNamespacedResourcesProvider<out HasMetadata>
        }
    }

    protected open fun createCustomResourcesProvider(definition: CustomResourceDefinition, namespace: String?)
            : IResourcesProvider<GenericCustomResource> {
        return CustomResourcesProvider(definition, namespace, client)
    }

    override fun add(resource: HasMetadata): Boolean {
        // we need to add resource to both
        return BooleanUtils.or(
                addToNamespacedProvider(resource),
                addToNonNamespacedProvider(resource)
        )
    }

    private fun addToNonNamespacedProvider(resource: HasMetadata): Boolean {
        val provider = getProvider(ResourceKind.new(resource::class.java), ResourcesIn.NO_NAMESPACE)
        return add(provider, resource)
    }

    private fun addToNamespacedProvider(resource: HasMetadata): Boolean {
        return if (getCurrentNamespace() == resource.metadata.namespace) {
            val provider = getProvider(ResourceKind.new(resource::class.java), ResourcesIn.CURRENT_NAMESPACE)
            add(provider, resource)
        } else {
            false
        }
    }

    private fun add(provider: IResourcesProvider<out HasMetadata>?, resource: HasMetadata): Boolean {
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
        return BooleanUtils.or(
                removeFromNonNamespacedProvider(resource),
                removeFromNamespacedProvider(resource)
        )
    }

    private fun removeFromNonNamespacedProvider(resource: HasMetadata): Boolean {
        val provider = getProvider(ResourceKind.new(resource::class.java), ResourcesIn.NO_NAMESPACE)
        return remove(provider, resource)
    }

    private fun removeFromNamespacedProvider(resource: HasMetadata): Boolean {
        return if (getCurrentNamespace() == resource.metadata.namespace) {
            val provider = getProvider(ResourceKind.new(resource::class.java), ResourcesIn.CURRENT_NAMESPACE)
            remove(provider, resource)
        } else {
            false
        }
    }

    private fun remove(provider: IResourcesProvider<out HasMetadata>?, resource: HasMetadata): Boolean {
        if (provider == null) {
            return false
        }
        val removed = provider.remove(resource)
        if (removed) {
            modelChange.fireRemoved(resource)
        }
        return removed
    }

    protected open fun getRetrieveOperations(namespace: String)
            : Collection<() -> Watchable<Watch, Watcher<HasMetadata>>?> {
        val resources: MutableList<() ->Watchable<Watch, Watcher<HasMetadata>>?> = mutableListOf()
        resources.addAll(namespacedProviders.values
                .map { it.getWatchable() as () -> Watchable<Watch, Watcher<HasMetadata>>? })
        resources.addAll(nonNamespacedProviders.values
                .map { it.getWatchable() as () -> Watchable<Watch, Watcher<HasMetadata>>? })
        return resources
    }

    override fun invalidate() {
        namespacedProviders.values.forEach { it.invalidate() }
        nonNamespacedProviders.values.forEach { it.invalidate() }
    }

    override fun invalidate(kind: ResourceKind<*>) {
        namespacedProviders[kind]?.invalidate()
        nonNamespacedProviders[kind]?.invalidate()
    }

    override fun invalidate(resource: HasMetadata) {
        namespacedProviders[ResourceKind.new(resource::class.java)]?.invalidate(resource)
        nonNamespacedProviders[ResourceKind.new(resource::class.java)]?.invalidate(resource)
    }

    override fun startWatch() {
        val namespace = getCurrentNamespace() ?: return
        startWatch(namespace)
    }

    private fun startWatch(namespace: String) {
        try {
            watch.watchAll(getRetrieveOperations(namespace))
        } catch (e: ResourceException) {
            logger<ActiveContext<N, C>>().warn("Could not start watching resources on server ${client.masterUrl}", e)
        }
    }

    private fun stopWatch(namespace: String) {
        watch.ignoreAll(getRetrieveOperations(namespace))
    }

    override fun close() {
        client.close()
    }

    private fun <P: IResourcesProvider<out HasMetadata>> getAllResourceProviders(type: Class<P>)
            : MutableMap<ResourceKind<out HasMetadata>, P> {
        val providers = mutableMapOf<ResourceKind<out HasMetadata>, P>()
        providers.putAll(
                getInternalResourceProviders(client)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        providers.putAll(
                getExtensionResourceProviders(client)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        return providers
    }

    protected abstract fun getInternalResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>>

    protected open fun getExtensionResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>> {
        return extensionName.extensionList
                .map { it.create(client) }
    }
}
