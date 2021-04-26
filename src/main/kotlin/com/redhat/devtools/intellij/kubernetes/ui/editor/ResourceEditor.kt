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
package com.redhat.devtools.intellij.kubernetes.ui.editor

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.context.ActiveContext
import com.redhat.devtools.intellij.kubernetes.model.util.toResource
import io.fabric8.kubernetes.api.model.HasMetadata
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.swing.JComponent

object ResourceEditor {

    private val KEY_EDITOR_MODEL = Key<ResourceEditorModel>(ResourceEditorModel::class.java.name)

    private val LOGGER = LoggerFactory.getLogger(ResourceEditor::class.java)
    private val resourceModel = ServiceManager.getService(IResourceModel::class.java)

    @Throws(IOException::class)
    fun open(project: Project, resource: HasMetadata) {
        val file = ResourceEditorFile.get(resource)
        openEditor(file, project)
    }

    private fun openEditor(virtualFile: VirtualFile?, project: Project): FileEditor? {
        if (virtualFile == null) {
            return null
        }
        val editors = FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
        return editors.getOrNull(0)
    }

    fun isResourceEditor(editor: FileEditor): Boolean {
        return isFile(editor.file)
    }

    fun isFile(file: VirtualFile?): Boolean {
        return ResourceEditorFile.matches(file)
    }

    fun delete(manager: FileEditorManager, file: VirtualFile, project: Project) {
        val editor = manager.getEditors(file).firstOrNull()
        val model = getEditorModel(editor, project) ?: return
        model.stopWatch()
        ResourceEditorFile.delete(file)
    }

    fun showNotifications(editor: FileEditor, project: Project) {
        val editorModel = getEditorModel(editor, project) ?: return
        if (editorModel.isDeletedOnCluster()) {
            DeletedNotification.show(editor, editorModel.resource, project)
            ReloadNotification.hide(editor, project)
        } else if (editorModel.isUpdatedOnCluster()) {
            ReloadNotification.show(editor, editorModel.resource, project)
            DeletedNotification.hide(editor, project)
        }
    }

    fun getEditorModel(editor: FileEditor?, project: Project): ResourceEditorModel? {
        if (editor == null) {
            return null
        }
        var editorModel: ResourceEditorModel? = getUserData(KEY_EDITOR_MODEL, editor)
        if (editorModel == null) {
            editorModel = createEditorModel(editor) ?: return null
            putUserData(KEY_EDITOR_MODEL, editorModel, editor)
            editorModel.addListener(onResourceChanged(editor, project))
            editorModel.watch()
        }
        return editorModel
    }

    private fun createEditorModel(editor: FileEditor): ResourceEditorModel? {
        val file = editor.file ?: return null
        var editorModel: ResourceEditorModel? = null
        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document?.text != null) {
            val resource = toResource<HasMetadata>(document.text)
            val context = resourceModel.getCurrentContext() as? ActiveContext
            if (context != null
                && resource != null) {
                editorModel = ResourceEditorModel(resource, context)
            }
        }
        return editorModel
    }

    private fun onResourceChanged(editor: FileEditor, project: Project): ModelChangeObservable.IResourceChangeListener {
        return object : ModelChangeObservable.IResourceChangeListener {
            override fun added(added: Any) {
            }

            override fun removed(removed: Any) {
                val resource = removed as? HasMetadata ?: return
                DeletedNotification.show(editor, resource, project)
            }

            override fun modified(modified: Any) {
                val resource = modified as? HasMetadata ?: return
                ReloadNotification.show(editor, resource, project)
            }
        }
    }

    private fun <T> getUserData(key: Key<T>, editor: FileEditor?): T? {
        return editor?.getUserData(key)
    }

    private fun <T> putUserData(key: Key<T>, value: T?, editor: FileEditor?) {
        editor?.putUserData(key, value)
    }
}

fun FileEditor.showNotification(key: Key<JComponent>, panelFactory: () -> EditorNotificationPanel, project: Project) {
    if (this.getUserData(key) != null) {
        return
    }
    val panel = panelFactory.invoke()
    this.putUserData(key, panel)
    FileEditorManager.getInstance(project).addTopComponent(this, panel)
}

fun FileEditor.hideNotification(key: Key<JComponent>, project: Project) {
    val panel = this.getUserData(key)
    if (panel != null) {
        FileEditorManager.getInstance(project).removeTopComponent(this, panel)
    }
}


