package dev.bifunctor.ide.agent

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AiRuleServiceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = "src/test/testData/projects"

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("tiny-pet-clinic", "")
  }

  fun testLoadsAllCursorRules() {
    val service = AiRuleServiceImpl(project)
    val rules = service.getAllRules()

    assertSize(3, rules)
    assertContainsElements(
      rules.map { it.name },  //
      "java-style-guide", "kotlin-style-guide", "general-style-guide"
    )
  }

  fun testGetRulesForFilesHonoursGlobs() {
    val service = AiRuleServiceImpl(project)

    val javaFile: VirtualFile = myFixture.findFileInTempDir(
      "src/main/java/tinypetclinic/TinyPetClinic.java"
    ) ?: error("TinyPetClinic.java was not copied to the temporary project")

    val matched = service.getRulesForFiles(listOf(javaFile))

    assertSize(2, matched)
    assertContainsElements(
      matched.map { it.name }, //
      "java-style-guide", "general-style-guide"
    )
  }
}
