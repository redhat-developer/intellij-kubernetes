package com.redhat.devtools.intellij.kubernetes.ui

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.intellij.ui.EditorNotificationPanel

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.redhat.devtools.intellij.kubernetes.model.util.sameRevision
import com.redhat.devtools.intellij.kubernetes.model.util.writeFile
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.utils.Serialization
import java.io.File


class ResourceEditorSelectionListener : StartupActivity {

    override fun runActivity(project: Project) {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val editor = event.newEditor ?: return
                    val resource = getResource(editor) ?: return
                    val model = ServiceManager.getService(IResourceModel::class.java)
                    val latestRevision = model.resource(resource) ?: return
                    if (!resource.sameRevision(latestRevision)) {
                        FileEditorManager.getInstance(project).addTopComponent(editor, createPanel(editor, latestRevision, project))
                    }
                }
            })
    }

    private fun getResource(editor: FileEditor): HasMetadata? {
        var resource = editor.getUserData(ResourceEditor.USER_DATA_RESOURCE)
        if (resource == null
            && editor.file != null) {
            val document = FileDocumentManager.getInstance().getDocument(editor.file!!)
            if (document?.text != null) {
                resource = Serialization.unmarshal(document.text, HasMetadata::class.java)
                editor.putUserData(ResourceEditor.USER_DATA_RESOURCE, resource)
            }
        }
        return resource
    }

    private fun createPanel(editor: FileEditor, resource: HasMetadata, project: Project): EditorNotificationPanel {
        val panel = EditorNotificationPanel(EditorColors.NOTIFICATION_BACKGROUND)
        panel.setText("${resource.metadata.name} changed on server. Reload content?")
        panel.createActionLabel("Reload now") {
            val file = editor.file
            if (file != null
                && !project.isDisposed) {
                writeFile(resource, File(file.canonicalPath))
                file.refresh(false, true)
                editor.putUserData(ResourceEditor.USER_DATA_RESOURCE, null)
                FileDocumentManager.getInstance().reloadFiles(editor.file!!)
                FileEditorManager.getInstance(project).removeTopComponent(editor, panel)
            }
        }
        panel.createActionLabel("Keep current") {

        }
        return panel
    }

}