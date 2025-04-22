package dev.bifunctor.ide.agent

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.bifunctor.ide.files.RecentFilesService


data class QueryContext(
  val recentFiles: List<VirtualFile> = emptyList(),
  val selection: String? = null,
  val resolvedSelection: List<PsiElement> = emptyList(),
) {
  fun merge(another: QueryContext?): QueryContext {
    if (another == null) {
      return this
    }
    return QueryContext(
      recentFiles = (recentFiles + another.recentFiles).distinct(),
      selection = selection ?: another.selection,
      resolvedSelection = (resolvedSelection + another.resolvedSelection).distinct()
    )
  }
}

interface ContextCollector {
  fun collectProjectContext(): QueryContext
  fun collectSelectionContext(editor: Editor, document: Document): QueryContext
}

class ContextCollectorImpl(private val project: Project) : ContextCollector {
  private val recentFilesService = RecentFilesService.getInstance(project)

  override fun collectProjectContext(): QueryContext {
    val recentFiles = recentFilesService.getRecentFiles()
    return QueryContext(recentFiles)
  }

  override fun collectSelectionContext(editor: Editor, document: Document): QueryContext {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile == null) {
      return QueryContext(emptyList())
    }
    val selectionModel = editor.selectionModel

    if (!selectionModel.hasSelection()) {
      return QueryContext()
    }

    val selectionStart = selectionModel.selectionStart
    val selectionEnd = selectionModel.selectionEnd

    val elementsInRange = findElementsInRange(psiFile, selectionStart, selectionEnd)
    val resolvedElements = resolveReferences(elementsInRange)

    return QueryContext(
      selection = editor.selectionModel.selectedText,
      resolvedSelection = resolvedElements.toList()
    )
  }

  private fun resolveReferences(elements: Collection<PsiElement>): Collection<PsiElement> {
    val resolvedElements = mutableSetOf<PsiElement>()

    elements.forEach { element ->
      element.parent.references.forEach { reference ->
        reference.resolve()?.let { resolved ->
          resolvedElements.add(resolved)
        }
      }
    }
    return resolvedElements
  }

  private fun findElementsInRange(file: PsiFile, selectionStart: Int, selectionEnd: Int): List<PsiElement> {
    val startElement = file.findElementAt(selectionStart) ?: return emptyList()
    val endElement = file.findElementAt(selectionEnd - 1) ?: return emptyList()

    val result = mutableListOf<PsiElement>()
    var e: PsiElement? = startElement
    while (e !== endElement) {
      if (e == null) {
        break
      }
      result.add(e)
      e = e.nextSibling
    }
    result.add(endElement)
    return result
  }
}

