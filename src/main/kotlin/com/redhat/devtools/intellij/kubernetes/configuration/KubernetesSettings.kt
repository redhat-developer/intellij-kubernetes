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
package com.redhat.devtools.intellij.kubernetes.configuration

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * @author: Hong Zhang
 */
@Service(Service.Level.APP)
@State(name = "KubernetesSettings", storages = [Storage("redhat-kubernetes.xml")])
class KubernetesSettings : PersistentStateComponent<KubernetesSettings> {
    var REDHAT_KUBERNETES_NODE_SHELL_IMAGE = "alexeiled/nsenter:2.38"
    var REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS = ""

    override fun getState(): KubernetesSettings {
        return this
    }

    override fun loadState(state: KubernetesSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun isModified(settings: KubernetesSettings): Boolean {
        return REDHAT_KUBERNETES_NODE_SHELL_IMAGE != settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE ||
                REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS != settings.REDHAT_KUBERNETES_NODE_SHELL_IMAGE_PULL_SECRETS
    }
}