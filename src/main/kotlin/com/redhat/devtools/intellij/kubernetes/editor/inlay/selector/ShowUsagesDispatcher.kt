package com.redhat.devtools.intellij.kubernetes.editor.inlay.selector

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope

object ShowUsagesDispatcher {

    @JvmStatic
    fun runWithCustomScope(project: Project, element: PsiElement) {
        val showUsagesAction: AnAction = ActionManager.getInstance().getAction(ShowUsagesAction.ID) ?: return

        // Create custom DataContext
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.PSI_ELEMENT, element)
            .add(CommonDataKeys.PSI_FILE, element.containingFile)
            .add(DataKey.create(FindUsagesOptions::class.java.name), createFindUsagesOptions(project, element))
            .build()

        // Create AnActionEvent
        val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)

        // Perform the action
        showUsagesAction.actionPerformed(actionEvent)
    }

    private fun createFindUsagesOptions(project: Project, element: PsiElement): FindUsagesOptions {
        val options = FindUsagesOptions(project)
        //val customScope: SearchScope = GlobalSearchScope.everythingScope(project)
        options.searchScope = NoExclusionsScope(project)
        return options
    }

    fun findUsageManager(project: Project, element: PsiElement, editor: Editor) {
        val fileEditor = getFileEditor(editor)
        val findUsagesManager = FindUsagesManager(project)
        val customScope: SearchScope = NoExclusionsScope(project)

        findUsagesManager.findUsages(element, null, fileEditor, false, customScope)
    }

    private fun getFileEditor(editor: Editor): FileEditor? {
        val virtualFile: VirtualFile = editor.virtualFile ?: return null
        val project = editor.project ?: return null
        val fileEditors = FileEditorManager.getInstance(project).getEditors(virtualFile)

        return fileEditors.find { fileEditor -> fileEditor is TextEditor && fileEditor.editor == editor }
    }

    class NoExclusionsScope(project: Project) : GlobalSearchScope(project) {
        override fun contains(file: VirtualFile): Boolean {
            return true; // Include EVERY file, even excluded ones
        }

        override fun isSearchInModuleContent(module: Module): Boolean {
            return true
        }

        override fun isSearchInModuleContent(module: Module, testSources: Boolean): Boolean {
            return true;
        }

        override fun isSearchInLibraries(): Boolean {
            return true;
        }
    }
}