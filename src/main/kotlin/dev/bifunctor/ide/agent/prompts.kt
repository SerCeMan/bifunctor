package dev.bifunctor.ide.agent

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import dev.langchain4j.model.Tokenizer

private val CONTEXT_PLACEHOLDER = "<@context@>"

class Prompt(
  private val template: String,
) {
  fun render(tokenizer: Tokenizer, maxTokens: Int, ctx: QueryContext, ruleService: AiRuleService): String {
    // never take more than 1/8 of the max tokens for the context
    val maxTokenLen = maxTokens / 8
    val contextPrompt = asPromptPart(ctx, maxTokenLen, ruleService, tokenizer)
    return template.replace(CONTEXT_PLACEHOLDER, contextPrompt)
  }

  private fun asPromptPart(
    ctx: QueryContext, maxTokenLen: Int, ruleService: AiRuleService, tokenizer: Tokenizer
  ): String {
    val recentFiles = ctx.recentFiles
    val selection = ctx.selection
    val resolvedSelection = ctx.resolvedSelection
    val relevantFiles = (recentFiles + resolvedSelection.map { it.containingFile.virtualFile }).distinct()

    val promptBuilder = StringBuilder()

    if (selection.isNullOrEmpty()) {
      promptBuilder.append("<selection>\n")
      promptBuilder.append(selection)
      promptBuilder.append("\n</selection>")
    }
    val includedFiles = mutableListOf<VirtualFile>()
    if (relevantFiles.isNotEmpty()) {
      promptBuilder.append("<relevant_files>\n")
      relevantFiles //
        .takeWhile {
          // TODO: can a tokenizer work on string builders rather than strings?
          tokenizer.estimateTokenCountInText(promptBuilder.toString()) < maxTokenLen
        } //
        .forEach { file ->
          promptBuilder.appendLine(file.path)
          promptBuilder.appendLine(file.readText())
          includedFiles.add(file)
        }
      promptBuilder.append("</relevant_files>\n")
    }
    val aiRules = ruleService.getRulesForFiles(includedFiles)
    if (includedFiles.isNotEmpty()) {
      promptBuilder.append("<ai_rules>\n")
      // always include all relevant rules
      for (rule in aiRules) {
        promptBuilder.appendLine(rule.content)
      }
      promptBuilder.append("</ai_rules>\n")
    }
    return promptBuilder.toString()
  }
}


object Prompts {
  const val COMPLETION_MARKER = """@task-completed"""
  const val CARET_RESPONSE = "<caret>{response}</caret>"
  const val CARET = """<caret/>"""

  private fun contextPlaceholder(): String = """
      The IDE has provided the following context that might be helpful to you. The might include: 
      * selection – the text that is currently selected in the editor
      * recent files – the files that the user has recently opened
      * ai_rules – the project rules you must follow precisely.
      <context>
      $CONTEXT_PLACEHOLDER
      </context>
    """.trimIndent()

  fun promptConversation(task: String): Prompt {
    return Prompt(
      """
        You are given the following task to complete.
        If the task is a question, answer it directly. If the task is a direction, 
        then execute it using the tools at your disposal.
        <task>
        $task
        </task>
        
        ${contextPlaceholder()}
        Once you have completed the task, respond with "$COMPLETION_MARKER".
    """.trimIndent()
    )
  }

  fun promptCompletion(caretContext: String): Prompt {
    return Prompt(
      """
        Provide the best possible block of code that should be inserted at the $CARET position.
        <caretContext>
        $caretContext
        </caretContext>
        
        ${contextPlaceholder()}
        Reply with the response that will be inserted in between the caret tags.
    """.trimIndent()
    )
  }

  fun unpromptUserMessage(prompt: String): String {
    val regex = Regex("<task>(.*?)</task>", RegexOption.DOT_MATCHES_ALL)
    return regex.find(prompt)?.groups?.get(1)?.value?.trim() ?: prompt
  }

  fun unpromptAiMessage(text: String): String {
    // remove all completion markers
    return text.replace(COMPLETION_MARKER, "")
  }
}
