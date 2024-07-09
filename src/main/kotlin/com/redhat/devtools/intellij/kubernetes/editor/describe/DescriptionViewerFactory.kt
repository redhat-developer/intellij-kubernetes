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
package com.redhat.devtools.intellij.kubernetes.editor.describe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import org.jetbrains.yaml.YAMLFileType


open class DescriptionViewerFactory protected constructor() {

    companion object {
        const val PREFIX_FILE_NAME = "Description-"

        val instance = DescriptionViewerFactory()

        private val KEY_RESOURCE = Key<HasMetadata>(HasMetadata::class.java.name)

        fun isDescriptionFile(file: VirtualFile?): Boolean {
            return file?.name != null
                    && file.name.startsWith(PREFIX_FILE_NAME)
        }

        fun getResource(file: VirtualFile?): HasMetadata? {
            if (file == null) {
                return null
            }
            return file.getUserData(KEY_RESOURCE)
        }
    }


    fun openEditor(resource: HasMetadata, project: Project) {
        val description = describe(resource) ?: return
        val editor = getOpenedEditor(resource, project)
        if (editor != null) {
            putUserData(resource, editor.file)
            replaceDocument(editor, description)
        } else {
            val file = createYamlFile(description)
            putUserData(resource, file)
            openNewEditor(file, project)
        }
    }

    private fun putUserData(resource: HasMetadata, file: VirtualFile) {
        file.putUserData(KEY_RESOURCE, resource)
    }

    private fun replaceDocument(editor: FileEditor, description: String) {
        WriteAction.compute<Unit, Exception> {
            val document = FileDocumentManager.getInstance().getDocument(editor.file) ?: return@compute
            document.setReadOnly(false)
            document.setText(description)
            document.setReadOnly(true)
        }
    }

    private fun describe(resource: HasMetadata): String? {
        return when(resource) {
            is Pod -> describe(resource)
            else -> null
        }
    }

    private fun describe(pod: Pod): String {
        val description = YAMLDescription()
        PodDescriber(pod).addTo(description)
        return description.toText()
    }

    private fun createYamlFile(content: String): VirtualFile {
        val filename = "$PREFIX_FILE_NAME${System.currentTimeMillis()}.tmp"
        val file = LightVirtualFile(filename, YAMLFileType.YML, content)
        file.isWritable = false
        return file
    }

    private fun openNewEditor(file: VirtualFile, project: Project) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun getOpenedEditor(resource: HasMetadata, project: Project): FileEditor? {
        val manager = FileEditorManager.getInstance(project)
        val file = getOpenedEditorFile(manager, resource) ?: return null
        return manager.openFile(file, true).firstOrNull()
    }

    private fun getOpenedEditorFile(manager: FileEditorManager, resource: HasMetadata): VirtualFile? {
        return manager.openFiles.find { file ->
            try {
                val fileResource = getResource(file)
                if (fileResource == null) {
                    false
                } else {
                    resource.isSameResource(fileResource)
                }

            } catch (e: Exception) {
                false
            }
        }
    }

    /** for testing purposes */
    protected open fun runAsync(runnable: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable)
    }

    /** for testing purposes */
    protected open fun runInUI(runnable: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runnable.invoke()
        } else {
            ApplicationManager.getApplication().invokeLater(runnable)
        }
    }

    /* for testing purposes */
    protected open fun getTelemetryMessageBuilder(): TelemetryMessageBuilder {
      return TelemetryService.instance;
    }
}
