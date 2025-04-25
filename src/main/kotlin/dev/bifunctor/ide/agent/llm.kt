package dev.bifunctor.ide.agent

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.bifunctor.ide.ui.settings.BifSettingsImpl
import dev.bifunctor.ide.ui.settings.LlmKeyService
import dev.langchain4j.memory.chat.TokenWindowChatMemory
import dev.langchain4j.model.Tokenizer
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.openai.OpenAiTokenizer
import dev.langchain4j.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

interface AgenticLoop {
  fun interrupt()
}

interface Conversation {
  val convCtx: State<QueryContext>
  val incompleteMessage: State<IncomingLlmMessage?>
  val isResponding: State<Boolean>
  val messages: SnapshotStateList<LlmMessage>
  val id: String

  fun runAgenticLoop(message: String, queryContext: QueryContext?): AgenticLoop
  fun addContext(value: QueryContext?)
}

class AgenticLoopImpl : AgenticLoop {
  val isInterrupted = AtomicBoolean(false)

  override fun interrupt() {
    isInterrupted.set(true)
  }
}

class ConversationImpl(
  override val id: String,
  private val contextCollector: ContextCollector,
  private val assistant: ConversationAssistant,
  private val maxMessages: Int,
  private val messageList: LlmMessageList,
  private val maxTokens: Int,
  private val tokenizer: Tokenizer,
  private val project: Project,
) : Conversation {
  override val convCtx: MutableState<QueryContext> = mutableStateOf(QueryContext())
  override val incompleteMessage: MutableState<IncomingLlmMessage?> = mutableStateOf(null)
  override val messages: SnapshotStateList<LlmMessage> = messageList.llmMessages
  override val isResponding: MutableState<Boolean> = mutableStateOf(false)
  private var hastStarted = false

  override fun runAgenticLoop(message: String, queryContext: QueryContext?): AgenticLoopImpl {
    val projectContext = contextCollector.collectProjectContext()

    val initialContext = projectContext.merge(queryContext)
    addContext(initialContext)

    val textPrompt = when {
      hastStarted -> message
      else -> {
        val prompt = Prompts.promptConversation(message)
        prompt.renderWithRules(tokenizer, maxTokens, convCtx.value, project)
      }
    }
    val agenticLoop = AgenticLoopImpl()
    executeLoop(textPrompt, maxMessages, agenticLoop)
    return agenticLoop
  }

  override fun addContext(value: QueryContext?) {
    if (value != null) {
      convCtx.value = convCtx.value.merge(value)
    }
  }

  private fun executeLoop(message: String, maxAttempts: Int, agenticLoop: AgenticLoopImpl) {
    if (maxAttempts <= 0) {
      return
    }

    CoroutineScope(Dispatchers.Main).launch {
      isResponding.value = true
    }
    val completeLoopText = StringBuilder()
    val removeListener = messageList.addMessageListener {
      CoroutineScope(Dispatchers.Main).launch {
        // the incomplete message is cleared in the listener as the TokenStream doesn't provide
        // a way to clear the message that was accumulated in the partial response between tool calls.
        incompleteMessage.value = null
      }
    }
    try {
      assistant.chat(id, message) //
        .onPartialResponse { res ->
          CoroutineScope(Dispatchers.Main).launch {
            incompleteMessage.value = IncomingLlmMessage(
              text = (incompleteMessage.value?.text ?: "") + res
            )
            completeLoopText.append(res)
          }
        }.onCompleteResponse {
          CoroutineScope(Dispatchers.Main).launch {
            isResponding.value = false
            removeListener()
            if (!completeLoopText.contains(Prompts.COMPLETION_MARKER) && !agenticLoop.isInterrupted.get()) {
              executeLoop(
                "keep trying, and make sure to include the ${Prompts.COMPLETION_MARKER} when the response is final.",
                maxAttempts - 1,
                agenticLoop
              )
            }
          }
        }.onError { e ->
          handleError(e, removeListener)
        }.start()
    } catch (e: Throwable) {
      handleError(e, removeListener)
    }
  }

  private fun handleError(e: Throwable, removeListener: () -> Unit) {
    removeListener()
    CoroutineScope(Dispatchers.Main).launch {
      isResponding.value = false
      val errorMessage = e.message ?: "Unknown error"
      messageList.llmMessages.add(LlmErrorMessage(errorMessage))
    }
  }
}

interface ConversationAssistant {
  @SystemMessage(
    """
You are a helpful agentic AI coding assistant running inside a JetBrains IDE. 
You've got access to the tools the IDE provides.

Every user's request will have <context> attached which might be useful (or not) for you to complete the task.
Always prioritize user's instructions.
Never lie to the user or make up information, only provide information that you're confident about.

When calling tools,
* Always follow the correct tool's schema
* Before calling each tool, briefly explain what you're trying to achieve and why.

When searching for information,
* Be mindful that the codebase can be large, so specify the search scope when possible.

When making changes to the code,
* Always ensure you only make targeted edits, and preserve the rest of the code as it was before. 
* Never include unrelated changes even if they make the code better, always focus on the user's request.
"""
  )
  fun chat(@MemoryId convId: String, @UserMessage message: String): TokenStream
}

interface LlmService {
  val initializedState: State<Boolean>
  val availableModels: State<List<LlmModel>>

  fun chooseModel(): LlmModel? {
    val settings = BifSettingsImpl.instance
    val models = availableModels.value
    return models.firstOrNull { it.id == settings.lastUsedModelId } ?: models.firstOrNull()
  }

  fun startConversation(model: LlmModel): Conversation
  fun askModel(model: LlmModel, prompt: Prompt, projectContext: QueryContext): Flow<String>
}

class LlmServiceImpl(private val project: Project) : LlmService {
  private val tools = project.getService(ToolService::class.java)
  private val settings = service<BifSettingsImpl>()
  private val llmKeyService = service<LlmKeyService>()
  private val toolStateService = service<ToolStateService>()
  private val contextCollector = project.service<ContextCollector>()

  override val initializedState: MutableState<Boolean>
    get() = llmKeyService.initializedState

  override val availableModels = derivedStateOf {
    llmKeyService.allKeys //
      .filterNot { it.keyState.value.isNullOrEmpty() } //
      .flatMap { LlmModels.allModelsFrom(it.modelProvider) } //
      .toList()
  }

  override fun startConversation(model: LlmModel): ConversationImpl {
    val modelProvider = model.llmProvider
    val modelKey = llmKeyService.getKeyFor(modelProvider) //
      ?: throw RuntimeException("The model key is not set")

    val chatModel: StreamingChatLanguageModel = //
      buildChatModel(modelProvider, model, modelKey.keyValue)
    val maxMessages = 25

    val maxTokens = model.maxTokens - model.maxTokens / 7
    val tokenizer = chooseTokenizer(model)
    val convId = "conv_${UUID.randomUUID()}"
    val chatMemory = TokenWindowChatMemory.builder() //
      .maxTokens(maxTokens, tokenizer) //
      .id(convId).build()

    val memory = BifChatMemory(toolStateService, tools, chatMemory)
    val assistant: ConversationAssistant = AiServices.builder(ConversationAssistant::class.java)
      .streamingChatLanguageModel(chatModel)
      .chatMemoryProvider { memoryId ->
        if (memoryId != convId) {
          throw IllegalArgumentException("Invalid memory ID: $memoryId")
        }
        memory
      }
      .toolProvider(tools)
      .build()

    return ConversationImpl(convId, contextCollector, assistant, maxMessages, memory, maxTokens, tokenizer, project)
  }

  private fun chooseTokenizer(model: LlmModel): Tokenizer {
    // Even though, the model isn't necessarily OpenAI, we use the OpenAI tokenizer as an approximation for the total
    // number of tokens as lanchain4j doesn't seem to have other tokenizers.
    return when (model.llmProvider) {
      LlmProvider.OPEN_AI -> OpenAiTokenizer(model.id)
      LlmProvider.ANTHROPIC -> OpenAiTokenizer("gpt-4o")
      LlmProvider.XAI -> OpenAiTokenizer("gpt-4o")
    }
  }

  override fun askModel(model: LlmModel, prompt: Prompt, projectContext: QueryContext): Flow<String> = callbackFlow {
    val tokenizer = chooseTokenizer(model)
    val textPrompt = prompt.renderWithRules(tokenizer, model.maxTokens, projectContext, project)
    if (textPrompt.isBlank()) {
      // Nothing to do
      close()
      return@callbackFlow
    }
    if (!llmKeyService.initializedState.value) {
      close()
      return@callbackFlow
    }

    // (1) Pick a model & corresponding key
    val model = availableModels.value.firstOrNull()
      ?: throw RuntimeException("No LLM models are available; please configure an API key.")
    val modelProvider = model.llmProvider
    val modelKey = llmKeyService.getKeyFor(modelProvider) //
      ?: throw RuntimeException("The model key is not set")

    val chatModel: StreamingChatLanguageModel = //
      buildChatModel(modelProvider, model, modelKey.keyValue)

    // (5) Create the token stream, hooking partial results into trySend
    chatModel.chat(textPrompt, object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialText: String) {
        trySend(partialText)
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        close()
      }

      override fun onError(error: Throwable) {
        close(error)
      }
    })
    awaitClose {}
  }

  private fun buildChatModel(
    modelProvider: LlmProvider, //
    model: LlmModel, //
    apiKey: String? //
  ): StreamingChatLanguageModel {
    val chatModel: StreamingChatLanguageModel = when (modelProvider) {
      LlmProvider.OPEN_AI -> OpenAiStreamingChatModel.builder() //
        .apiKey(apiKey) //
        .modelName(model.id) //
        .build()

      LlmProvider.ANTHROPIC -> AnthropicStreamingChatModel.builder() //
        .apiKey(apiKey) //
        .modelName(model.id) //
        .build()

      LlmProvider.XAI -> OpenAiStreamingChatModel.builder() //
        .baseUrl("https://api.x.ai/v1").apiKey(apiKey) //
        .modelName(model.id) //
        .build()
    }
    return chatModel
  }
}
