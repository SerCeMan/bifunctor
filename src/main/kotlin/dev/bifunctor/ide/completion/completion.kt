package dev.bifunctor.ide.completion

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import dev.bifunctor.ide.agent.ContextCollector
import dev.bifunctor.ide.agent.LlmService
import dev.bifunctor.ide.agent.prompts.Prompts
import dev.bifunctor.ide.ui.settings.BifSettingsImpl
import dev.bifunctor.ide.ui.settings.LlmKeyService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class BifCompletionProvider : DebouncedInlineCompletionProvider() {
  private val llmKeyService = service<LlmKeyService>()
  private val settings = BifSettingsImpl.instance

  override val id: InlineCompletionProviderID =
    InlineCompletionProviderID("dev.bifunctor.ide.completion.BifunctorCompletionProvider")

  override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration = 250.milliseconds

  override fun isEnabled(event: InlineCompletionEvent): Boolean {
    if (!settings.completionEnabled) {
      return false
    }
    val req = event.toRequest()
    if (req == null) {
      return false
    }
    val editor = req.editor
    val project = editor.project
    if (project == null || project.isDisposed) {
      return false
    }
    return llmKeyService.initialized.get()
  }

  override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
    val editor = request.editor
    val project = editor.project
    if (project == null) {
      return InlineCompletionSuggestion.Empty
    }
    val document = request.file.fileDocument
    val (lineTextUpToCaret, contextAroundCaret) = ReadAction.compute<Pair<String, String>, Throwable> {
      val caretOffset = editor.caretModel.offset
      val lineContext = extractLineUpToCaret(document, caretOffset)
      val contextAroundCaret = snippetAroundCaretWithMarker(document, caretOffset)
      lineContext to contextAroundCaret
    }
    val llmService = project.getService(LlmService::class.java)
    val contextCollector = project.service<ContextCollector>()
    val projectContext = contextCollector.collectProjectContext()
    val userPrompt = Prompts.promptCompletion(contextAroundCaret)

    val model = llmService.chooseModel()
    if (model == null) {
      return InlineCompletionSuggestion.Empty
    }

    val completionText = try {
      val completionFlow = llmService.askModel(model, userPrompt, projectContext)
      buildString {
        completionFlow.collect(::append)
      }
    } catch (e: Throwable) {
      return InlineCompletionSuggestion.Empty
    }

    val finalSnippet = prepareCompletion(completionText, lineTextUpToCaret)
    if (finalSnippet.isBlank()) {
      return InlineCompletionSuggestion.Empty
    }

    val element: InlineCompletionElement = InlineCompletionGrayTextElement(finalSnippet)
    return InlineCompletionSingleSuggestion.build {
      emit(element)
    }
  }

  private fun extractLineUpToCaret(document: Document, caretOffset: Int): String {
    if (caretOffset < 0 || caretOffset > document.textLength) {
      return ""
    }
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    return document.getText(TextRange(lineStart, caretOffset))
  }

  private fun prepareCompletion(completion: String, lineTextUpToCaret: String): String {
    var codeBlock = stripMarkdownTags(completion).trim().take(300)
    val possiblePrefixes = listOf(lineTextUpToCaret, lineTextUpToCaret.trim())
    for (possiblePrefix in possiblePrefixes) {
      if (codeBlock.startsWith(possiblePrefix)) {
        codeBlock = codeBlock.removePrefix(possiblePrefix)
        break
      }
    }
    return codeBlock
  }

  private fun snippetAroundCaretWithMarker(
    document: Document,
    caretOffset: Int,
    linesBefore: Int = 10,
    linesAfter: Int = 10
  ): String {
    val caretLine = document.getLineNumber(caretOffset)

    val startLine = maxOf(0, caretLine - linesBefore)
    val endLine = minOf(document.lineCount - 1, caretLine + linesAfter)

    val lines = document.text.lines()
    val snippetLines = mutableListOf<String>()
    for (ln in startLine..endLine) {
      if (ln == caretLine) {
        val offsetInLine = caretOffset - document.getLineStartOffset(caretLine)
        val originalLine = lines[ln]
        if (offsetInLine in 0..originalLine.length) {
          val lineWithCaret = buildString {
            append(originalLine.take(offsetInLine))
            append(Prompts.CARET_RESPONSE)
            append(originalLine.drop(offsetInLine))
          }
          snippetLines.add(lineWithCaret)
        } else {
          snippetLines.add(originalLine)
        }
      } else {
        snippetLines.add(lines[ln])
      }
    }
    return snippetLines.joinToString("\n")
  }
}

