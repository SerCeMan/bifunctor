package dev.bifunctor.ide.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bifunctor.ide.agent.*
import dev.bifunctor.ide.ui.components.chat.ChatContentContainer
import dev.bifunctor.ide.ui.story
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ChatContentContainerPreview() {
  val selectionService = FakeSelectionService()
  LaunchedEffect(selectionService) {
    val queryCtx = QueryContexts.mockQueryCtx
    selectionService.sendToChat(queryCtx)
  }

  var selectedMode by remember { mutableStateOf("With Models") }
  val modes = listOf("With Models", "Without Models")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.Top
  ) {
    Dropdown(
      menuContent = {
        modes.forEach { mode ->
          selectableItem(
            selected = (selectedMode == mode),
            onClick = { selectedMode = mode }
          ) {
            Text(mode)
          }
        }
      }
    ) {
      Text("Mode: $selectedMode")
    }

    Spacer(Modifier.height(16.dp))

    val models = if (selectedMode == "With Models") {
      LlmModels.all
    } else {
      emptyList()
    }

    val fakeLlmService = FakeLlmService(models)

    ChatContentContainer(
      llm = fakeLlmService,
      settings = FakeSettings(),
      selectionService = selectionService,
      openSettings = {}
    )
  }
}

fun main() = story {
  ChatContentContainerPreview()
}
