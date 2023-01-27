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
package com.redhat.devtools.intellij.kubernetes.extension

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.LazyExtensionInstance
import io.fabric8.kubernetes.api.model.HasMetadata

abstract class CustomizableEditorProvider(val provider: Class<*>) :
    LazyExtensionInstance<CustomizableEditorProvider>() {
    abstract val keying: (editor: FileEditor?, project: Project) -> String
    abstract val acceptable: (editor: FileEditor?, project: Project) -> Boolean
    abstract val serializer: (res: HasMetadata) -> String
    abstract val customizer: (editor: FileEditor, project: Project) -> FileEditor

    companion object {
        private val providers = HashMap<String, CustomizableEditorProvider?>()
        private val registry = HashMap<Class<*>, CustomizableEditorProvider>()

        init {
            register(HelmEditorProvider::class.java)
        }

        @JvmStatic
        protected fun register(provider: Class<*>) {
            registry[provider] = provider.getConstructor().newInstance() as CustomizableEditorProvider
        }

        @JvmStatic
        fun find(editor: FileEditor, project: Project): CustomizableEditorProvider? {
            val provider = registry.values.firstOrNull { it.acceptable.invoke(editor, project) }
            val key = provider?.keying?.invoke(editor, project)
            if (null != key)
                providers[key] = provider
            return providers[key]
        }
    }
}