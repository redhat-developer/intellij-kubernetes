/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableExec
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableLog
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableProcess
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.JobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.LogWatch
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

open class ProcessWatches(
    private val clientFactory: (String?, String?) -> ClientAdapter<out KubernetesClient>
    = { namespace: String?, context: String? -> ClientAdapter.Factory.create(namespace, context) }
) {

    @Suppress("UNCHECKED_CAST")
    protected open val operators: Map<ResourceKind<out HasMetadata>, OperatorSpecs> = mapOf(
        AllPodsOperator.KIND to OperatorSpecs(
            AllPodsOperator::class.java,
            ::AllPodsOperator
        ),
        JobsOperator.KIND to OperatorSpecs(
            JobsOperator::class.java,
            ::JobsOperator
        ),
        BuildsOperator.KIND to OperatorSpecs(
            BuildsOperator::class.java,
            ::BuildsOperator as (ClientAdapter<out KubernetesClient>) -> IWatchableProcess<*>
        )
    )

    private val watches: ConcurrentHashMap<Closeable, IWatchableProcess<*>> = ConcurrentHashMap()

    fun canWatchLog(resource: HasMetadata): Boolean {
        return getOperatorFactory<IWatchableLog<HasMetadata>>(resource) != null
    }

    fun watchLog(container: Container, resource: HasMetadata, out: OutputStream): LogWatch? {
        logger<ProcessWatches>().debug("Watching log of container in ${toMessage(resource, -1)}")
        val operator = createOperator<IWatchableLog<HasMetadata>>(resource) ?: return null
        return try {
            val watch = operator.watchLog(container, resource, out)
            storeOperator(watch, operator)
            watch
        } catch (e: KubernetesClientException) {
            throw ResourceException("Could not watch log of ${toMessage(resource, -1)}", e)
        } catch (e: IOException) {
            // WebSocketHandshakeException
            throw ResourceException("Could not watch log of ${toMessage(resource, -1)}", e)
        }
    }

    fun stopWatchLog(watch: LogWatch): Boolean {
        logger<ProcessWatches>().debug("Closing log watch $watch.")
        return stopWatch(watch, watches[watch])
    }

    fun canWatchExec(resource: HasMetadata): Boolean {
        return getOperatorFactory<IWatchableExec<HasMetadata>>(resource) != null
    }

    fun watchExec(container: Container, resource: HasMetadata, listener: ExecListener): ExecWatch? {
        logger<ProcessWatches>().debug("Watching exec of container \"${container.name}\" in ${toMessage(resource, -1)}.")
        val operator = createOperator<IWatchableExec<HasMetadata>>(resource)
        return try {
            val watch = operator?.watchExec(container, resource, listener)
            storeOperator(watch, operator)
            watch
        } catch (e: Throwable) {
            // KubernetesClientException
            // IOException
            throw ResourceException(
                "Could not connect to container \"${container.name}\" in ${toMessage(resource, 30)}.",
                e, listOf(resource)
            )
        }
    }

    fun stopWatchExec(watch: ExecWatch): Boolean {
        logger<ProcessWatches>().debug("Closing exec watch $watch.")
        return stopWatch(watch, watches[watch])
    }

    private fun stopWatch(watch: Closeable, operator: IWatchableProcess<*>?): Boolean {
        return try {
            watch.close()
            operator?.close()
            operator != null
        } catch (e: Exception) {
            logger<ProcessWatches>().warn(
                "Could not close exec watch $watch",
                e.cause)
            false
        }
    }

    private inline fun <reified W : IWatchableProcess<*>> createOperator(resource: HasMetadata): W? {
        val client = clientFactory.invoke(null, null)
        return getOperatorFactory<W>(resource)?.invoke(client) as? W
    }

    private inline fun <reified W : IWatchableProcess<*>> getOperatorFactory(resource: HasMetadata)
            : ((adapter: ClientAdapter<out KubernetesClient>) -> IWatchableProcess<*>)? {
        val spec = operators.entries.find { entry ->
            val kind = entry.key
            val operatorAttributes = entry.value
            kind.clazz == resource::class.java
                    && operatorAttributes.implements(W::class.java)
        }?.value
        return spec?.factory
    }

    private fun storeOperator(watch: Closeable?, operator: IWatchableProcess<HasMetadata>?) {
        if (watch == null
            || operator == null) {
            return
        }
        watches[watch] = operator
    }

    protected class OperatorSpecs(
        private val clazz: Class<out IWatchableProcess<*>>,
        val factory: (ClientAdapter<*>) -> IWatchableProcess<*>
    ) {
        fun implements(requested: Class<*>): Boolean {
            return requested.isAssignableFrom(clazz)
        }
    }

}