/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.helm

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.runtime.RawExtension
import lombok.Data

@Data
@JsonDeserialize(using = HelmRelease.HelmReleaseDeserializer::class)
class HelmRelease(
    private var meta: ObjectMeta,
    var revision: Int,
    var updated: String,
    var status: String,
    var chart: String,
    var api: String,
    @JsonAlias("app_version") private var appVersion: String,
    val isHistory: Boolean = false
) : RawExtension(), HasMetadata, Namespaced {
    companion object {
        val KIND = ResourceKind.create(HelmRelease::class.java)
        const val FILE_NAME_PREFIX = "helmrelease"

        @JvmStatic
        fun from(
            name: String,
            namespace: String,
            revision: Int? = -1,
            updated: String? = "",
            status: String? = "",
            chart: String? = "",
            api: String? = "v1",
            appVersion: String? = "",
            isHistory: Boolean = false
        ): HelmRelease {
            val metadata = ObjectMeta()
            metadata.name = name
            metadata.namespace = namespace
            return HelmRelease(
                metadata,
                revision ?: -1,
                updated ?: "",
                status ?: "",
                chart ?: "",
                api ?: "v1",
                appVersion ?: "",
                isHistory
            )
        }
    }

    val kind = KIND

    class HelmReleaseDeserializer : JsonDeserializer<HelmRelease>() {
        override fun deserialize(jp: JsonParser?, p1: DeserializationContext?): HelmRelease? {
            if (null == jp) return null
            val node = jp.codec.readTree<JsonNode>(jp)
            val revision = node.get("revision").asInt()
            val updated = node.get("updated").asText()
            val status = node.get("status").asText()
            val chart = node.get("chart").asText()
            val appVersion = node.get("app_version").asText()
            val metadata = ObjectMeta()
            if (node.has("name")) {
                metadata.name = node.get("name").asText()
            } else {
                metadata.name = "v$revision"
            }
            var isHistory = false
            if (node.has("namespace")) {
                metadata.namespace = node.get("namespace").asText()
            } else {
                isHistory = true
            }
            return HelmRelease(metadata, revision, updated, status, chart, "v1", appVersion, isHistory)
        }
    }

    override fun getMetadata(): ObjectMeta = this.meta

    override fun setMetadata(metadata: ObjectMeta?) {
        if (null != metadata) this.meta = metadata
    }

    override fun setApiVersion(version: String?) {
        if (null != version) this.api = version
    }

    override fun getApiVersion(): String = this.api

    override fun getValue(): String {
        return if (isHistory) {
            "v" + this.revision
        } else {
            this.meta.name + "(" + this.chart + ")"
        }
    }

    override fun toString(): String = getValue()
}