package dev.bifunctor.ide.agent.prompts

private val CONTEXT_PLACEHOLDER = "<@context@>"

class Prompt(
  private val template: String,
) {
  fun render(contextPrompt: String): String {
    return template.replace(CONTEXT_PLACEHOLDER, contextPrompt)
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
