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

    assertSize(4, rules)
    assertContainsElements(
      rules.map { it.name },  //
      "java-style-guide", "kotlin-style-guide", "general-style-guide", "junie-guidelines"
    )
  }

  fun testGetRulesForFilesHonoursGlobs() {
    val service = AiRuleServiceImpl(project)

    val javaFile: VirtualFile = myFixture.findFileInTempDir(
      "src/main/java/tinypetclinic/TinyPetClinic.java"
    ) ?: error("TinyPetClinic.java was not copied to the temporary project")

    val matched = service.getRulesForFiles(listOf(javaFile))

    assertSize(3, matched)
    assertContainsElements(
      matched.map { it.name }, //
      "java-style-guide", "general-style-guide", "junie-guidelines"
    )
  }

  fun testFindsBuildGradleKts() {
    val service = AiRuleServiceImpl(project)
    val buildGradleKts: VirtualFile = myFixture.findFileInTempDir(
      "build.gradle.kts"
    ) ?: error("build.gradle.kts was not copied to the temporary project")

    val matchedKts = service.getRulesForFiles(listOf(buildGradleKts))

    assertSize(3, matchedKts)
    assertContainsElements(
      matchedKts.map { it.name }, //
      "kotlin-style-guide"
    )

    val buildGradle: VirtualFile = myFixture.findFileInTempDir(
      "build.gradle"
    ) ?: error("build.gradle was not copied to the temporary project")
    val matchBuildGradle = service.getRulesForFiles(listOf(buildGradle))
    assertSize(2, matchBuildGradle)
    assertDoesntContain(
      matchBuildGradle.map { it.name }, //
      "kotlin-style-guide"
    )
  }

  fun testMatchGlob() {
    val service = AiRuleServiceImpl(project)

    // Test basic glob matching
    assertTrue(service.matchGlob("*.java", "/path/to/file.java"))
    assertFalse(service.matchGlob("*.java", "/path/to/file.kt"))

    // Test directory matching
    assertTrue(service.matchGlob("src/**/*.java", "/path/to/src/main/java/file.java"))
    assertFalse(service.matchGlob("src/**/*.java", "/path/to/test/java/file.java"))

    // Test multiple extensions
    assertTrue(service.matchGlob("*.{java,kt}", "/path/to/file.java"))
    assertTrue(service.matchGlob("*.{java,kt}", "/path/to/file.kt"))
    assertFalse(service.matchGlob("*.{java,kt}", "/path/to/file.py"))

    // Test complex patterns
    assertTrue(service.matchGlob("**/*Test.{java,kt}", "/path/to/src/test/java/MyTest.java"))
    assertTrue(service.matchGlob("**/*Test.{java,kt}", "/path/to/src/test/kotlin/MyTest.kt"))
    assertFalse(service.matchGlob("**/*Test.{java,kt}", "/path/to/src/main/java/MyClass.java"))
  }
}
