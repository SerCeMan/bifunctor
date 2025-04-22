package dev.bifunctor.ide.ui.selection

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.project.Project
import com.intellij.util.text.CharArrayUtil
import dev.bifunctor.ide.agent.ContextCollector
import dev.bifunctor.ide.agent.SelectionService

class SendSelectionToChat : EditorAction(Handler()) {
  private class Handler : EditorWriteActionHandler.ForEachCaret() {
    override fun executeWriteAction(editor: Editor, caret: Caret, dataContext: DataContext) {
      val project = CommonDataKeys.PROJECT.getData(dataContext)
      if (project == null) {
        return
      }
      if (isEnabled(editor, caret, dataContext)) {
        sendSelection(editor, project)
      }
    }
  }

  override fun update(editor: Editor, presentation: Presentation, dataContext: DataContext) {
    presentation.isEnabled = originalIsEnabled(editor, true)
  }

  override fun updateForKeyboardAccess(editor: Editor, presentation: Presentation, dataContext: DataContext) {
    presentation.isEnabled = isEnabled(editor, dataContext)
  }

  protected open fun isEnabled(editor: Editor, dataContext: DataContext?): Boolean {
    return originalIsEnabled(editor, true)
  }

  companion object {
    protected fun originalIsEnabled(editor: Editor, wantSelection: Boolean): Boolean {
      return (!wantSelection || hasSuitableSelection(editor)) && !editor.isOneLineMode && !editor.isViewer
    }

    private fun hasSuitableSelection(editor: Editor): Boolean {
      if (!editor.selectionModel.hasSelection()) {
        return false
      }
      val document = editor.document
      val selectionStart = editor.selectionModel.selectionStart
      val selectionEnd = editor.selectionModel.selectionEnd
      return !CharArrayUtil.containsOnlyWhiteSpaces(document.charsSequence.subSequence(selectionStart, selectionEnd))
    }

    private fun sendSelection(editor: Editor, project: Project) {
      val document = editor.document
      val contextCollection = project.getService(ContextCollector::class.java)
      val selectionCtx = contextCollection.collectSelectionContext(editor, document)

      val selectionService = project.getService(SelectionService::class.java)
      selectionService.sendToChat(selectionCtx)
    }
  }
}
