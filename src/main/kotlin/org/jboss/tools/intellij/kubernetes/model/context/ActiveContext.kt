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
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProviderFactory
import java.util.function.Predicate

abstract class ActiveContext<N: HasMetadata, C: KubernetesClient>(
    private val modelChange: IModelChangeObservable,
    override val client: C,
    context: NamedContext
) : Context(context), IActiveContext<N, C> {

    private val extensionName : ExtensionPointName<IResourcesProviderFactory<HasMetadata, C, IResourcesProvider<HasMetadata>>> =
        ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.resourceProvider")

    private var _resourceProviders: MutableMap<String, IResourcesProvider<out HasMetadata>> = mutableMapOf()
    protected open val resourceProviders: Map<String, IResourcesProvider<out HasMetadata>>
        get() {
            if (_resourceProviders.isEmpty()) {
                _resourceProviders.putAll(
                        getInternalResourceProviders(client).associateBy { it.kind.name })
                _resourceProviders.putAll(
                        getExtensionResourceProviders(client).associateBy { it.kind.name })
            }
            return _resourceProviders
        }

    protected var namespace: String? = client.configuration.namespace

    protected open var watch: ResourceWatch =
        ResourceWatch(
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
        invalidateNamespacedProviders()
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

    override fun <T: HasMetadata> getResources(kind: Class<T>): Collection<T> {
        val provider = resourceProviders[kind.name] ?: return emptyList()
        return getResources(provider) as Collection<T>
    }

    private fun <T: HasMetadata> getResources(provider: IResourcesProvider<T>): Collection<T> {
        return when(provider) {
            is INonNamespacedResourcesProvider ->
                provider.getAllResources()
            is INamespacedResourcesProvider -> {
                val namespace = getCurrentNamespace() ?: return emptyList()
                provider.getAllResources(namespace)
            }
            else -> emptyList()
        }
    }

    override fun add(resource: HasMetadata): Boolean {
        val provider = resourceProviders[resource::class.java.name] ?: return false
        val added = provider.add(resource)
        if (added) {
            modelChange.fireAdded(resource)
        }
        return added
    }

    override fun remove(resource: HasMetadata): Boolean {
        val provider = resourceProviders[resource::class.java.name] ?: return false
        val removed = provider.remove(resource)
        if (removed) {
            modelChange.fireRemoved(resource)
        }
        return removed
    }

    protected open fun getWatchableResources(namespace: String): List<() -> Watchable<Watch, Watcher<HasMetadata>>?> {
        return resourceProviders.values
                .map {
                    when (it) {
                        is INamespacedResourcesProvider ->
                            it.getWatchableResource(namespace)
                        is INonNamespacedResourcesProvider ->
                            it.getWatchableResource()
                        else -> { -> null }
                    }
                } as List<() -> Watchable<Watch, Watcher<HasMetadata>>?>
    }

    override fun invalidate() {
        resourceProviders.values.forEach { it.invalidate() }
    }

    override fun invalidate(kind: Class<out HasMetadata>) {
        resourceProviders[kind.name]?.invalidate()
    }

    override fun invalidate(resource: HasMetadata) {
        resourceProviders[resource::class.java.name]?.invalidate(resource)
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

    private fun invalidateNamespacedProviders() {
        resourceProviders
                .filter { it.value is INamespacedResourcesProvider<*> }
                .forEach { (it.value as INamespacedResourcesProvider).invalidate() }
    }

    protected abstract fun getInternalResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>>

    private fun getExtensionResourceProviders(client: C): List<IResourcesProvider<HasMetadata>> {
        return extensionName.extensionList.map {
            it.create(client)
        }
    }

}
