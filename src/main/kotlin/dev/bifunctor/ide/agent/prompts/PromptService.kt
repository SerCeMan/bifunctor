package dev.bifunctor.ide.agent.prompts

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import dev.bifunctor.ide.agent.AiRuleService
import dev.bifunctor.ide.agent.QueryContext
import dev.langchain4j.model.Tokenizer

interface PromptService {
  fun renderPrompt(prompt: Prompt, tokenizer: Tokenizer, maxTokens: Int, ctx: QueryContext): String
}

class PromptServiceImpl(project: Project) : PromptService {
  private val ruleService: AiRuleService = project.service<AiRuleService>()

  override fun renderPrompt(prompt: Prompt, tokenizer: Tokenizer, maxTokens: Int, ctx: QueryContext): String {
    // never take more than 1/8 of the max tokens for the context
    val maxTokenLen = maxTokens / 8
    val contextPrompt = asPromptPart(ctx, maxTokenLen, tokenizer)
    return prompt.render(contextPrompt)
  }

  private fun asPromptPart(
    ctx: QueryContext, maxTokenLen: Int, tokenizer: Tokenizer
  ): String {
    val recentFiles = ctx.recentFiles
    val selection = ctx.selection
    val resolvedSelection = ctx.resolvedSelection
    val relevantFiles = (recentFiles + resolvedSelection.map { it.containingFile.virtualFile }).distinct()

    val promptBuilder = StringBuilder()

    if (!selection.isNullOrEmpty()) {
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
