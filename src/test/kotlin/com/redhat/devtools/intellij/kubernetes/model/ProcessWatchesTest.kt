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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableExec
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableLog
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableProcess
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.DeploymentsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.JobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.api.model.Build
import java.io.OutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ProcessWatchesTest {

    private val currentNamespace = ClientMocks.NAMESPACE2
    private val allNamespaces = arrayOf(ClientMocks.NAMESPACE1, ClientMocks.NAMESPACE2, ClientMocks.NAMESPACE3)
    private val allPods = arrayOf(ClientMocks.POD1, ClientMocks.POD2, ClientMocks.POD3)

    private val container1: Container = mock()
    private val container2: Container = mock()
    private val pod = ClientMocks.POD2.apply {
        ClientMocks.setContainers(this, container1, container2)
    }

    private val client = KubeClientAdapter(ClientMocks.client(currentNamespace.metadata.name, allNamespaces))

    private val podsWatchingOperator = Mocks.logAndExecWatchingNamespacedResourceOperator<Pod, KubernetesClient>(
        AllPodsOperator.KIND,
        allPods.toList(),
        currentNamespace
    )
    private val jobsWatchingOperator = Mocks.logAndExecWatchingNamespacedResourceOperator<Job, KubernetesClient>(
        JobsOperator.KIND,
        emptyList(),
        currentNamespace
    )
    private val buildsWatchingOperator = Mocks.logAndExecWatchingNamespacedResourceOperator<Build, KubernetesClient>(
        BuildsOperator.KIND,
        emptyList(),
        currentNamespace
    )
    private val deploymentWatchingOperator = Mocks.logAndExecWatchingNamespacedResourceOperator<Deployment, KubernetesClient>(
        DeploymentsOperator.KIND,
        emptyList(),
        currentNamespace
    )

    private val clientFactory: (String?, String?) -> ClientAdapter<out KubernetesClient> =
        mock {
            on { invoke(anyOrNull(), anyOrNull()) } doReturn client
        }
    private val operators = mapOf(
        /* tests require BuildOperator NOT to be supported */
        jobsWatchingOperator.kind to
                Pair(
                    jobsWatchingOperator::class.java as Class<IWatchableProcess<*>>,
                    { _: ClientAdapter<*> -> jobsWatchingOperator as IWatchableLog<HasMetadata> }),
        podsWatchingOperator.kind to
                Pair(
                    podsWatchingOperator::class.java as Class<IWatchableProcess<*>>,
                    { _: ClientAdapter<*> -> podsWatchingOperator as IWatchableLog<HasMetadata> }),
        deploymentWatchingOperator.kind to
                Pair(
                    deploymentWatchingOperator::class.java as Class<IWatchableProcess<*>>,
                    { _: ClientAdapter<*> -> deploymentWatchingOperator as IWatchableLog<HasMetadata> })
        )

    private val watches = TestableProcessWatches(clientFactory, operators)

    @Test
    fun `#watchLog should create new client with null context and null namespace`() {
        // given
        // when
        watches.watchLog(container1, pod, mock())
        // then
        verify(clientFactory).invoke(isNull(), isNull())
    }

    @Test
    fun `#watchLog should call #watchLog on the first operator that supports given kind and watching the log`() {
        // given
        val expected = podsWatchingOperator as IWatchableLog<Pod>
        val out: OutputStream = mock()
        // when
        watches.watchLog(container1, pod, out)
        // then
        verify(expected).watchLog(container1, pod, out)
    }

    @Test
    fun `#watchLog should return null if no operator was found that supports watching the log`() {
        // given
        val build = resource<Build>("death star")
        // when
        val log = watches.watchLog(container1, build, mock())
        // then
        assertThat(log).isNull()
    }

    @Test
    fun `#stopWatchLog should return true if existing watch is stopped`() {
        // given
        val watch = watches.watchLog(container1, pod, mock())
        assertThat(watch).isNotNull
        // when
        val stopped = watches.stopWatchLog(watch!!)
        // then
        assertThat(stopped).isTrue
    }

    @Test
    fun `#stopWatchLog should return false if watch does not exist`() {
        // given
        // when
        val stopped = watches.stopWatchLog(mock())
        // then
        assertThat(stopped).isFalse
    }

    @Test
    fun `#canWatchLog should return true if there is an operator that supports given kind and watching the log`() {
        // given
        // when
        val canWatchLog = watches.canWatchLog(pod)
        // then
        assertThat(canWatchLog).isTrue
    }

    @Test
    fun `#canWatchLog should false if no operator exists that supports watching the log for the given resource`() {
        // given
        // when
        val canWatchLog = watches.canWatchLog(resource<Build>("rebel base"))
        // then
        assertThat(canWatchLog).isFalse
    }

    @Test
    fun `#watchExec should create new client with null context and null namespace`() {
        // given
        // when
        watches.watchExec(container1, pod, mock())
        // then
        verify(clientFactory).invoke(isNull(), isNull())
    }

    @Test
    fun `#watchExec should call #watchExec on the first operator that supports given kind and watching exec`() {
        // given
        val expected = podsWatchingOperator as IWatchableExec<Pod>
        // when
        watches.watchExec(container1, pod, mock())
        // then
        verify(expected).watchExec(eq(container1), eq(pod), any())
    }

    @Test
    fun `#watchExec should return null if no operator was found that supports watching exec`() {
        // given
        val build = resource<Build>("death star")
        // when
        val execWatch = watches.watchExec(container1, build, mock())
        // then
        assertThat(execWatch).isNull()
    }

    @Test
    fun `#stopWatchExec should return true if existing watch is stopped`() {
        // given
        val watch = watches.watchExec(container1, pod, mock())
        assertThat(watch).isNotNull
        // when
        val stopped = watches.stopWatchExec(watch!!)
        // then
        assertThat(stopped).isTrue
    }

    @Test
    fun `#stopWatchExec should return false if watch does not exist`() {
        // given
        // when
        val stopped = watches.stopWatchExec(mock())
        // then
        assertThat(stopped).isFalse
    }

    @Test
    fun `#canWatchExec should return true if there is an operator that supports watching the exec for the given resource`() {
        // given
        // when
        val canWatchExec = watches.canWatchExec(ClientMocks.POD2)
        // then
        assertThat(canWatchExec).isTrue
    }

    @Test
    fun `#canWatchExec should return false if there is no operator that supports watching the exec for the given resource`() {
        // given
        // when
        val canWatchExec = watches.canWatchExec(resource<Build>("cruiser"))
        // then
        assertThat(canWatchExec).isFalse
    }

    class TestableProcessWatches(
        clientFactory: (String?, String?) -> ClientAdapter<out KubernetesClient>,
        private val specs: Map<out ResourceKind<*>, Pair<Class<IWatchableProcess<*>>, (ClientAdapter<*>) -> IWatchableProcess<*>>>
    ) : ProcessWatches(clientFactory) {
        override val operators: Map<ResourceKind<out HasMetadata>, OperatorSpecs> by lazy {
            specs.asIterable()
                .associate { entry ->
                    val pair = entry.value
                    entry.key to OperatorSpecs(pair.first, pair.second)
                }
        }

    }

}