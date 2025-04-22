package dev.bifunctor.ide.agent

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.find.TextSearchService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.PathUtil
import com.intellij.util.Processor
import com.intellij.util.io.awaitExit
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.service.tool.*
import kotlinx.coroutines.*
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.io.File
import java.io.StringWriter
import java.lang.reflect.Method
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class ToolState {
  INITIAL,
  WAITING_FOR_APPROVAL,
  REJECTED,
  EXECUTING,
}

enum class ApprovalMode(val autoApprovedCaps: Set<ToolCapability>) {
  ALWAYS_ASK(setOf(ToolCapability.READ_ONLY)),
  ASK_FOR_SHELL_COMMANDS(setOf(ToolCapability.READ_ONLY, ToolCapability.READ_WRITE)),
  AUTO_APPROVE(ToolCapability.entries.toSet())
}

@Service(Service.Level.APP)
class ToolStateService {
  private val states = mutableMapOf<String, MutableState<ToolState>>()
  private val approvalSignals = mutableMapOf<String, CompletableDeferred<Boolean>>()
  private val approvalModeSetting = mutableMapOf<String, ApprovalMode>()

  fun getState(toolReqId: String): State<ToolState> {
    return states.computeIfAbsent(toolReqId, { mutableStateOf(ToolState.INITIAL) })
  }

  fun updateState(toolReqId: String, state: ToolState) {
    val currentState = states.computeIfAbsent(toolReqId, { mutableStateOf(ToolState.INITIAL) })
    currentState.value = state
  }

  fun requestApproval(toolReqId: String) {
    updateState(toolReqId, ToolState.WAITING_FOR_APPROVAL)
    approvalSignals[toolReqId] = CompletableDeferred()
  }

  suspend fun awaitApproval(toolReqId: String): Boolean {
    val deferred = approvalSignals[toolReqId]
    if (deferred == null) {
      return false
    }
    return deferred.await()
  }

  fun approve(toolReqId: String) {
    updateState(toolReqId, ToolState.EXECUTING)
    approvalSignals[toolReqId]?.complete(true)
  }

  fun reject(toolReqId: String) {
    updateState(toolReqId, ToolState.REJECTED)
    approvalSignals[toolReqId]?.complete(false)
  }

  fun setApprovalMode(conversationId: String, mode: ApprovalMode) {
    approvalModeSetting[conversationId] = mode
  }

  fun getApprovalMode(conversationId: String): ApprovalMode {
    return approvalModeSetting[conversationId] ?: ApprovalMode.ALWAYS_ASK
  }
}

enum class ToolCapability {
  /** The default capability allowing tools to read files. */
  READ_ONLY,

  /** Allows the tool to execute actions that modify files on the file system. */
  READ_WRITE,

  /** Allows the tool to execute arbitrary shell commands. */
  EXECUTE_SHELL_COMMANDS;
}

interface BifTool {
  companion object {
    val LOG: Logger = Logger.getInstance(BifTool::class.java)
  }

  val name: String
  val project: Project
  val toolCapabilities: Set<ToolCapability>
  val iconKey: IconKey?
    get() = null
  val toolStateService: ToolStateService
    get() = service<ToolStateService>()
  val projectBasePath
    get() = project.basePath //
      ?: throw RuntimeException("Project base path is null.")

  fun executeTool(timeout: Duration = 10.seconds, runTool: suspend () -> String): String {
    val toolContext = ToolContextProvider.getCurrentToolContext()
    val toolReqId = toolContext.toolExecutionRequest.id()
    val approvalMode = toolContext.approvalMode

    val needsApproval = !approvalMode.autoApprovedCaps.containsAll(toolCapabilities)
    if (needsApproval) {
      toolStateService.requestApproval(toolReqId)
    } else {
      toolStateService.updateState(toolReqId, ToolState.EXECUTING)
    }
    return runBlocking {
      try {
        // await for approval
        if (needsApproval) {
          toolStateService.awaitApproval(toolReqId)
          val state = toolStateService.getState(toolReqId)
          if (state.value == ToolState.REJECTED) {
            throw RuntimeException("tool execution was rejected.")
          }
        }

        withTimeout(timeout) {
          withContext(Dispatchers.IO) {
            val toolOutput = runTool()
            when {
              toolOutput.isNotBlank() -> toolOutput
              // Empty output causes langchain4j to hang, so return a placeholder
              // TODO: dive deeper to find the source of weird behavior
              else -> "the tool completed without any output."
            }
          }
        }
      } catch (e: TimeoutCancellationException) {
        LOG.warn("The tool $name timed out after $timeout", e)
        throw RuntimeException("Timeout after $timeout")
      }
    }
  }
}

private fun findVirtualFileInProject(projectBasePath: String, dirPath: String): Pair<VirtualFile, VirtualFile> {
  // Find the project root directory in the VFS
  val projectRoot = LocalFileSystem.getInstance().findFileByPath(projectBasePath)
    ?: throw RuntimeException("Could not locate project root: $projectBasePath")
  // Locate the target directory, assuming dirPath is relative to the project root
  val relativeFile = projectRoot.findFileByRelativePath(dirPath)
    ?: throw RuntimeException("Directory not found at '$dirPath' (relative to: $projectBasePath)")
  return Pair(projectRoot, relativeFile)
}


class ShellTool(override val project: Project) : BifTool {
  override val name: String = "terminalCommand"
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.EXECUTE_SHELL_COMMANDS)
  override val iconKey: IconKey = AllIconsKeys.Actions.Execute

  @Tool(
    name = "runShellCommand", value = ["Executes a shell command in IntelliJ's integrated terminal."]
  )
  fun runCommand(command: String): String = executeTool(timeout = 1000.seconds) {
    val processBuilder = ProcessBuilder("bash", "-c", command)
    processBuilder.directory(Paths.get(projectBasePath).toFile())
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.awaitExit()
    if (exitCode != 0) {
      throw RuntimeException("Command '$command' failed with exit code $exitCode. Output:\n$output")
    }
    output
  }
}


// Verification tools

class RunTestsTool(override val project: Project) : BifTool {
  override val name: String = "runTests"
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)
  override val iconKey: IconKey = AllIconsKeys.RunConfigurations.TestState.Run

  private val mapper = ObjectMapper().registerKotlinModule()

  @Tool(
    name = "runTests", value = [ //
      "Runs Java tests for a fully qualified class optionally followed by #methodName.", //
      "Example: com.example.MyTestClass or com.example.MyTestClass#testSomething" //
    ]
  )
  fun runTests(testReference: String): String = executeTool(timeout = 5.minutes) {
    val (className, methodName) = parseClassAndMethod(testReference)
    val context = ReadAction.compute<ConfigurationFromContext, Throwable> {
      val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
      if (psiClass == null) {
        throw RuntimeException("Class not found: $className")
      }
      val psiMethod =
        if (methodName.isNullOrEmpty()) null else psiClass.findMethodsByName(methodName, false).firstOrNull()
      ConfigurationContext.createEmptyContextForLocation(PsiLocation(psiMethod ?: psiClass))
        .createConfigurationsFromContext()
        ?.first() ?: throw RuntimeException("No configuration found for class: $className")
    }
    val configuration = context.configuration
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val latch = CountDownLatch(1)
    val outputBuffer = StringBuilder()
    val exitCodeAtomic = AtomicInteger(-1)
    val environment = ExecutionEnvironmentBuilder.create(executor, configuration) //
      .build { descriptor ->
        descriptor.processHandler?.addProcessListener(object : ProcessAdapter() {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            outputBuffer.append(event.text)
          }

          override fun processTerminated(event: ProcessEvent) {
            exitCodeAtomic.set(event.exitCode)
            latch.countDown()
          }
        })
      }
    ApplicationManager.getApplication().invokeLater {
      ProgramRunnerUtil.executeConfiguration(environment, false, true)
    }
    ApplicationManager.getApplication().executeOnPooledThread {
      latch.await()
    }.get()
    val (exitCode, output) = exitCodeAtomic.get() to outputBuffer.toString()
    val result = TestResult( //
      className = className, //
      methodName = methodName, //
      exitCode = exitCode, //
      output = output //
    )

    mapper.writeValueAsString(result)
  }

  private fun parseClassAndMethod(input: String): Pair<String, String?> {
    val idx = input.indexOf('#')
    return if (idx < 0) input to null else {
      input.substring(0, idx) to input.substring(idx + 1)
    }
  }
}

private data class TestResult(
  val className: String, val methodName: String?, val exitCode: Int, val output: String
)

// Git tools

class LocalDiffTool(override val project: Project) : BifTool {
  override val name: String = "showLocalDiff"
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)
  override val iconKey: IconKey = AllIconsKeys.Actions.Diff

  @Tool(
    name = "showLocalDiff",
    value = ["Generates a unified diff representing all local changes in the project’s default changelist."]
  )
  fun showLocalDiff(): String = executeTool {
    calcDiff()
  }

  private fun calcDiff(): String {
    val changeManager = ChangeListManager.getInstance(project)
    // (A) Gather tracked changes from the default changelist
    val trackedChanges = changeManager.defaultChangeList.changes
    // (B) Gather unversioned files and turn them into synthetic changes
    val unversionedChanges = changeManager.unversionedFilesPaths.filter { !it.isDirectory }.map { file ->
      val afterRev = CurrentContentRevision(file)
      Change(null, afterRev)
    }

    val allChanges = trackedChanges + unversionedChanges
    if (allChanges.isEmpty()) {
      return "No changes found."
    }

    val patches = IdeaTextPatchBuilder.buildPatch(
      project, allChanges.toList(), Paths.get(projectBasePath), false, true
    )

    val writer = StringWriter()
    UnifiedDiffWriter.write(project, patches, writer, "\n", null)
    return writer.toString()
  }
}


// Code-Insight Tools

class FindClassTool(override val project: Project) : BifTool {

  override val name = "findClassByName"
  override val toolCapabilities = setOf(ToolCapability.READ_ONLY)
  override val iconKey: IconKey = AllIconsKeys.Nodes.Class

  @Tool(
    name = "findClassByName",
    value = [
      "Given a short class/object/type‑alias name, e.g. 'Runnable' or 'CoroutineScope',",
      "returns suggestions sorted by relevancy.",
      "Each line is 'fullyQualifiedName|filePath' (path is relative to project root)."
    ]
  )
  fun findClassByName(shortName: String): String = executeTool {
    if (shortName.isBlank()) error("blank class name")

    val scope = GlobalSearchScope.allScope(project)
    val basePath = project.basePath ?: error("Project base path is null")

    val hits: List<Pair<String, String>> = ReadAction.compute<List<Pair<String, String>>, Throwable> {
      PsiShortNamesCache.getInstance(project)
        .getClassesByName(shortName, scope)
        .asSequence()
        .mapNotNull { psiClass ->
          val fqn = (psiClass as? PsiNamedElement)?.name?.let { psiClass.qualifiedName }
            ?: return@mapNotNull null
          val vf = psiClass.containingFile?.virtualFile ?: return@mapNotNull null
          val relPath = vf.path.removePrefix(basePath).trimStart('/', '\\')
          fqn to relPath
        }
        .distinct()               // avoid duplicates coming from light classes
        .sortedWith(
          compareBy<Pair<String, String>> { (_, p) -> p.count { it == '/' || it == '\\' } } // prefer shallower paths
            .thenBy { it.second }                                                         // then lexicographically
        )
        .toList()
    }

    if (hits.isEmpty()) "no matches found."
    else hits.joinToString("\n") { "${it.first}|${it.second}" }
  }
}

@Suppress("UnstableApiUsage")
class FindInFilesTool(override val project: Project) : BifTool {
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)
  override val name: String = "findTextInFiles"
  override val iconKey: IconKey = AllIconsKeys.Actions.Find

  private val textSearchService = TextSearchService.getInstance()

  @Tool(
    name = "findTextInFiles",
    value = ["Searches for occurrences of the specified text within the project or within a specified directory.", "If a directory path is provided (relative to project root), the search is limited to that directory and its subdirectories.", "Returns up to 100 results as 'relative/path:lineNumber:entireLine'.", "Usage examples:", "  findTextInFiles(\"MyClass\")                         // searches entire project", "  findTextInFiles(\"TODO\", \"src/main/java\")         // searches only in src/main/java"]
  )
  fun findTextInFiles(text: String, dirPath: String? = null): String = executeTool(timeout = 10.seconds) {
    findInFiles(text, dirPath)
  }

  private fun findInFiles(text: String, dirPath: String?): String {
    if (text.isBlank()) {
      throw RuntimeException("blank text")
    }

    val projectBasePath = projectBasePath
    val scope = if (!dirPath.isNullOrBlank()) {
      val (_, directoryVf) = findVirtualFileInProject(projectBasePath, dirPath)
      if (!directoryVf.isDirectory) {
        throw RuntimeException("Provided path is not a directory: $dirPath")
      }
      GlobalSearchScopesCore.directoriesScope(project, true, directoryVf)
    } else {
      GlobalSearchScope.projectScope(project)
    }

    val matches = mutableListOf<String>()
    ReadAction.run<RuntimeException> {
      textSearchService.processFilesWithText(text, Processor { virtualFile ->
        if (!textSearchService.isInSearchableScope(virtualFile, project)) {
          return@Processor true
        }
        val fileText = VfsUtil.loadText(virtualFile)
        val lines = fileText.lines()
        for ((index, line) in lines.withIndex()) {
          if (line.contains(text)) {
            val relativePath = virtualFile.path.removePrefix(projectBasePath).trimStart('/', '\\')
            matches.add("$relativePath:${index + 1}:$line")
            if (matches.size >= 75) {
              matches.add("... and more matches found.")
              return@Processor false
            }
          }
        }
        true
      }, scope)
    }
    if (matches.isEmpty()) {
      return "no matches found."
    }
    return matches.joinToString("\n")
  }
}


// File-System Tools

class ListFilesTool(override val project: Project) : BifTool {
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)
  override val name: String = "listFiles"
  override val iconKey: IconKey = AllIconsKeys.Nodes.Folder

  @Tool(
    name = "listFiles",
    value = ["List the files (and subdirectories) of the provided directory path as a single string of *relative* paths, one per line. Relative to the project root."]
  )
  fun listFiles(dirPath: String): String = executeTool {
    val (projectRoot, targetDir) = findVirtualFileInProject(projectBasePath, dirPath)
    if (!targetDir.isDirectory) {
      throw RuntimeException("Path is not a directory: $dirPath")
    }
    // Collect all child paths as relative to the project root
    val childPaths = ReadAction.compute<List<String>, Throwable> {
      targetDir.children //
        ?.map { child -> //
          // Strip off the projectRoot.path prefix
          child.path.removePrefix(projectRoot.path).trimStart(File.separatorChar)
        } //
        ?.sorted() //
        ?: emptyList()
    }
    childPaths.joinToString("\n")
  }
}

class WriteFileTool(override val project: Project) : BifTool {
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_WRITE)
  override val name: String = "writeFile"
  override val iconKey: IconKey = AllIconsKeys.Actions.Edit

  @Tool(
    name = "writeFile",
    value = ["Write the provided content to a file at the provided path (relative to project root).", "Automatically creates any missing directories along the path."]
  )
  fun writeFile(filePath: String, content: String) = executeTool {
    val projectBasePath = projectBasePath
    val projectRootVf = LocalFileSystem.getInstance().findFileByPath(projectBasePath)
      ?: throw RuntimeException("Could not locate project root: $projectBasePath")

    ApplicationManager.getApplication().invokeAndWait({
      WriteAction.run<Throwable> {
        val parentPath = PathUtil.getParentPath(filePath)
        val fileName = PathUtil.getFileName(filePath)
        val parentVf = VfsUtil.createDirectoryIfMissing(projectRootVf, parentPath)
          ?: throw RuntimeException("Failed to create directories for path: $parentPath")
        val targetVf = parentVf.findChild(fileName) ?: parentVf.createChildData(this, fileName)

        VfsUtil.saveText(targetVf, content)
      }
    }, ModalityState.nonModal())
    "ok"
  }
}

class ReadFileTool(override val project: Project) : BifTool {
  override val name: String = "readFile"
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)
  override val iconKey: IconKey = AllIconsKeys.Actions.Preview

  @Tool(name = "readFile", value = ["Read the content of the file at the provided path (relative to project root)."])
  fun readFile(filePath: String): String = executeTool {
    val (_, virtualFile) = findVirtualFileInProject(projectBasePath, filePath)

    ReadAction.compute<String, Throwable> {
      VfsUtil.loadText(virtualFile)
    }
  }
}

class FindReplaceInFileTool(override val project: Project) : BifTool {
  override val name: String = "findReplaceInFile"
  override val toolCapabilities: Set<ToolCapability> = setOf(ToolCapability.READ_WRITE)
  override val iconKey: IconKey = AllIconsKeys.Actions.Replace

  @Tool(
    name = "findReplaceInFile",
    value = ["Find and replace text in a file at the provided path (relative to project root).", "Returns the number of replacements made."]
  )
  fun findReplaceInFile(filePath: String, searchText: String, replaceText: String): String = executeTool {
    val (_, virtualFile) = findVirtualFileInProject(projectBasePath, filePath)

    val content = ReadAction.compute<String, Throwable> {
      VfsUtil.loadText(virtualFile)
    }

    val newContent = content.replace(searchText, replaceText)
    val replacementCount = content.split(searchText).size - 1

    ApplicationManager.getApplication().invokeAndWait({
      WriteAction.run<Throwable> {
        VfsUtil.saveText(virtualFile, newContent)
      }
    }, ModalityState.nonModal())

    "Made $replacementCount replacements."
  }
}

interface ToolService : ToolProvider {
  fun findToolByName(name: String): BifTool?
}

class ToolServiceImpl(project: Project) : ToolService {
  private val tools: List<BifTool> = validate(
    listOf(
      WriteFileTool(project),
      ReadFileTool(project),
      ListFilesTool(project),
      FindClassTool(project),
      FindInFilesTool(project),
      RunTestsTool(project),
      LocalDiffTool(project),
      ShellTool(project),
      FindReplaceInFileTool(project),
    )
  )

  private fun validate(tools: List<BifTool>): List<BifTool> {
    // ensure the names are unique
    if (tools.size != tools.map { it.name }.size) {
      throw RuntimeException("Tool names must be unique: $tools")
    }
    return tools
  }

  override fun findToolByName(name: String): BifTool? {
    return tools.find { it.name == name }
  }

  override fun provideTools(request: ToolProviderRequest): ToolProviderResult {
    val convId = request.chatMemoryId() as? String
      ?: throw RuntimeException("expected convId to be a String, but got: ${request.chatMemoryId()}")
    val toolMap = mutableMapOf<ToolSpecification, ToolExecutor>()
    for (tool in tools) {
      for (method in tool.javaClass.declaredMethods) {
        if (method.isAnnotationPresent(Tool::class.java)) {
          val toolSpecification = ToolSpecifications.toolSpecificationFrom(method)
          toolMap[toolSpecification] = ContextToolExecutor(tool, method, convId)
        }
      }
    }
    return ToolProviderResult(toolMap)
  }
}

object ToolContextProvider {
  private val contextHolder = ThreadLocal<ContextToolExecutor.ToolContext?>()

  fun getCurrentToolContext(): ContextToolExecutor.ToolContext {
    val ctx = contextHolder.get()
    if (ctx == null) {
      throw RuntimeException("ToolContext is not set. Make sure to call setCurrentToolContext() before using it.")
    }
    return ctx
  }

  fun setCurrentToolContext(toolContext: ContextToolExecutor.ToolContext?) {
    contextHolder.set(toolContext)
  }
}

class ContextToolExecutor(obj: Any, method: Method, private val convId: String) : DefaultToolExecutor(obj, method) {
  class ToolContext(
    val toolExecutionRequest: ToolExecutionRequest, val approvalMode: ApprovalMode
  )

  private val toolStateService = service<ToolStateService>()

  override fun execute(toolExecutionRequest: ToolExecutionRequest, memoryId: Any): String {
    val approvalMode = toolStateService.getApprovalMode(convId)
    val toolContext = ToolContext(toolExecutionRequest, approvalMode)

    try {
      ToolContextProvider.setCurrentToolContext(toolContext)
      val result = super.execute(toolExecutionRequest, memoryId)
      return result
    } finally {
      ToolContextProvider.setCurrentToolContext(null)
    }
  }
}
