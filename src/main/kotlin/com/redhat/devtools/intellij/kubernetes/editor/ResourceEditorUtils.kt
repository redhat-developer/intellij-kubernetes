package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile

/**
 * Returns a [ResourceEditor] for the given [FileEditor] if it exists. Returns `null` otherwise.
 * The editor exists if it is contained in the user data for the given editor or its file.
 *
 * @param editor for which an existing [ResourceEditor] is returned.
 * @return [ResourceEditor] that exists.
 *
 * @see [FileEditor.getUserData]
 * @see [VirtualFile.getUserData]
 */
fun getExisting(editor: FileEditor?): ResourceEditor? {
    if (editor == null) {
        return null
    }
    return editor.getUserData(ResourceEditor.KEY_RESOURCE_EDITOR)
        ?: getExisting(editor.file)
}

/**
 * Returns a [ResourceEditor] for the given [VirtualFile] if it exists. Returns `null` otherwise.
 * The editor exists if it is contained in the user data for the given file.
 *
 * @param file for which an existing [VirtualFile] is returned.
 * @return [ResourceEditor] that exists.
 *
 * @see [VirtualFile.getUserData]
 */
fun getExisting(file: VirtualFile?): ResourceEditor? {
    return file?.getUserData(ResourceEditor.KEY_RESOURCE_EDITOR)
}
