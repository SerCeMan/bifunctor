package dev.bifunctor.ide.files

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.LinkedBlockingDeque

@Service(Service.Level.PROJECT)
class RecentFilesService(val project: Project) {
  companion object {
    private const val MAX_SIZE = 7

    fun getInstance(project: Project): RecentFilesService {
      return project.getService(RecentFilesService::class.java)
    }
  }

  private val recentFiles = LinkedBlockingDeque<VirtualFile>(MAX_SIZE)

  init {
    project.messageBus.connect().subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          synchronized(recentFiles) {
            recentFiles.remove(file)
            recentFiles.offerFirst(file)
            if (recentFiles.size > MAX_SIZE) {
              recentFiles.pollLast()
            }
          }
        }
      }
    )
  }

  fun getRecentFiles(): List<VirtualFile> = synchronized(recentFiles) {
    recentFiles.toList()
  }
}
