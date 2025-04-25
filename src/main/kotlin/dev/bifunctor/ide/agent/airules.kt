package dev.bifunctor.ide.agent

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
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

class AiRuleServiceImpl(private val project: Project) : AiRuleService {
  private val rules: List<AiRule>
  private val cursorRulesDir = ".cursor/rules"

  init {
    rules = loadRules()
  }

  fun loadRules(): List<AiRule> {
    val projectPath = project.basePath
    if (projectPath == null) {
      LOG.warn("project path is null, can't load rules")
      return emptyList()
    }
    return loadCursorRules(projectPath)
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

  private fun loadCursorRules(projectPath: String): List<AiRule> {
    val rulesDir = File(projectPath, cursorRulesDir)
    if (!rulesDir.exists() || !rulesDir.isDirectory) {
      return emptyList()
    }

    val ruleFiles = rulesDir.listFiles { file -> file.isFile && file.extension == "mdc" } ?: return emptyList()
    return ruleFiles.mapNotNull { file ->
      try {
        parseMdcRule(file)
      } catch (e: Exception) {
        LOG.warn("Failed to parse rule file: ${file.name}", e)
        null
      }
    }
  }

  private fun parseMdcRule(file: File): AiRule? {
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
            val lineGlobs = globLine.split(",").map { it.trim() }
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
    return FileSystems.getDefault().getPathMatcher("glob:${glob}").matches(Path(path))
  }
}
