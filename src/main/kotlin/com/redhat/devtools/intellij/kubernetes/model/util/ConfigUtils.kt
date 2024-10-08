/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import io.fabric8.kubernetes.client.utils.KubernetesSerialization
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object ConfigUtils {

    fun getFileWithCurrentContext(): Pair<File, io.fabric8.kubernetes.api.model.Config>? {
        return Config.getKubeconfigFilenames()
            .asSequence()
            .mapNotNull { filepath: String? ->
                if (filepath != null) {
                    File(filepath)
                } else {
                    null
                }
            }
            .filter { file: File ->
                file.exists()
                        && file.isFile
            }
            .mapNotNull { file: File ->
                val config = KubeConfigUtils.parseConfig(file) ?: return@mapNotNull null
                Pair(file, config)
            }
            .filter { pair: Pair<File, io.fabric8.kubernetes.api.model.Config> ->
                pair.second.currentContext?.isNotEmpty() != null
            }
            .firstOrNull()
    }

    fun getCurrentContext(config: io.fabric8.kubernetes.api.model.Config): NamedContext? {
        return config.contexts.find {
            context -> context.name == config.currentContext
        }
    }


}
