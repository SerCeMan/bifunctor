package dev.bifunctor.ide.agent

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.intellij.util.ui.EDT

interface SelectionService {
  val lastSelectionCtx: State<QueryContext?>
  fun sendToChat(selectionCtx: QueryContext)
  fun clearSelectionContext()
}

class SelectionServiceImpl : SelectionService {
  override val lastSelectionCtx: MutableState<QueryContext?> = mutableStateOf(null)

  override fun sendToChat(selectionCtx: QueryContext) {
    ensureEdt()
    this.lastSelectionCtx.value = selectionCtx
  }

  override fun clearSelectionContext() {
    ensureEdt()
    this.lastSelectionCtx.value = null
  }

  private fun ensureEdt() {
    if (!EDT.isCurrentThreadEdt()) {
      throw IllegalStateException("Cannot work with selection on a non-EDT thread: ${Thread.currentThread().name}")
    }
  }
}
