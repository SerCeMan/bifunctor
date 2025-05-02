package dev.bifunctor.ide.agent

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import dev.bifunctor.ide.agent.prompts.Prompt
import dev.bifunctor.ide.agent.prompts.PromptServiceImpl
import dev.langchain4j.model.openai.OpenAiTokenizer

class PromptServiceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = "src/test/testData/projects"

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("tiny-pet-clinic", "")
  }

  fun testRenderPrompt() {
    val ruleService = AiRuleServiceImpl(project)
    project.registerServiceInstance(AiRuleService::class.java, ruleService)
    val promptService = PromptServiceImpl(project)
    val prompt = Prompt("Test prompt with context: <@context@>")

    val petClinicFile: VirtualFile = myFixture.findFileInTempDir(
      "src/main/java/tinypetclinic/TinyPetClinic.java"
    )

    val queryContext = QueryContext( //
      selection = "test selection", //
      recentFiles = listOf(petClinicFile), //
      resolvedSelection = emptyList() //
    )
    val tokenizer = OpenAiTokenizer("gpt-4o")
    val renderedPrompt = promptService.renderPrompt(prompt, tokenizer, 1000, queryContext)

    assertEquals(
      """
        Test prompt with context: <selection>
        test selection
        </selection><relevant_files>
        /src/src/main/java/tinypetclinic/TinyPetClinic.java
        class TinyPetClinic {
            public static void main(String[] args) {
                System.out.println("Hello, Tiny Pet Clinic!");
            }
        }

        </relevant_files>
        <ai_rules>

        Make sure every file has a JavaDoc header.


        Write consistent code.

        # Tiny Pet Clinic Guidelines

        Avoid adding unnecessary comments.

        </ai_rules>

      """.trimIndent(),
      renderedPrompt
    )
  }
}
