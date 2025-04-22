package dev.bifunctor.ide.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import dev.bifunctor.ide.agent.LlmModel
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ModelSelector(
  models: List<LlmModel>,
  selectedModel: LlmModel?,
  onModelSelected: (LlmModel) -> Unit,
  enabled: Boolean
) {
  Box {
    Dropdown(
      enabled = enabled && models.isNotEmpty(),
      menuContent = {
        models.forEach { model ->
          selectableItem(
            selected = (model.id == selectedModel?.id),
            iconKey = model.icon,
            onClick = {
              onModelSelected(model)
            }
          ) {
            Text(model.id)
          }
        }
      }
    ) {
      Text(selectedModel?.id ?: "none selected")
    }
  }
}
