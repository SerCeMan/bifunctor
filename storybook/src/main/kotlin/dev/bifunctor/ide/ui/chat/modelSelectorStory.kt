package dev.bifunctor.ide.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bifunctor.ide.agent.LlmModel
import dev.bifunctor.ide.agent.LlmModels
import dev.bifunctor.ide.ui.components.chat.ModelSelector
import dev.bifunctor.ide.ui.story
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ModelSelectorPreview() {
  val previewModels = LlmModels.all
  var selectedModel1: LlmModel? by remember { mutableStateOf(previewModels.first()) }
  var selectedModel2: LlmModel? by remember { mutableStateOf(null) }

  Column {
    Text("1. Model selector $previewModels")
    ModelSelector(
      models = previewModels,
      selectedModel = selectedModel1,
      onModelSelected = { newModel ->
        selectedModel1 = newModel
      },
      enabled = true
    )
    Spacer(Modifier.height(16.dp))
    Text("Currently selected: $selectedModel1")
    Spacer(Modifier.height(16.dp))

    Text("2. Model selector with nothing selected")
    ModelSelector(
      models = previewModels,
      selectedModel = selectedModel2,
      onModelSelected = { newModel ->
        selectedModel2 = newModel
      },
      enabled = true
    )
    Text("Currently selected: $selectedModel2")
    Spacer(Modifier.height(16.dp))

    Text("3. Model selector with none available")
    ModelSelector(
      models = emptyList(),
      selectedModel = null,
      onModelSelected = {},
      enabled = true,
    )
  }
}

fun main() = story {
  ModelSelectorPreview()
}
