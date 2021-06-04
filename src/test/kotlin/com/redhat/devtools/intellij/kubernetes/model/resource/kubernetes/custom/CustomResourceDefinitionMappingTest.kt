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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinition
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinitionVersion
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata.HasMetadataResource
import com.redhat.devtools.intellij.kubernetes.model.util.getApiVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URL

class CustomResourceDefinitionMappingTest {

    companion object {
        private val group = null
        private const val version = "v1"
        private val neo = resource<HasMetadataResource>("neo", "zion", "uid", getApiVersion(group, version), "1")
        private val kind = neo.kind!!
        private val morpheus =
            resource<HasMetadataResource>("morpheus", "zion", "uid", getApiVersion(group, "v2021"), "1")

        private val CRD_NOT_MATCHING = customResourceDefinition(
            "crd1", "ns1", "uid1", "apiVersion1",
            version,
            listOf(
                customResourceDefinitionVersion("v42")
            ),
            group,
            kind,
            CustomResourceScope.CLUSTER
        )
        private val CRD_MATCHING_NEO = customResourceDefinition(
            "crd2", "ns2", "uid2", "apiVersion2",
            version,
            listOf(
                customResourceDefinitionVersion("84"),
                customResourceDefinitionVersion(version),
                customResourceDefinitionVersion("v21")
            ),
            group,
            kind,
            CustomResourceScope.CLUSTER
        )
        private val CRD_NOT_MATCHING_2 = customResourceDefinition(
            "crd3", "ns3", "uid3", "apiVersion3",
            version,
            listOf(
                customResourceDefinitionVersion("v168")
            ),
            group,
            kind,
            CustomResourceScope.CLUSTER
        )
        private val CRD_MATCHING_NEO_2 = customResourceDefinition(
            "crd4", "ns4", "uid4", "apiVersion4",
            version,
            listOf(
                customResourceDefinitionVersion("v11"),
                customResourceDefinitionVersion("v22"),
                customResourceDefinitionVersion(version)
            ),
            group,
            kind,
            CustomResourceScope.CLUSTER
        )
    }

    @Test
    fun `#getDefinitionFor should return crd that has spec with same kind, group and version`() {
        // given
        val crds = listOf(CRD_NOT_MATCHING, CRD_MATCHING_NEO, CRD_NOT_MATCHING_2)
        // when
        val crd = CustomResourceDefinitionMapping.getDefinitionFor(neo, crds)
        // then
        assertThat(crd).isEqualTo(CRD_MATCHING_NEO)
    }

    @Test
    fun `#getDefinitionFor should return null if has no crd with spec that matches resource`() {
        // given
        val crds = listOf(CRD_NOT_MATCHING, CRD_NOT_MATCHING_2)
        // when
        val crd = CustomResourceDefinitionMapping.getDefinitionFor(neo, crds)
        // then
        assertThat(crd).isNull()
    }

    @Test
    fun `#getDefinitionFor should return first crd that matches if there are several ones`() {
        // given crd2 & crd4 are matching
        val crds = listOf(CRD_NOT_MATCHING, CRD_MATCHING_NEO, CRD_NOT_MATCHING_2, CRD_MATCHING_NEO_2 )
        // when
        val crd = CustomResourceDefinitionMapping.getDefinitionFor(neo, crds)
        // then crd2 is returned
        assertThat(crd).isEqualTo(CRD_MATCHING_NEO)
    }

    @Test
    fun `#isCustomResource should return true if given crds have a spec that matches resource`() {
        // given
        val crds = listOf(CRD_NOT_MATCHING, CRD_MATCHING_NEO, CRD_NOT_MATCHING_2)
        // when
        val isCustomResource = CustomResourceDefinitionMapping.isCustomResource(neo, crds)
        // then
        assertThat(isCustomResource).isTrue()
    }

    @Test
    fun `#isCustomResource should return false if given crds have DO NOT have a spec that matches resource`() {
        // given
        val crds = listOf(CRD_NOT_MATCHING, CRD_MATCHING_NEO, CRD_NOT_MATCHING_2)
        // when
        val isCustomResource = CustomResourceDefinitionMapping.isCustomResource(morpheus, crds)
        // then
        assertThat(isCustomResource).isFalse()
    }

    @Test
    fun `#getDefinitions should query cluster for existing definitions`() {
        // given
        val crds = listOf(CRD_NOT_MATCHING, CRD_MATCHING_NEO, CRD_NOT_MATCHING_2)
        val client = client("currentNamespace",
            emptyArray(),
            URL("https://kubernetes.cluster"),
            crds)
        // when
        CustomResourceDefinitionMapping.getDefinitions(client)
        // then
        verify(client.apiextensions().v1beta1().customResourceDefinitions().list()).items
    }
}