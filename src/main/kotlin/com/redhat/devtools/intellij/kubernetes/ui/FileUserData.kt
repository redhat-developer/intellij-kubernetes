package com.redhat.devtools.intellij.kubernetes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import io.fabric8.kubernetes.api.model.HasMetadata

class FileUserData(private val file: VirtualFile?) {

    companion object {
        val RESOURCE = Key<HasMetadata>("RESOURCE")
        val PROJECT = Key<Project>("PROJECT")
    }

    fun <T> get(key: Key<T>): T? {
        return file?.getUserData(key)
    }

    fun <T> put(name: String, value: T): FileUserData {
        return put(Key(name), value)
    }

    fun <T> put(key: Key<T>, value: T): FileUserData {
        file?.putUserData(key, value)
        return this
    }

    fun isForFile(file: VirtualFile?): Boolean {
        return this.file == file
    }
}