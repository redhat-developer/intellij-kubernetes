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
package com.redhat.devtools.intellij.kubernetes.model.mocks

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.CREATION_TIMESTAMP
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.GENERATION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.LABELS
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.NAME
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.RESOURCE_VERSION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.SELF_LINK
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.UID
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.KIND
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.METADATA
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory.Companion.API_VERSION
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResourceFactory.SPEC
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.util.getApiVersion
import com.redhat.devtools.intellij.kubernetes.model.util.getHighestPriorityVersion
import io.fabric8.kubernetes.api.model.Context
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionNames
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.V1ApiextensionAPIGroupDSL
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ApiextensionsAPIGroupDSL
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.PodResource
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl
import org.mockito.ArgumentMatchers
import java.net.URL


typealias NamespaceListOperation =
        NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>>

object ClientMocks {

    val NAMESPACE1 = resource<Namespace>("namespace1", null, "nsUid1", "v1", "1")
    val NAMESPACE2 = resource<Namespace>("namespace2", null, "nsUid2","v1", "1")
    val NAMESPACE3 = resource<Namespace>("namespace3", null, "nsUid3", "v1", "1")

    val POD1 = resource<Pod>("pod1", "namespace1", "podUid1", "v1", "1")
    val POD2 = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
    val POD3 = resource<Pod>("pod3", "namespace3", "podUid3", "v1", "1")


    fun client(
        currentNamespace: String?,
        namespaces: Array<Namespace>,
        masterUrl: URL = URL("http://localhost"),
        customResourceDefinitions: List<CustomResourceDefinition> = emptyList()
    ): KubernetesClient {
        val namespacesMock = namespaceListOperation(namespaces)
        val config = mock<Config> {
            on { namespace } doReturn currentNamespace
        }
        val apiExtensions = apiextensionsOperation(customResourceDefinitions)

        return mock {
            on { namespaces() } doReturn namespacesMock
            on { namespace } doReturn currentNamespace
            on { configuration } doReturn config
            on { getMasterUrl() } doReturn masterUrl
            on { apiextensions() } doReturn apiExtensions
        }
    }

    private fun namespaceListOperation(namespaces: Array<Namespace>): NamespaceListOperation {
        val namespaceList = mock<NamespaceList> {
            on { items } doReturn namespaces.asList()
        }
        return mock {
            on { list() } doReturn namespaceList
        }
    }

    private fun apiextensionsOperation(definitions: List<CustomResourceDefinition>): ApiextensionsAPIGroupDSL {
        val v1CrdListOperation: CustomResourceDefinitionList = mock {
            on { items } doReturn definitions
        }
        val v1CrdsOperation = mock<MixedOperation<CustomResourceDefinition, CustomResourceDefinitionList, Resource<CustomResourceDefinition>>> {
            on { list() } doReturn v1CrdListOperation
        }
        val v1Operation: V1ApiextensionAPIGroupDSL = mock {
            on { customResourceDefinitions() } doReturn v1CrdsOperation
        }
        return mock {
            on { v1() } doReturn v1Operation
        }
    }


    fun inNamespace(mixedOp: MixedOperation<Pod, PodList, PodResource<Pod>>)
            : NonNamespaceOperation<Pod, PodList, PodResource<Pod>> {
        val nonNamespaceOperation: NonNamespaceOperation<Pod, PodList, PodResource<Pod>> = mock()
        whenever(mixedOp.inNamespace(ArgumentMatchers.anyString()))
            .doReturn(nonNamespaceOperation)
        return nonNamespaceOperation
    }

    fun pods(client: KubernetesClient)
            : MixedOperation<Pod, PodList, PodResource<Pod>> {
        val podsOp = mock<MixedOperation<Pod, PodList, PodResource<Pod>>>()
        whenever(client.pods())
            .doReturn(podsOp)
        return podsOp
    }

    fun list(nonNamespaceOperation: NonNamespaceOperation<Pod, PodList, PodResource<Pod>>)
            : PodList {
        val podList = mock<PodList>()
        whenever(nonNamespaceOperation.list())
            .doReturn(podList)
        return podList

    }

    fun list(mixedOp: MixedOperation<Pod, PodList, PodResource<Pod>>): PodList {
        val podList = mock<PodList>()
        whenever(mixedOp.list())
            .doReturn(podList)
        return podList
    }

    fun items(podList: PodList, vararg pods: Pod ) {
        val returnedPods = listOf(*pods)
        whenever(podList.items)
            .doReturn(returnedPods)
    }

    fun withName(op: NonNamespaceOperation<Pod, PodList, PodResource<Pod>>, pod: Pod) {
        val podResource = mock<PodResource<Pod>>()
        whenever(podResource.get())
            .doReturn(pod)
        whenever(op.withName(pod.metadata.name))
            .doReturn(podResource)
    }

    fun namedContext(name: String, namespace: String, cluster: String, user: String): NamedContext {
        val context: Context = kubeConfigContext(namespace, cluster, user)
        return namedContext(name, context)
    }

    fun namedContext(name: String, context: Context): NamedContext {
        return mock {
            on { this.name } doReturn name
            on { this.context } doReturn context
        }
    }

    fun kubeConfigContext(namespace: String, cluster: String, user: String): Context {
        return mock {
            on { this.namespace } doReturn namespace
            on { this.cluster } doReturn cluster
            on { this.user } doReturn user
        }
    }

    fun config(currentContext: NamedContext?, contexts: List<NamedContext>): Config {
        return mock {
            on { mock.currentContext } doReturn currentContext
            on { mock.contexts } doReturn contexts
        }
    }

    fun changeConfig(currentContext: NamedContext?, contexts: List<NamedContext>, config: Config) {
        doReturn(contexts)
            .whenever(config).contexts
        doReturn(currentContext)
            .whenever(config).currentContext
    }

    fun apiConfig(currentContext: String, contexts: List<NamedContext>): io.fabric8.kubernetes.api.model.Config {
        return mock {
            on { mock.currentContext } doReturn currentContext
            on { mock.contexts } doReturn contexts
        }
    }

    fun customResourceDefinition(
        name: String,
        namespace: String,
        uid: String,
        apiVersion: String,
        specVersion: String,
        specGroup: String?,
        specKind: String,
        specScope: String): CustomResourceDefinition {
        return customResourceDefinition(
            name,
            namespace,
            uid,
            apiVersion,
            listOf(customResourceDefinitionVersion(specVersion)),
            specGroup,
            specKind,
            specScope)
    }

    fun customResourceDefinition(
        name: String,
        namespace: String,
        uid: String,
        apiVersion: String,
        specVersions: List<CustomResourceDefinitionVersion>,
        specGroup: String?,
        specKind: String,
        specScope: String): CustomResourceDefinition {
        val names: CustomResourceDefinitionNames = mock {
            on { mock.kind } doReturn specKind
        }
        val spec = mock<CustomResourceDefinitionSpec> {
            on { mock.versions } doReturn specVersions
            on { mock.group } doReturn specGroup
            on { mock.names } doReturn names
            on { mock.scope } doReturn specScope
        }
        val definition = resource<CustomResourceDefinition>(name, namespace, uid, apiVersion)
        whenever(definition.spec)
            .doReturn(spec)
        return definition
    }

    fun customResourceDefinitionVersion(name: String): CustomResourceDefinitionVersion {
        return mock {
            on { getName() } doReturn name
        }
    }

    inline fun <reified T: HasMetadata> resource(
        name: String,
        namespace: String? = null,
        uid: String? = System.currentTimeMillis().toString(),
        apiVersion: String,
        resourceVersion: String? = System.currentTimeMillis().toString()): T {
        val metadata = objectMeta(name, namespace, uid, resourceVersion)
        return mock {
            on { getMetadata() } doReturn metadata
            on { getApiVersion() } doReturn apiVersion
            on { getKind() } doReturn T::class.java.simpleName
        }
    }

    fun customResource(
        name: String,
        namespace: String?,
        definition: CustomResourceDefinition,
        uid: String? = System.currentTimeMillis().toString(),
        resourceVersion: String = System.currentTimeMillis().toString()
    ): GenericCustomResource {
        val metadata = objectMeta(name, namespace, uid, resourceVersion)
        val apiVersion = getApiVersion(
            definition.spec.group,
            getHighestPriorityVersion(definition.spec)!!
        )
        val kind = definition.spec.names.kind
        return mock {
            on { getMetadata() } doReturn metadata
            on { getApiVersion() } doReturn apiVersion
            on { getKind() } doReturn kind
        }
    }

    fun customResourceMap(customResource: GenericCustomResource): Map<String, Any?> {
        return mapOf(
            KIND to customResource.kind as String,
            API_VERSION to customResource.apiVersion as String,
            METADATA to metadataMap(customResource.metadata),
            SPEC to customResource.spec
        )
    }

    private fun metadataMap(objectMeta: ObjectMeta): Map<String, Any> {
        return mapOf(
            CREATION_TIMESTAMP to objectMeta.creationTimestamp,
            GENERATION to objectMeta.generation,
            NAME to objectMeta.name,
            NAMESPACE to objectMeta.namespace,
            RESOURCE_VERSION to objectMeta.resourceVersion,
            SELF_LINK to objectMeta.selfLink,
            UID to objectMeta.uid,
            LABELS to objectMeta.labels
        )
    }

    fun namespacedCustomResourceOperation(resource: Map<String, Any?>, resources: Map<String, Any?>, watch: Watch): RawCustomResourceOperationsImpl {
        val op = spy(RawCustomResourceOperationsImpl(mock(), mock(), mock()))
        doReturn(resources)
            .whenever(op).list(any<String>())
        doReturn(watch)
            .whenever(op).watch(any(), any(), any<Map<String, String>>(), any<ListOptions>(), any<Watcher<String>>())
        doReturn(resource)
            .whenever(op).list(any<String>())
        doReturn(resource)
            .whenever(op).createOrReplace(any(), any<String>())
        doReturn(true)
            .whenever(op).delete(any(), any<String>())
        return op
    }

    fun objectMeta(
        name: String,
        namespace: String?,
        uid: String?,
        resourceVersion: String?
    ): ObjectMeta {
        return mock {
            on { getName() } doReturn name
            on { getNamespace() } doReturn namespace
            on { getUid() } doReturn uid
            on { getResourceVersion() } doReturn resourceVersion
        }
    }
}
