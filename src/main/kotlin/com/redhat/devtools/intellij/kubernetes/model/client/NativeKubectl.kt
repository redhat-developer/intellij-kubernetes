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
package com.redhat.devtools.intellij.kubernetes.model.client;

import io.fabric8.kubernetes.api.model.HasMetadata

class NativeKubectl {
    companion object Constants {
        private const val bin: String = "kubectl"

        @JvmStatic
        val describe: (target: HasMetadata) -> String =
            { KubernetesRelated.command(bin, "describe", false, it.kind, it.metadata.name, it.metadata.namespace) }

        val ready = KubernetesRelated.ready(bin)

        @JvmStatic
        fun isReady(): Boolean = ready
    }
}
