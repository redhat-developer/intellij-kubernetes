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
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Persistent value that stores to and loads from [FileAttribute] for a given [FileEditor].
 */
class PersistentEditorValue(
    private val editor: FileEditor,
    // for mocking purposes
    private val persistence: FileAttribute = fileAttribute
) {

    private companion object {
        private val fileAttribute =
            FileAttribute("com.redhat.devtools.intellij.kubernetes.editor.PersistentEditorValue")
    }

    private var initialized = false
    private var value: String? = null
    private val mutex = ReentrantReadWriteLock()

    fun set(value: String?) {
        mutex.write {
            this.initialized = true
            this.value = value
        }
    }

    fun get(): String? {
        mutex.read {
            if (!initialized) {
                this.value = get(editor.file)
                initialized = true
            }
            return value
        }
    }

    private fun get(file: VirtualFile?): String? {
        if (file == null) {
            return null
        }
        val bytes = persistence.readAttributeBytes(file)
        if (bytes == null
            || bytes.isEmpty()) {
            return null
        }
        return String(bytes)
    }

    fun save() {
        val file = editor.file ?: return
        val bytes = get()?.toByteArray() ?: ByteArray(0)
        persistence.writeAttributeBytes(file, bytes)
    }
}
