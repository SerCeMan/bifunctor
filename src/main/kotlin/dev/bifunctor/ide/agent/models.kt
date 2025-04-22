package dev.bifunctor.ide.agent

import dev.bifunctor.ide.agent.LlmProvider.OPEN_AI
import dev.langchain4j.model.anthropic.AnthropicChatModelName
import dev.langchain4j.model.openai.OpenAiChatModelName
import org.jetbrains.jewel.ui.icon.IconKey

enum class XaiModelName(val id: String) {
  GROK_3_BETA("grok-3-beta"),
  GROK_3_MINI("grok-3-mini");

  override fun toString(): String = id
}

enum class MoreOpenAiChatModelNames(val id: String) {
  GPT__4_1("gpt-4.1"),
  GPT__4_1_MINI("gpt-4.1-mini"),
  GPT__4_2("gpt-4.1-nano");

  override fun toString(): String = id
}

enum class LlmProvider(val id: String) {
  OPEN_AI("OpenAI"),
  ANTHROPIC("Antrophic"),
  XAI("xAI"),
}

data class LlmModelSpec(
  // TODO: determine the Effective Context Length, not only the max tokens
  val maxTokens: Int,
)

data class LlmModel( //
  val id: String, //
  val llmProvider: LlmProvider, //
  val maxTokens: Int, //
  val icon: IconKey? = null //
)

object LlmModels {
  private val allModels: Map<String, LlmModel> = buildModelMap()

  private fun buildModelMap(): Map<String, LlmModel> {
    return mutableMapOf<String, LlmModel>() //
      .plus(
        fromProvider(
          OPEN_AI, mapOf(
            OpenAiChatModelName.GPT_4_O_MINI to LlmModelSpec(128000),
            OpenAiChatModelName.GPT_4_O to LlmModelSpec(128000),
            OpenAiChatModelName.O3_MINI to LlmModelSpec(128000),
            OpenAiChatModelName.O1 to LlmModelSpec(32000),
            // even though the official context window is 1M, the effective context window is much smaller,
            // so cap it at something reasonable like 200k tokens.
            MoreOpenAiChatModelNames.GPT__4_1 to LlmModelSpec(200000),
            MoreOpenAiChatModelNames.GPT__4_1_MINI to LlmModelSpec(200000),
            MoreOpenAiChatModelNames.GPT__4_2 to LlmModelSpec(200000),
          )
        )
      ).plus(
        fromProvider(
          LlmProvider.ANTHROPIC, mapOf(
            AnthropicChatModelName.CLAUDE_3_HAIKU_20240307 to LlmModelSpec(200000),
            AnthropicChatModelName.CLAUDE_3_5_SONNET_20241022 to LlmModelSpec(200000),
            AnthropicChatModelName.CLAUDE_3_7_SONNET_20250219 to LlmModelSpec(200000),
          )
        )
      ).plus(
        fromProvider(
          LlmProvider.XAI, mapOf(
            // even though the official context window is 1M, the effective context window could be much smaller,
            // so cap it at something reasonable like 200k tokens.
            XaiModelName.GROK_3_BETA to LlmModelSpec(200000),
            XaiModelName.GROK_3_MINI to LlmModelSpec(200000),
          )
        )
      )
  }

  private fun fromProvider(provider: LlmProvider, modelSpecs: Map<Enum<*>, LlmModelSpec>): Map<String, LlmModel> {
    return modelSpecs.map { (modelName, spec) ->
      val id = modelName.toString()
      id to LlmModel(id, provider, spec.maxTokens)
    }.toMap()
  }

  fun allModelsFrom(provider: LlmProvider): List<LlmModel> {
    return allModels.values.filter { it.llmProvider.id == provider.id }
  }

  val all: List<LlmModel> = allModels.values.toList()
}
