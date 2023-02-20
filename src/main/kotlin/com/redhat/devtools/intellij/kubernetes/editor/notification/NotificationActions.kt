package com.redhat.devtools.intellij.kubernetes.editor.notification

import com.intellij.ui.EditorNotificationPanel
import com.redhat.devtools.intellij.kubernetes.editor.actions.DiffAction
import com.redhat.devtools.intellij.kubernetes.editor.actions.PullAction
import com.redhat.devtools.intellij.kubernetes.editor.actions.PushAllAction
import com.redhat.devtools.intellij.kubernetes.editor.actions.PushModifiedAction

fun addPush(all: Boolean, panel: EditorNotificationPanel) {
    if (all) {
        panel.createActionLabel("Push", PushAllAction.ID)
    } else {
        panel.createActionLabel("Push", PushModifiedAction.ID)
    }
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