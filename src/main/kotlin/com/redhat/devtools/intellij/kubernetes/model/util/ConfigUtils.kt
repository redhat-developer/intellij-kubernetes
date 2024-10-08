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

import io.fabric8.kubernetes.api.model.Config
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import java.io.File

object ConfigUtils {

    fun getFileWithCurrentContext(): Pair<File, Config>? {
        return io.fabric8.kubernetes.client.Config.getKubeconfigFilenames()
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
            .filter { pair: Pair<File, Config> ->
                pair.second.currentContext?.isNotEmpty() != null
            }
            .firstOrNull()
    }

    fun getCurrentContext(config: Config): NamedContext? {
        return config.contexts.find {
            context -> context.name == config.currentContext
        }
    }


}
