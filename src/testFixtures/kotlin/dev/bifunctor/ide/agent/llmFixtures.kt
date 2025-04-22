package dev.bifunctor.ide.agent

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.bifunctor.ide.ui.settings.BifSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.swing.SwingUtilities

class FakeLlmService(private val models: List<LlmModel> = LlmModels.all) : LlmService {
  override val initializedState = mutableStateOf(true)
  override val availableModels = mutableStateOf(models)
  override fun startConversation(model: LlmModel): Conversation = FakeConversation()
  override fun askModel(model: LlmModel, prompt: Prompt, projectContext: QueryContext): Flow<String> {
    return flow {
      emit("Completed: ${prompt}")
    }
  }
}

class TaskAgenticLoop(val task: Future<*>) : AgenticLoop {
  override fun interrupt() {
    task.cancel(true)
  }
}

class FakeConversation : Conversation {
  override val incompleteMessage: MutableState<IncomingLlmMessage?> = mutableStateOf(null)
  override val messages = mutableStateListOf<LlmMessage>()
  override val isResponding: MutableState<Boolean> = mutableStateOf(false)
  override val id = UUID.randomUUID().toString()

  override val convCtx: MutableState<QueryContext> = mutableStateOf(QueryContext())
  private val pool = Executors.newCachedThreadPool()

  override fun runAgenticLoop(message: String, queryContext: QueryContext?): AgenticLoop {
    val fakeMessages = ChatFixtures.fakeConversation
    val task = pool.submit {
      try {
        SwingUtilities.invokeLater {
          isResponding.value = true
        }
        for (msg in fakeMessages) {
          Thread.sleep(150)
          SwingUtilities.invokeLater {
            messages.add(msg)
          }
        }
      } finally {
        SwingUtilities.invokeLater {
          isResponding.value = false
        }
      }
    }
    return TaskAgenticLoop(task)
  }

  override fun addContext(value: QueryContext?) {
  }
}

class FakeSettings : BifSettings {
  override var lastUsedModelId: String? = "gpt4o"
  override var completionEnabled: Boolean = true
}
