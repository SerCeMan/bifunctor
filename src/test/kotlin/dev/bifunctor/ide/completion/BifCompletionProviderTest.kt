package dev.bifunctor.ide.completion

import androidx.compose.runtime.mutableStateOf
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import dev.bifunctor.ide.agent.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.LinkedBlockingDeque

class BifCompletionProviderTest : BasePlatformTestCase() {
  @Suppress("NonExtendableApiUsage")
  private inner class MockEvent : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest {
      return InlineCompletionRequest(
        this,
        myFixture.file,
        myFixture.editor,
        myFixture.editor.document,
        myFixture.caretOffset,
        myFixture.caretOffset + 1
      )
    }
  }

  private class MockLlmService() : LlmService {
    override val availableModels = mutableStateOf(
      listOf(LlmModel("TestModel", LlmProvider.OPEN_AI, 4000))
    )
    override val initializedState = mutableStateOf(true)
    var nextResponses = LinkedBlockingDeque<String>()

    override fun askModel(model: LlmModel, prompt: Prompt, projectContext: QueryContext): Flow<String> = flow {
      val nextResponse = nextResponses.poll()
      if (nextResponse == null) {
        error("No response")
      }
      emit(nextResponse)
    }

    override fun startConversation(model: LlmModel) = error("not implemented")
  }

  private lateinit var provider: BifCompletionProvider
  private lateinit var mockLlmService: MockLlmService

  override fun setUp() {
    super.setUp()
    mockLlmService = MockLlmService()
    myFixture.project.registerServiceInstance(LlmService::class.java, mockLlmService)

    provider = BifCompletionProvider()
  }

  fun testOneCompletion() = runBlocking {
    val initialText = """
      public class Test {
        public static void main(String[] args) {
          System.out.<caret>
        }
      }
    """.trimIndent()
    myFixture.configureByText(JavaFileType.INSTANCE, initialText)
    val expectedCompletion = ".println(\"Hello World\")"
    mockLlmService.nextResponses.put(
      """
      ```java
      $expectedCompletion
      ```
      """.trimIndent()
    )

    val request = MockEvent().toRequest()
    val suggestion = provider.getSuggestionDebounced(request)
    assertTrue(suggestion is InlineCompletionSingleSuggestion)

    val single = suggestion as InlineCompletionSingleSuggestion
    val variant = single.getVariant()
    val elements = variant.elements.toList()
    assertEquals("Should be exactly 1 element", 1, elements.size)

    val textElement = elements.first()
    assertEquals(expectedCompletion, textElement.text)
  }

}
