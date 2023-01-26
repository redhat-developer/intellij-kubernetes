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
import com.intellij.openapi.extensions.PluginId
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
import java.util.concurrent.ConcurrentHashMap

class CustomizableAction private constructor(val id: Int) : AnAction() {
    override fun displayTextInToolbar(): Boolean = true
    override fun isDefaultIcon(): Boolean = false

    companion object Factory {
        private val pluginId = PluginId.getId("com.redhat.devtools.intellij.kubernetes")
        private val keyIndexes = ConcurrentHashMap<String, Key<*>>()

        private fun <T> get(name: String): Key<T> {
            keyIndexes.computeIfAbsent(name) { Key.create<T>(name) }
            @Suppress("UNCHECKED_CAST")
            return keyIndexes[name] as Key<T>
        }

        const val ID = "com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction"
        val id: (i: Int) -> String get() = { "${ID}-${it}" }

        private val keyActions: Key<List<Action>> =
            get("actions-com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction")
        private val keyNotification: Key<(re: ResourceEditor, project: Project, deleted: Boolean, resource: HasMetadata, modified: Boolean, clusterResource: ClusterResource?) -> Unit> =
            get("notifications-com.redhat.devtools.intellij.kubernetes.editor.actions.CustomizableAction")
        private val keyAnActions = get<List<CustomizableAction>>("anactions-${ID}")
        val keyAction: (i: Int) -> Key<Action> get() = { get("action-${id(it)}") }

        @JvmStatic
        private val actions = ArrayList<CustomizableAction>()

        @JvmStatic
        fun bindActionGroup(actions: List<Action>, fe: FileEditor): DefaultActionGroup {
            val size = actions.size
            for (i in 0 until size) {
                if (i >= this.actions.size) {
                    this.actions.add(CustomizableAction(i))
                    ActionManager.getInstance().registerAction(id(i), this.actions[i], pluginId)
                }
                fe.putUserData(keyAction(i), actions[i])
            }
            val bound = this.actions.take(actions.size)
            fe.putUserData(keyAnActions, bound)
            return DefaultActionGroup(bound)
        }

        @JvmStatic
        fun register(
            fe: FileEditor,
            notify: (re: ResourceEditor, project: Project, deleted: Boolean, resource: HasMetadata, modified: Boolean, clusterResource: ClusterResource?) -> Unit,
            actions: () -> List<Action>
        ) {
            fe.putUserData(keyActions, actions.invoke())
            fe.putUserData(keyNotification, notify)
        }

        @JvmStatic
        fun acceptable(editor: FileEditor): Boolean {
            return null != editor.getUserData(keyActions)
        }

        @JvmStatic
        fun render(editor: FileEditor, resourceEditor: ResourceEditor) {
            val actions = editor.getUserData(keyActions) ?: return
            val anActions = editor.getUserData(keyAnActions) ?: return
            resourceEditor.runAsync {
                for ((i, anAction) in anActions.withIndex()) {
                    anAction.isDefaultIcon = false
                    anAction.templatePresentation.text = actions[i].title
                    anAction.templatePresentation.icon = actions[i].icon
                    anAction.templatePresentation.description = actions[i].description
                }
            }
        }

        @JvmStatic
        fun create(editor: FileEditor, project: Project): ResourceEditor {
            val notify = editor.getUserData(keyNotification) ?: { _, _, _, _, _, _ ->
            }
            val resourceEditor =
                NonClusterResourceEditor(editor, IResourceModel.getInstance(), project, notify).initAfterCreated()
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

        val action = fileEditor.getUserData(keyAction(id)) ?: return

        val telemetry = TelemetryService.instance.action(TelemetryService.NAME_PREFIX_EDITOR + action.keyword)
        com.redhat.devtools.intellij.kubernetes.actions.run(action.title, true) {
            try {
                val editor =
                    ResourceEditorFactory.instance.getExistingOrCreate(fileEditor, project) ?: return@run
                action.callback.invoke(fileEditor, editor, project)
                TelemetryService.sendTelemetry(editor.editorResource.get(), telemetry)
            } catch (e: Exception) {
                Notification().error(action.error, action.message.invoke(e))
                telemetry.error(e).send()
            }
        }
    }
}