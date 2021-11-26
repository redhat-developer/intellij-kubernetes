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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GenericCustomResourceTest {

    private val kind = "jedi"
    private val version = "old Republic"
    private val meta = ObjectMetaBuilder()
        .withNamespace("Tatooine")
        .withClusterName("Outer Rim")
        .withName("Hett")
        .withCreationTimestamp("17 BBY")
        .withResourceVersion("1")
        .withUid("004cf053-fbdd-4854-a9e4-9df2ba3c1035")
        .build()
    private val spec = mapOf(
        Pair("weapon", "lightsaber"),
        Pair("clan", "Tusken Raiders")
    )

    private val resource = GenericCustomResource(
        kind,
        version,
        meta,
        spec
    )

    @Test
    fun `should be equals if same instance`() {
        // given
        // when
        val areEquals = (resource == resource)
        // then
        assertThat(areEquals).isTrue()
    }

    @Test
    fun `should be equal if are equal`() {
        // given
        val otherMeta = ObjectMetaBuilder(resource.metadata).build()
        val otherSpec = spec.entries.associateBy({ it.key }, { it.value })
        val otherResource = GenericCustomResource(
            resource.kind,
            resource.apiVersion,
            otherMeta,
            otherSpec
        )
        // when
        val areEquals = (resource == otherResource)
        // then
        assertThat(areEquals).isTrue()
    }

    @Test
    fun `should NOT be equal if kind is different`() {
        // given
        val otherMeta = ObjectMetaBuilder(resource.metadata).build()
        val otherSpec = spec.entries.associateBy({ it.key }, { it.value })
        val otherResource = GenericCustomResource(
            "sith",
            resource.apiVersion,
            otherMeta,
            otherSpec
        )
        // when
        val areEquals = (resource == otherResource)
        // then
        assertThat(areEquals).isFalse()
    }

    @Test
    fun `should NOT be equal if apiVersion is different`() {
        // given
        val otherMeta = ObjectMetaBuilder(resource.metadata).build()
        val otherSpec = spec.entries.associateBy({ it.key }, { it.value })
        val otherResource = GenericCustomResource(
            resource.kind,
            "new Republic",
            otherMeta,
            otherSpec
        )
        // when
        val areEquals = (resource == otherResource)
        // then
        assertThat(areEquals).isFalse()
    }

    @Test
    fun `should NOT be equal if name is different`() {
        // given
        val otherMeta = ObjectMetaBuilder(resource.metadata)
            .withName("Kenobi")
            .build()
        val otherSpec = spec.entries.associateBy({ it.key }, { it.value })
        val otherResource = GenericCustomResource(
            resource.kind,
            resource.apiVersion,
            otherMeta,
            otherSpec
        )
        // when
        val areEquals = (resource == otherResource)
        // then
        assertThat(areEquals).isFalse()
    }

    @Test
    fun `should NOT be equal if 1 spec value is different`() {
        // given
        val otherMeta = ObjectMetaBuilder(resource.metadata).build()
        val otherSpec = spec.entries.associateBy(
            { it.key },
            {
                if (it.key == "weapon") {
                    "laser gun"
                } else {
                    it.value
                }
            })
        val otherResource = GenericCustomResource(
            resource.kind,
            resource.apiVersion,
            otherMeta,
            otherSpec
        )
        // when
        val areEquals = (resource == otherResource)
        // then
        assertThat(areEquals).isFalse()
    }

    @Test
    fun `should NOT be equal if spec has 1 more value`() {
        // given
        val otherMeta = ObjectMetaBuilder(resource.metadata).build()
        val otherSpec = mutableMapOf(Pair("wrapping", "mask"))
        spec.entries.associateByTo(otherSpec,
            { it.key }, { it.value })
        val otherResource = GenericCustomResource(
            resource.kind,
            resource.apiVersion,
            otherMeta,
            otherSpec
        )
        // when
        val areEquals = (resource == otherResource)
        // then
        assertThat(areEquals).isFalse()
    }
}