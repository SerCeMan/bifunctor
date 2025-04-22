package dev.bifunctor.ide.agent

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

class FakeSelectionService : SelectionService {
  override val lastSelectionCtx: MutableState<QueryContext?> = mutableStateOf(null)

  override fun sendToChat(selectionCtx: QueryContext) {
    lastSelectionCtx.value = selectionCtx
  }

  override fun clearSelectionContext() {
    lastSelectionCtx.value = null
  }
}
