package dev.bifunctor.ide.agent

import com.intellij.mock.MockVirtualFile

object QueryContexts {
  val mockQueryCtx: QueryContext
    get() = QueryContext(
      recentFiles = listOf(MockVirtualFile.file("test.java"))
    )
}
