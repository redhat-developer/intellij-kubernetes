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
package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.model.cluster.ActiveContext
import org.jboss.tools.intellij.kubernetes.model.cluster.ClusterFactory
import org.jboss.tools.intellij.kubernetes.model.cluster.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.cluster.IContext
import org.jboss.tools.intellij.kubernetes.model.cluster.Context
import org.jboss.tools.intellij.kubernetes.model.util.KubeConfigClusters

interface IResourceModel {
    val allContexts: List<IContext>
    val currentContext: IActiveContext<out HasMetadata, out KubernetesClient>?
    fun getClient(): KubernetesClient?
    fun setCurrentContext(context: IContext)
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R>
    fun getKind(resource: HasMetadata): Class<out HasMetadata>
    fun invalidate(element: Any?)
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
}

class ResourceModel(
    private val observable: IModelChangeObservable = ModelChangeObservable(),
    private val clusterFactory: (IModelChangeObservable, NamedContext?) -> IActiveContext<out HasMetadata, out KubernetesClient> =
        ClusterFactory()::create
) : IResourceModel {

    private val config = KubeConfigClusters()

    private val _allContexts: MutableList<IContext> = mutableListOf()
    override val allContexts: List<IContext>
        get() {
            if (_allContexts.isEmpty()) {
                _allContexts.addAll(config.contexts.mapNotNull { createContext(it) })
            }
            return _allContexts
        }

    private fun createContext(context: NamedContext): IContext? {
        return if (config.isCurrent(context)) {
                currentContext
            } else {
                Context(context)
            }
    }

    override var currentContext: IActiveContext<out HasMetadata, out KubernetesClient>? = null
        get() {
            if (field == null) {
                field = createCurrentCluster()
            }
            return field
        }

    override fun setCurrentContext(context: IContext) {

    }

    private fun createCurrentCluster(): IActiveContext<out HasMetadata, out KubernetesClient> {
        val cluster = clusterFactory(observable, config.currentContext)
        cluster.startWatch()
        this.currentContext = cluster
        return cluster
    }

    private fun closeCurrentCluster() {
        currentContext?.close()
        currentContext == null
    }

    override fun getClient(): KubernetesClient? {
        return currentContext?.client
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener);
    }

    override fun setCurrentNamespace(namespace: String) {
        currentContext?.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return currentContext?.getCurrentNamespace() ?: return null
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${currentContext?.client?.masterUrl}", e)
        }
    }

    override fun <R: HasMetadata> getResources(kind: Class<R>): Collection<R> {
        try {
            return currentContext?.getResources(kind) ?: return emptyList()
        } catch (e: KubernetesClientException) {
            if (isNotFound(e)) {
                return emptyList()
            }
            throw ResourceException("Could not get ${kind.simpleName}s for server ${currentContext?.client?.masterUrl}", e)
        }
    }

    override fun getKind(resource: HasMetadata): Class<out HasMetadata> {
        return resource::class.java
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is ResourceModel -> invalidate()
            is ActiveContext<*, *> -> invalidate(element)
            is Class<*> -> invalidate(element)
            is HasMetadata -> invalidate(element)
        }
    }

    private fun invalidate() {
        closeCurrentCluster()
        createCurrentCluster()
        _allContexts.clear()
        observable.fireModified(this)
    }

    private fun invalidate(cluster: ActiveContext<*,*>) {
        closeCurrentCluster()
        createCurrentCluster()
        observable.fireModified(cluster)
    }

    private fun invalidate(kind: Class<*>) {
        val hasMetadataClass = kind as? Class<HasMetadata> ?: return
        currentContext?.invalidate(hasMetadataClass) ?: return
        observable.fireModified(hasMetadataClass)
    }

    private fun invalidate(resource: HasMetadata) {
        currentContext?.invalidate(resource) ?: return
        observable.fireModified(resource)
    }

    private fun isNotFound(e: KubernetesClientException): Boolean {
        return e.code == 404
    }

}