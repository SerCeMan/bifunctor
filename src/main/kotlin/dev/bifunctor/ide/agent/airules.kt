package dev.bifunctor.ide.agent

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import java.nio.file.FileSystems
import kotlin.io.path.Path

private val LOG = logger<AiRuleServiceImpl>()

data class AiRule(
  val name: String,
  val description: String?,
  val content: String,
  val globs: List<String>,
)

interface AiRuleService {
  fun getRulesForFiles(files: List<VirtualFile>): List<AiRule>
}

class AiRuleServiceImpl : AiRuleService {
  private val rules: List<AiRule>
  private val cursorRulesDir = ".cursor/rules"
  private val junieGuidelinesPath = ".junie/guidelines.md"

  constructor(project: Project) {
    rules = loadRules(project.guessProjectDir())
  }

  fun getAllRules(): List<AiRule> {
    return rules
  }

  private fun loadRules(projectDir: VirtualFile?): List<AiRule> {
    if (projectDir == null) {
      LOG.warn("project path is null, can't load rules")
      return emptyList()
    }
    val cursorRules = loadCursorRules(projectDir)
    val junieRules = loadJunieGuidelines(projectDir)
    return cursorRules + junieRules
  }

  private fun loadJunieGuidelines(projectDir: VirtualFile): List<AiRule> =
    ReadAction.compute<List<AiRule>, RuntimeException> {
      val guidelinesFile = projectDir.findFileByRelativePath(junieGuidelinesPath)
      return@compute when (guidelinesFile) {
        null -> emptyList()
        else -> try {
          val content = guidelinesFile.readText()
          val guidelines = AiRule( //
            name = "junie-guidelines", //
            description = "Junie Guidelines", //
            content = content, //
            globs = emptyList() //
          )
          listOf(guidelines)
        } catch (e: Exception) {
          LOG.warn("Failed to parse guidelines file: ${guidelinesFile.name}", e)
          emptyList()
        }
      }
    }

  override fun getRulesForFiles(files: List<VirtualFile>): List<AiRule> {
    val filePaths = files.map { it.path }
    return rules.filter { rule ->
      when {
        rule.globs.isEmpty() -> true
        else -> rule.globs.any { glob ->
          filePaths.any { filePath ->
            matchGlob(glob, filePath)
          }
        }
      }
    }
  }

  private fun loadCursorRules(projectDir: VirtualFile): List<AiRule> =
    ReadAction.compute<List<AiRule>, RuntimeException> {
      val rulesRoot = projectDir.findFileByRelativePath(cursorRulesDir)
      when (rulesRoot) {
        null -> {
          LOG.warn("No rules found in .cursor/rules")
          return@compute emptyList()
        }

        else -> rulesRoot //
          .children //
          .asSequence() //
          .filter { !it.isDirectory && it.extension.equals("mdc", true) } //
          .mapNotNull { vf -> tryParseMdcFile(vf) } //
          .toList()
      }
    }

  private fun tryParseMdcFile(vf: VirtualFile): AiRule? = try {
    parseMdcRule(vf)
  } catch (e: Exception) {
    LOG.warn("Failed to parse rule file: ${vf.name}", e)
    null
  }

  private fun splitGlobLine(globLine: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var braceLevel = 0
    var i = 0
    while (i < globLine.length) {
      when (globLine[i]) {
        '{' -> braceLevel++
        '}' -> braceLevel--
        ',' -> {
          if (braceLevel == 0) {
            if (current.isNotEmpty()) {
              result.add(current.toString().trim())
              current = StringBuilder()
            }
            i++
            continue
          }
        }
      }
      current.append(globLine[i])
      i++
    }
    if (braceLevel != 0) {
      throw IllegalArgumentException("Unmatched braces in glob line: $globLine")
    }
    if (current.isNotEmpty()) {
      result.add(current.toString().trim())
    }
    return result
  }

  private fun parseMdcRule(file: VirtualFile): AiRule? {
    val content = file.readText()
    val lines = content.lines()
    // Parse metadata
    var description: String? = null
    val globs = mutableListOf<String>()
    for (fileLine in lines) {
      val line = fileLine.trim()
      when {
        line.startsWith("description:") -> {
          description += line.substringAfter("description:").trim()
        }

        line.startsWith("globs:") -> {
          val globLine = line.substringAfter("globs:").trim()
          if (globLine.isNotEmpty()) {
            val lineGlobs = splitGlobLine(globLine)
            globs.addAll(lineGlobs)
          }
        }
      }
    }

    // Extract rule content (everything after metadata)
    val lastMetadataLine = lines.lastIndexOf("---")
    val contentStartIndex = 0.coerceAtLeast(lastMetadataLine + 1)
    val ruleContent = lines.subList(contentStartIndex.coerceAtMost(lines.size), lines.size).joinToString("\n")
    return AiRule(
      name = file.nameWithoutExtension,
      description = description,
      content = ruleContent,
      globs = globs,
    )
  }

  fun matchGlob(glob: String, path: String): Boolean {
    val fileSystem = FileSystems.getDefault()
    val matcher = fileSystem.getPathMatcher("glob:**/${glob}")
    return matcher.matches(Path(path))
  }
}
