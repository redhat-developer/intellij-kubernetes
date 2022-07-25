package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class FollowLogsActionsGroup: DefaultActionGroup("Follow Logs", true) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(FollowContainerLogActionsGroup())
    }

}