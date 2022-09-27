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

import io.fabric8.kubernetes.api.model.Config
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import java.io.File

/**
 * A class that allows to access the kube config file that's by default at ~/.kube/config
 * (but may be configured to be at a different location). This class respects this by relying on the
 * {@link io.fabric8.kubernetes.client.Config} for the location instead of using a hard coded path.
 */
class KubeConfigAdapter {

    private val file: File by lazy {
        File(io.fabric8.kubernetes.client.Config.getKubeconfigFilename())
    }

    fun exists(): Boolean {
        return file.exists()
    }

    fun load(): Config? {
        if (!exists()) {
            return null
        }
        return KubeConfigUtils.parseConfig(file)
    }

    fun save(config: Config) {
        KubeConfigUtils.persistKubeConfigIntoFile(config, file.absolutePath)
    }
}