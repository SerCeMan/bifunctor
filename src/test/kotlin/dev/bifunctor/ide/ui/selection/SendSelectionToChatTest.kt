package dev.bifunctor.ide.ui.selection

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import dev.bifunctor.ide.agent.ContextCollector
import dev.bifunctor.ide.agent.ContextCollectorImpl
import dev.bifunctor.ide.agent.SelectionService
import dev.bifunctor.ide.agent.SelectionServiceImpl
import org.intellij.lang.annotations.Language

class SendSelectionToChatTest : BasePlatformTestCase() {
  fun testWithoutSelection() {
    verifyJavaSelection(
      "class A {}",
      null
    )
  }

  fun testWithInlineSelection() {
    verifyJavaSelection(
      """
        class Clz1 {
            static class B {
            }
            
            static class A {
            }
            
            public static void main(String[] args) {
                <selection>A a = new A();</selection>
            }
        }
      """.trimIndent(),
      "A a = new A();"
    )
  }

  private fun verifyJavaSelection(@Language("Java") javaInput: String, expectedResult: String?) {
    val pointerService = SelectionServiceImpl()
    val contextCollector = ContextCollectorImpl(project)
    myFixture.project.registerServiceInstance(SelectionService::class.java, pointerService)
    myFixture.project.registerServiceInstance(ContextCollector::class.java, contextCollector)
    myFixture.configureByText(getTestName(false) + ".java", javaInput)
    val action = SendSelectionToChat()
    assertEquals(expectedResult != null, myFixture.testAction(action).isEnabled)
    if (expectedResult != null) {
      val selection = pointerService.lastSelectionCtx.value
      assertEquals(selection?.selection, expectedResult)
      assertEquals(selection?.resolvedSelection?.size, 1)
      assertEquals((selection?.resolvedSelection?.get(0) as? PsiClass)?.name, "A")
    }
  }
}
