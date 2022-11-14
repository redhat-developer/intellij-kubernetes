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
package com.redhat.devtools.intellij.kubernetes.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.utils.ConfigHelper
import com.redhat.devtools.intellij.common.utils.ConfigWatcher
import com.redhat.devtools.intellij.common.utils.ExecHelper
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.context.Context
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.ResettableLazyProperty
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.NAME_PREFIX_CONTEXT
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_IS_OPENSHIFT
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_KUBERNETES_VERSION
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_OPENSHIFT_VERSION
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.NamedContextBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import java.nio.file.Paths

interface IAllContexts {
	/**
	 * The current context. Is also contained in [all].
	 */
	val current: IActiveContext<out HasMetadata, out KubernetesClient>?

	/**
	 * All contexts that are available. These are loaded from the kube-config file.
	 */
	val all: List<IContext>

	/**
	 * Sets the given context as current context. The old context is closed.
	 *
	 * @param context the context to set as current context
	 * @return new active context
	 *
	 * @see current
	 */
	fun setCurrentContext(context: IContext): IActiveContext<out HasMetadata, out KubernetesClient>?

	/**
	 * Sets the given namespace as current namespace.
	 * A new context is created and set. This is done because the fabric8 client is not able to switch namespace.
	 *
	 * @param namespace the namespace to set as current namespace
	 * @return new active context with the given namespace as current namespace
	 *
	 * @see current
	 */
	fun setCurrentNamespace(namespace: String): IActiveContext<out HasMetadata, out KubernetesClient>?

	/**
	 * Invalidates all contexts and the current context.
	 * Causes them to be reloaded from config when accessed.
	 */
	fun refresh()
}

open class AllContexts(
	private val contextFactory: (ClientAdapter<out KubernetesClient>, IResourceModelObservable) -> IActiveContext<out HasMetadata, out KubernetesClient>? =
		IActiveContext.Factory::create,
	private val modelChange: IResourceModelObservable,
	private val clientFactory: (String?, String?) -> ClientAdapter<out KubernetesClient> = ClientAdapter.Factory::create
) : IAllContexts {

	init {
		watchKubeConfig()
	}

	private val client = ResettableLazyProperty {
		clientFactory.invoke(null,null)
	}

	override val current: IActiveContext<out HasMetadata, out KubernetesClient>?
		get() {
			synchronized(this) {
				return findActive(all)
			}
		}

	override val all: MutableList<IContext> = mutableListOf()
		get() {
			synchronized(this) {
				if (field.isEmpty()) {
					val all = createContexts(client.get(), client.get()?.config)
					field.addAll(all)
				}
				return field
			}
		}

	override fun setCurrentContext(context: IContext): IActiveContext<out HasMetadata, out KubernetesClient>? {
		val new = setCurrentContext(context, current, emptyList())
		if (new != null) {
			modelChange.fireAllContextsChanged()
		}
		return new
	}

	override fun setCurrentNamespace(namespace: String): IActiveContext<out HasMetadata, out KubernetesClient>? {
		val old = this.current ?: return null
		val context = NamedContextBuilder(old.context).build()
		context.context.namespace = namespace
		val new = setCurrentContext(Context(context), old, old.getWatched())
		modelChange.fireCurrentNamespaceChanged(new, old)
		return new
	}

	private fun setCurrentContext(
		toSet: IContext,
		current: IActiveContext<out HasMetadata, out KubernetesClient>?,
		toWatch: Collection<ResourceKind<out HasMetadata>>?
	) : IActiveContext<out HasMetadata, out KubernetesClient>? {
		synchronized(this) {
			if (toSet == current) {
				return current
			}
			if (!exists(toSet, all)) {
				return null
			}
			recreateClient(
				toSet.context.context.namespace,
				toSet.context.name,
				client.get()
			)
			current?.close()
			all.clear() // causes reload of all contexts when accessed afterwards
			val newCurrent = this.current // gets new current from all
			if (toWatch != null) {
				newCurrent?.watchAll(toWatch)
			}
			return newCurrent
		}
	}

	override fun refresh() {
		synchronized(this) {
			this.current?.close()
			all.clear() // latter access will cause reload
			modelChange.fireAllContextsChanged()
		}
	}

	private fun findActive(all: List<IContext>): IActiveContext<out HasMetadata, out KubernetesClient>? {
		return if (all.isNotEmpty()) {
			val activeContext = all.firstOrNull { it.active }
			activeContext as? IActiveContext<out HasMetadata, out KubernetesClient>
		} else {
			null
		}
	}

	private fun createContexts(client: ClientAdapter<out KubernetesClient>?, config: ClientConfig?)
			: List<IContext> {
		if (client == null
			|| config == null
		) {
			return emptyList()
		}
		return config.allContexts
			.map {
				if (config.isCurrent(it)) {
					createActiveContext(client) ?: Context(it)
				} else {
					Context(it)
				}
			}
	}

	private fun recreateClient(namespace: String?, context: String?, client: ClientAdapter<out KubernetesClient>?)
			: ClientAdapter<out KubernetesClient> {
		client?.close()
		val new = clientFactory.invoke(namespace, context)
		new.config.save()
		this.client.set(new)
		return new
	}

	private fun createActiveContext(client: ClientAdapter<out KubernetesClient>?)
			: IActiveContext<out HasMetadata, out KubernetesClient>? {
		if (client == null) {
			return null
		}

		val context = contextFactory.invoke(client, modelChange) ?: return null
		reportTelemetry(context)
		return context
	}

	protected open fun reportTelemetry(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
		ExecHelper.submit {
			val telemetry = TelemetryService.instance.action(NAME_PREFIX_CONTEXT + "use")
				.property(PROP_IS_OPENSHIFT, context.isOpenShift().toString())
			try {
				telemetry
					.property(PROP_KUBERNETES_VERSION, context.version.kubernetesVersion)
					.property(PROP_OPENSHIFT_VERSION, context.version.openshiftVersion)
					.send()
			} catch (e: RuntimeException) {
				telemetry
					.property(PROP_KUBERNETES_VERSION, "error retrieving")
					.property(PROP_OPENSHIFT_VERSION, "error retrieving")
					.send()
				logger<AllContexts>().warn("Could not report context/cluster versions", e)
			}
		}
	}

	private fun exists(context: IContext, all: MutableList<IContext>): Boolean {
		return find(context, all) != null
	}

	private fun find(context: IContext, all: MutableList<IContext>): IContext? {
		val found = all.find { sameButNamespace(context.context, it.context) } ?: return null
		val indexOf = all.indexOf(found)
		if (indexOf < 0) {
			return null
		}
		return all[indexOf]
	}

	private fun sameButNamespace(toFind: NamedContext, toCheck: NamedContext): Boolean {
		return toFind.name == toCheck.name
				&& toFind.context.cluster == toCheck.context.cluster
				&& toFind.context.user == toCheck.context.user
	}

	protected open fun watchKubeConfig() {
		val path = Paths.get(ConfigHelper.getKubeConfigPath())
		/**
		 * [ConfigWatcher] cannot add/remove listeners nor can it get closed (and stop the [java.nio.file.WatchService]).
		 * We therefore have to create a single instance in here rather than using it in a shielded/private way within
		 * [com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig].
		 * Closing/Recreating [ConfigWatcher] is needed when used within [com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig].
		 * The latter gets closed/recreated whenever the context changes in
		 * [com.redhat.devtools.intellij.kubernetes.model.client.KubeConfigAdapter].
		 */
		val watcher = ConfigWatcher(path) { _, config -> onKubeConfigChanged(config) }
		runAsync(watcher::run)
	}

	protected open fun onKubeConfigChanged(fileConfig: io.fabric8.kubernetes.api.model.Config) {
		val client = client.get() ?: return
		val clientConfig = client.config.configuration
		if (ConfigHelper.areEqual(fileConfig, clientConfig)) {
			return
		}
		client.close()
		this.client.reset() // create new client when accessed
		refresh()
	}

	/** for testing purposes */
	protected open fun runAsync(runnable: () -> Unit) {
		ApplicationManager.getApplication().executeOnPooledThread(runnable)
	}
}