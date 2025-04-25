package dev.bifunctor.ide.files

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private const val MAX_SIZE = 7

@Service(Service.Level.PROJECT)
class RecentFilesService(project: Project) {
  private val editorHistoryManager = EditorHistoryManager.getInstance(project)

  fun getRecentFiles(): List<VirtualFile> {
    return editorHistoryManager.files.takeLast(MAX_SIZE).reversed()
  }
}
