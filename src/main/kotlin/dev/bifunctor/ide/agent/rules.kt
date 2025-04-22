package dev.bifunctor.ide.agent

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import dev.langchain4j.model.Tokenizer
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Represents a Cursor rule type
 */
enum class CursorRuleType {
    ALWAYS,           // Always included in the model context
    AUTO_ATTACHED,    // Included when files matching a glob pattern are referenced
    AGENT_REQUESTED,  // Rule is available to the AI, which decides whether to include it
    MANUAL            // Only included when explicitly mentioned using @ruleName
}

/**
 * Represents a parsed Cursor rule
 */
data class CursorRule(
    val name: String,
    val type: CursorRuleType,
    val description: String?,
    val content: String,
    val globs: List<String>?,
    val referencedFiles: List<String>?
)

/**
 * Service for managing Cursor rules
 */
@Service(Service.Level.PROJECT)
class CursorRulesService(private val project: Project) {
    private val logger = Logger.getInstance(CursorRulesService::class.java)
    private var rules: List<CursorRule> = emptyList()
    private val projectRulesDir = ".cursor/rules"
    private val legacyRulesFile = ".cursorrules"

    init {
        loadRules()
    }

    /**
     * Loads all rules from the project
     */
    fun loadRules() {
        val projectPath = project.basePath ?: return
        val projectRules = loadProjectRules(projectPath)
        val legacyRules = loadLegacyRules(projectPath)

        rules = projectRules + legacyRules
        logger.info("Loaded ${rules.size} cursor rules")
    }

    /**
     * Gets all rules of a specific type
     */
    fun getRulesByType(type: CursorRuleType): List<CursorRule> {
        return rules.filter { it.type == type }
    }

    /**
     * Gets a rule by name
     */
    fun getRuleByName(name: String): CursorRule? {
        return rules.find { it.name == name }
    }

    /**
     * Gets rules that should be applied for the given files
     */
    fun getRulesForFiles(files: List<VirtualFile>): List<CursorRule> {
        val autoAttachedRules = getRulesByType(CursorRuleType.AUTO_ATTACHED)
        if (autoAttachedRules.isEmpty() || files.isEmpty()) {
            return emptyList()
        }

        val filePaths = files.map { it.path }
        return autoAttachedRules.filter { rule ->
            rule.globs?.any { glob ->
                filePaths.any { filePath ->
                    matchGlob(glob, filePath)
                }
            } ?: false
        }
    }

    /**
     * Gets all rules that should be included in the context
     */
    fun getContextRules(queryContext: QueryContext): List<CursorRule> {
        val alwaysRules = getRulesByType(CursorRuleType.ALWAYS)
        val autoAttachedRules = getRulesForFiles(queryContext.recentFiles)

        return alwaysRules + autoAttachedRules
    }

    /**
     * Loads project rules from .cursor/rules directory
     */
    private fun loadProjectRules(projectPath: String): List<CursorRule> {
        val rulesDir = File(projectPath, projectRulesDir)
        if (!rulesDir.exists() || !rulesDir.isDirectory) {
            return emptyList()
        }

        val ruleFiles = rulesDir.listFiles { file -> file.isFile && file.extension == "mdc" } ?: return emptyList()
        return ruleFiles.mapNotNull { file ->
            try {
                parseMdcRule(file)
            } catch (e: Exception) {
                logger.warn("Failed to parse rule file: ${file.name}", e)
                null
            }
        }
    }

    /**
     * Loads legacy rules from .cursorrules file
     */
    private fun loadLegacyRules(projectPath: String): List<CursorRule> {
        val legacyFile = File(projectPath, legacyRulesFile)
        if (!legacyFile.exists() || !legacyFile.isFile) {
            return emptyList()
        }

        try {
            val content = legacyFile.readText()
            return listOf(
                CursorRule(
                    name = "legacy",
                    type = CursorRuleType.ALWAYS,
                    description = "Legacy cursor rules",
                    content = content,
                    globs = null,
                    referencedFiles = null
                )
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse legacy rules file", e)
            return emptyList()
        }
    }

    /**
     * Parses an MDC rule file
     */
    private fun parseMdcRule(file: File): CursorRule? {
        val content = file.readText()
        val lines = content.lines()

        // Check if the file has frontmatter (metadata)
        if (lines.size < 3 || !lines[0].startsWith("description:")) {
            // Simple rule without metadata
            return CursorRule(
                name = file.nameWithoutExtension,
                type = CursorRuleType.ALWAYS,
                description = null,
                content = content,
                globs = null,
                referencedFiles = null
            )
        }

        // Parse metadata
        var description: String? = null
        var type = CursorRuleType.ALWAYS
        val globs = mutableListOf<String>()
        var i = 0

        while (i < lines.size && !lines[i].startsWith("•")) {
            val line = lines[i].trim()
            when {
                line.startsWith("description:") -> {
                    description = line.substringAfter("description:").trim()
                }
                line.startsWith("globs:") -> {
                    // Next lines contain glob patterns
                    i++
                    while (i < lines.size && lines[i].trim().startsWith("-")) {
                        globs.add(lines[i].trim().substring(1).trim())
                        i++
                    }
                    continue
                }
                line.startsWith("alwaysApply:") -> {
                    val alwaysApply = line.substringAfter("alwaysApply:").trim().toBoolean()
                    type = if (alwaysApply) CursorRuleType.ALWAYS else {
                        if (globs.isNotEmpty()) CursorRuleType.AUTO_ATTACHED else CursorRuleType.AGENT_REQUESTED
                    }
                }
            }
            i++
        }

        // Extract rule content (everything after metadata)
        val contentStartIndex = lines.indexOfFirst { it.startsWith("•") }
        val ruleContent = if (contentStartIndex >= 0) {
            lines.subList(contentStartIndex, lines.size).joinToString("\n")
        } else {
            // If no bullet point is found, use everything after the metadata
            lines.subList(i, lines.size).joinToString("\n")
        }

        // Extract referenced files
        val referencedFiles = extractReferencedFiles(ruleContent)

        return CursorRule(
            name = file.nameWithoutExtension,
            type = type,
            description = description,
            content = ruleContent,
            globs = if (globs.isEmpty()) null else globs,
            referencedFiles = referencedFiles
        )
    }

    /**
     * Extracts referenced files from rule content
     */
    private fun extractReferencedFiles(content: String): List<String>? {
        val regex = Regex("@([\\w.-]+\\.[\\w]+)")
        val matches = regex.findAll(content)
        val files = matches.map { it.groupValues[1] }.toList()
        return if (files.isEmpty()) null else files
    }

    /**
     * Simple glob pattern matching
     */
    private fun matchGlob(glob: String, path: String): Boolean {
        val regex = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return path.matches(Regex(regex))
    }
}

/**
 * Extension function to get cursor rules for a QueryContext
 */
fun QueryContext.getCursorRules(project: Project): List<CursorRule> {
    val rulesService = project.service<CursorRulesService>()
    return rulesService.getContextRules(this)
}

/**
 * Extension function to add cursor rules to a prompt
 */
fun Prompt.addCursorRules(rules: List<CursorRule>): String {
    if (rules.isEmpty()) {
        return ""
    }

    val sb = StringBuilder()
    sb.appendLine("<cursor_rules>")

    rules.forEach { rule ->
        sb.appendLine("# ${rule.name}")
        if (rule.description != null) {
            sb.appendLine("Description: ${rule.description}")
        }
        sb.appendLine(rule.content)
        sb.appendLine()
    }

    sb.appendLine("</cursor_rules>")
    return sb.toString()
}

/**
 * Extension function to modify the Prompt.render method to include cursor rules
 */
fun Prompt.renderWithRules(tokenizer: Tokenizer, maxTokens: Int, ctx: QueryContext, project: Project): String {
    val rules = ctx.getCursorRules(project)
    val basePrompt = this.render(tokenizer, maxTokens, ctx)
    val rulesText = addCursorRules(rules)

    // Insert rules at the beginning of the prompt
    return if (rulesText.isNotEmpty()) {
        rulesText + "\n" + basePrompt
    } else {
        basePrompt
    }
}
