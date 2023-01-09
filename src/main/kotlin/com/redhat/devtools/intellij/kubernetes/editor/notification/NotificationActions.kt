package com.redhat.devtools.intellij.kubernetes.editor.notification

import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.kubernetes.editor.actions.DiffAction
import com.redhat.devtools.intellij.kubernetes.editor.actions.PullAction
import com.redhat.devtools.intellij.kubernetes.editor.actions.PushAction

fun addPush(panel: EditorNotificationPanel) {
    panel.createActionLabel("Push", PushAction.ID)
}

fun addPull(panel: EditorNotificationPanel) {
    panel.createActionLabel("Pull", PullAction.ID)
}

fun addDismiss(panel: EditorNotificationPanel, consumer: () -> Unit) {
    panel.createActionLabel("Dismiss", consumer)
}

fun addDiff(panel: EditorNotificationPanel) {
    panel.createActionLabel("Diff", DiffAction.ID)
}