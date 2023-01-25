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
package com.redhat.devtools.intellij.kubernetes.editor.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.redhat.devtools.intellij.kubernetes.editor.*
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelectedFileEditor
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import io.fabric8.kubernetes.api.model.HasMetadata

class CustomizableAction private constructor(val id: Int) : AnAction() {

    val title: Key<String> = actionTitle.invoke(id)
    val keyword: Key<String> = actionKeyword.invoke(id)
    private val callback: Key<(fe: FileEditor, re: ResourceEditor, project: Project) -> Unit> =
        actionCallback.invoke(id)
    val error: Key<String> = actionError.invoke(id)
    val message: Key<(e: Exception) -> String> = actionMessage.invoke(id)

    companion object Factory {
        private val keyIndexes = HashMap<String, Int>()

        private fun <T> get(name: String): Key<T> {
            keyIndexes.computeIfAbsent(name) { Key.create<T>(name).hashCode() }
            return Key.getKeyByIndex(keyIndexes[name]!!)!!
        }

        private val keyActions: Key<Array<Action>> =
            get("actions-com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction")
        private val keyNotification: Key<(re: ResourceEditor, project: Project, deleted: Boolean, resource: HasMetadata, modified: Boolean, clusterResource: ClusterResource?) -> Unit> =
            get("notifications-com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction")

        val id: (i: Int) -> String =
            { "com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction-${it}" }
        val actionTitle: (i: Int) -> Key<String> = { get("title-${id.invoke(it)}") }
        val actionError: (i: Int) -> Key<String> = { get("error-${id.invoke(it)}") }
        val actionMessage: (i: Int) -> Key<(e: Exception) -> String> = { get("message-${id.invoke(it)}") }
        val actionKeyword: (i: Int) -> Key<String> = { get("keyword-${id.invoke(it)}") }
        val actionCallback: (i: Int) -> Key<(fe: FileEditor, re: ResourceEditor, project: Project) -> Unit> =
            { get("callback-${id.invoke(it)}") }

        @JvmStatic
        private val actions = ArrayList<CustomizableAction>()

        @JvmStatic
        fun bindActionGroup(actions: Array<Action>, fe: FileEditor): DefaultActionGroup {
            val size = actions.size
            for (i in 0 until size) {
                if (i >= this.actions.size) {
                    this.actions.add(CustomizableAction(i))
                    ActionManager.getInstance().registerAction(id.invoke(i), this.actions[i])
                }
                fe.putUserData(actionTitle.invoke(i), actions[i].title)
                fe.putUserData(actionKeyword.invoke(i), actions[i].keyword)
                fe.putUserData(actionCallback.invoke(i), actions[i].callback)
                fe.putUserData(actionError.invoke(i), actions[i].error)
                fe.putUserData(actionMessage.invoke(i), actions[i].message)
            }
            return DefaultActionGroup(this.actions.take(actions.size))
        }

        @JvmStatic
        fun register(
            fe: FileEditor,
            notify: (re: ResourceEditor, project: Project, deleted: Boolean, resource: HasMetadata, modified: Boolean, clusterResource: ClusterResource?) -> Unit,
            actions: () -> Array<Action>
        ) {
            fe.putUserData(keyActions, actions.invoke())
            fe.putUserData(keyNotification, notify)
        }

        @JvmStatic
        fun acceptable(editor: FileEditor): Boolean {
            return null != editor.getUserData(keyActions)
        }

        @JvmStatic
        fun create(editor: FileEditor, project: Project): ResourceEditor {
            val notify = editor.getUserData(keyNotification) ?: { _, _, _, _, _, _ ->
            }
            val resourceEditor = NonClusterResourceEditor(editor, IResourceModel.getInstance(), project, notify)
            val actions = editor.getUserData(keyActions)
            if (null != actions) {
                resourceEditor.createToolbar { EditorToolbarFactory.create(actions, editor, project) }
            }
            ProjectManager.getInstance()
                .addProjectManagerListener(project, ResourceEditorFactory.onProjectClosed(resourceEditor))
            editor.putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, resourceEditor)
            editor.file?.putUserData(ResourceEditor.KEY_RESOURCE_EDITOR, resourceEditor)
            return resourceEditor
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fileEditor = getSelectedFileEditor(project) ?: return

        val title = fileEditor.getUserData(this.title) ?: return
        val keyword = fileEditor.getUserData(this.keyword) ?: return
        val callback = fileEditor.getUserData(this.callback) ?: return
        val error = fileEditor.getUserData(this.error) ?: return
        val message = fileEditor.getUserData(this.message) ?: return

        val telemetry = TelemetryService.instance.action(TelemetryService.NAME_PREFIX_EDITOR + keyword)
        com.redhat.devtools.intellij.kubernetes.actions.run(title, true) {
            try {
                val editor =
                    ResourceEditorFactory.instance.getExistingOrCreate(fileEditor, project) ?: return@run
                callback.invoke(fileEditor, editor, project)
                TelemetryService.sendTelemetry(editor.editorResource.get(), telemetry)
            } catch (e: Exception) {
                Notification().error(error, message.invoke(e))
                telemetry.error(e).send()
            }
        }
    }
}