package dev.bifunctor.ide.agent

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.langchain4j.data.message.*
import dev.langchain4j.memory.ChatMemory
import org.jetbrains.jewel.ui.icon.IconKey
import java.util.*

sealed class LlmContent

data class LlmTextContent(val text: String) : LlmContent()

sealed class LlmMessage

data class LlmUserMessage(
  val name: String?, //
  val contents: List<LlmContent> //
) : LlmMessage()

data class LlmSystemMessage(
  val text: String
) : LlmMessage()

data class LlmToolExecutionRequest( //
  val id: String, //
  val name: String,
  val arguments: String, //
  val toolIcon: IconKey? = null, //
  val state: State<ToolState>
)

data class LlmAiMessage(
  val text: String?, //
  val toolExecutionRequests: List<LlmToolExecutionRequest>?
) : LlmMessage()

data class LlmToolExecutionResultMessage(
  val id: String?, //
  val toolName: String?, //
  val text: String //
) : LlmMessage()

data class IncomingLlmMessage(val text: String = "") : LlmMessage()

data class LlmErrorMessage(val text: String) : LlmMessage()

interface LlmMessageList {
  val llmMessages: SnapshotStateList<LlmMessage>

  fun addMessageListener(listener: (LlmMessage) -> Unit): () -> Unit
}

class BifChatMemory( //
  private val toolStateService: ToolStateService,
  private val tools: ToolService,
  private val delegate: ChatMemory
) : ChatMemory, LlmMessageList {

  override val llmMessages: SnapshotStateList<LlmMessage> = mutableStateListOf()
  private val listeners = Collections.synchronizedList(mutableListOf<(LlmMessage) -> Unit>())

  override fun addMessageListener(listener: (LlmMessage) -> Unit): () -> Unit {
    listeners.add(listener)
    return {
      listeners.remove(listener)
    }
  }

  override fun id() = delegate.id()

  override fun add(message: ChatMessage) {
    val mappedMessage = mapMessage(message)
    llmMessages.add(mappedMessage)
    delegate.add(message)
    for (listener in listeners) {
      listener(mappedMessage)
    }
  }

  @Suppress("USELESS_CAST")
  private fun mapMessage(message: ChatMessage): LlmMessage {
    return when (message) {
      is UserMessage -> {
        val userContents = message.contents().map { mapContent(it) }
        LlmUserMessage(message.name(), userContents)
      }

      is SystemMessage -> {
        val systemText = (message as SystemMessage).text()
        LlmSystemMessage(systemText)
      }

      is AiMessage -> {
        val aiText = (message as AiMessage).text()
        val aiToolReqs = mapToolExecutionRequest(message)
        LlmAiMessage(aiText, aiToolReqs)
      }

      is ToolExecutionResultMessage -> {
        val toolMessage = message as ToolExecutionResultMessage
        LlmToolExecutionResultMessage(
          toolMessage.id(), toolMessage.toolName(), toolMessage.text()
        )
      }

      else -> throw UnsupportedOperationException("Unsupported type: ${message.type()}")
    }
  }

  private fun mapToolExecutionRequest(message: AiMessage): List<LlmToolExecutionRequest>? {
    return message.toolExecutionRequests()?.map { req ->
      val toolName = req.name()
      val requestedTool = tools.findToolByName(toolName)
      val toolStateService = toolStateService
      val state = toolStateService.getState(req.id())
      LlmToolExecutionRequest( //
        id = req.id(), //
        state = state, //
        name = toolName, //
        arguments = req.arguments(), //
        toolIcon = requestedTool?.iconKey
      )
    }
  }

  private fun mapContent(content: Content): LlmContent {
    return when (content) {
      is TextContent -> LlmTextContent(content.text())
      else -> throw IllegalArgumentException("Unsupported content type: ${content.type()}")
    }
  }

  override fun messages(): List<ChatMessage> {
    return delegate.messages()
  }

  override fun clear() {
    delegate.clear()
  }
}
