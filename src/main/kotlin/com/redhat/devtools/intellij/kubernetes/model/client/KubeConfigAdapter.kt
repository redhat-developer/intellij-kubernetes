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

import com.intellij.openapi.util.io.FileUtil
import io.fabric8.kubernetes.api.model.Config
import io.fabric8.kubernetes.api.model.Context
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import java.io.File

/**
 * A class that allows to access the kube config file that's by default at ~/.kube/config
 * (but may be configured to be at a different location). This class respects this by relying on the
 * {@link io.fabric8.kubernetes.client.Config} for the location instead of using a hard coded path.
 */
open class KubeConfigAdapter(
    private val file: File,
    private var _config: Config? = null,
    private val loadKubeConfig: (file: File) -> Config = { KubeConfigUtils.parseConfig(file) },
    private val saveKubeConfig: (config: Config, file: File) -> Unit = { config, file -> KubeConfigUtils.persistKubeConfigIntoFile(config, file) }
) {

    private val config: Config?
        /**
         * Returns the config that was given to this class at construction time or
         * parses the file that was given to this instance when it was constructed
         */
        get() {
            return if (_config != null) {
                _config
            } else {
                _config = load()
                _config
            }
        }

    private var modified: Boolean = false

    /* for testing purposes */
    protected open fun load(): Config? {
        if (!exists()) {
            return null
        }
        return loadKubeConfig.invoke(file)
    }

    /* for testing purposes */
    protected open fun exists(): Boolean {
        return file.exists()
    }

    fun save() {
        val config = this.config ?: return
        saveKubeConfig.invoke(config, file)
    }

    fun setCurrentContext(newCurrentContext: String?): Boolean {
        val oldCurrentContext = config?.currentContext ?: return false
        return if (newCurrentContext != oldCurrentContext) {
            _config?.currentContext = newCurrentContext
            this.modified = _config != null
            return modified
        } else {
            false
        }
    }

    /**
     * Sets the namespace in the given source [Context] to the given target [Context].
     * Does nothing if the target config has no current context
     * or if the source config has no current context
     * or if setting it would not change it.
     *
     * @param source Context whose namespace should be copied
     * @param target Context whose namespace should be overriden
     * @return
     */
    fun setCurrentNamespace(context: String?, namespace: String?): Boolean {
        if (context == null
            || namespace == null) {
            return false
        }
        val target = getContext(context) ?: return false
        return if (namespace != target.namespace) {
            target.namespace = namespace
            true
        } else {
            false
        }
    }

    private fun getContext(name: String?): Context? {
        if (name == null) {
            return null
        }
        val config = this.config ?: return null
        return config.contexts
            .filter { namedContext ->
                name == namedContext.name
            }.map { namedContext ->
                namedContext.context
            }.firstOrNull()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KubeConfigAdapter) return false

        return FileUtil.filesEqual(file, other.file)
    }

    override fun hashCode(): Int {
        return FileUtil.fileHashCode(file)
    }
}