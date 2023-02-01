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
package com.redhat.devtools.intellij.kubernetes.model.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.redhat.devtools.intellij.kubernetes.model.helm.HelmRelease
import io.fabric8.kubernetes.api.model.HasMetadata

class NativeHelm {
    companion object Constants {
        private const val bin: String = "helm"
        private val mapper = ObjectMapper()

        @JvmStatic
        val version: (target: HasMetadata) -> String = { KubernetesRelated.command(bin, "version") }
        val list: (namespace: String?) -> List<HelmRelease> =
            { mapper.readValue(KubernetesRelated.command(bin, "list", true, null, null, it), ListOfHelmRelease()) }
        val get: (release: String, namespace: String?) -> HelmRelease? =
            { release, namespace ->
                mapper.readValue(
                    KubernetesRelated.command(
                        bin,
                        "list",
                        true,
                        null,
                        null,
                        namespace,
                        listOf("-f", release)
                    ), ListOfHelmRelease()
                ).firstOrNull()
            }
        val history: (release: String, namespace: String?) -> List<HelmRelease> =
            { release, namespace ->
                mapper.readValue(
                    KubernetesRelated.command(
                        bin,
                        "history",
                        true,
                        null,
                        release,
                        namespace
                    ), ListOfHelmRelease()
                ).map { it.metadata.namespace = namespace; it }
            }
        val values: (release: String, namespace: String) -> String = { release, namespace ->
            KubernetesRelated.command(bin, listOf("get", "values"), false, null, release, namespace)
        }
        val upgrade: (release: HelmRelease, repository: String, values: ByteArray) -> String =
            { release, repository, values ->
                val versionStart = release.chart.lastIndexOf("-")
                val chartName = release.chart.substring(0, versionStart)
                val chartVersion = release.chart.substring(versionStart + 1)
                KubernetesRelated.command(
                    bin,
                    listOf("upgrade", release.metadata.name, "${repository}/${chartName}"),
                    false,
                    null,
                    null,
                    release.metadata.namespace,
                    listOf("--version", chartVersion, "-f", "-"),
                    values
                )
            }
        val delete: (release: String, namespace: String?) -> Boolean = { release, namespace ->
            KubernetesRelated.command(bin, "delete", false, null, release, namespace)
            true
        }

        val ready = KubernetesRelated.ready(bin)

        @JvmStatic
        fun isReady(): Boolean = ready
    }

    class ListOfHelmRelease : TypeReference<List<HelmRelease>>()
}