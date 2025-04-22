package dev.bifunctor.ide.ui.settings

import androidx.compose.runtime.Composable
import dev.bifunctor.ide.ui.story
import java.util.*

@Composable
fun BifSettingsPanelPreview() {
  val openAiKey = (1..5).map { UUID.randomUUID().toString() }.joinToString("-")
  BifSettingsPanel(
    isInitialized = true,
    initialOpenAiKey = openAiKey,
    initialAnthropicKey = "",
    onOpenAiKeyChange = {},
    onAnthropicKeyChange = {},
    initialXAiKey = "",
    onXAiKeyChange = {},
    initialCompletionEnabled = true,
    onCompletionEnabledChange = {},
  )
}

fun main() = story {
  BifSettingsPanelPreview()
}
