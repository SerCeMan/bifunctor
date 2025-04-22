package dev.bifunctor.ide.completion

/**
 * Matches:
 * ```
 * optional-language-or-other-characters on same line
 * optional newline
 * (captured content)
 * optional newline
 * ```
 *
 * The [^\n]* covers e.g. "java", "kotlin", or anything else after ``` on that line.
 * The ? after \n in the regex ensures that if there's a newline right after the language,
 * we skip it (but it doesn't break if the code starts immediately).
 * The (.*?) then captures all content until an optional trailing newline + ```
 */
private val tripleBacktickRegex = Regex(
  pattern = """^```[^\n]*\n?(.*?)\n?```$""", //
  options = setOf(RegexOption.DOT_MATCHES_ALL)
)

private val singleBacktickRegex = Regex(
  pattern = """^`([^`]*)`$"""
)

fun stripMarkdownTags(code: String): String {
  val trimmed = code.trim()
  tripleBacktickRegex.matchEntire(trimmed)?.let {
    return it.groupValues[1].trim()
  }
  singleBacktickRegex.matchEntire(trimmed)?.let {
    return it.groupValues[1].trim()
  }
  return trimmed
}
