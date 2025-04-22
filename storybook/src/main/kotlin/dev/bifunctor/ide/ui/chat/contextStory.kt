package dev.bifunctor.ide.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.mock.MockVirtualFile
import dev.bifunctor.ide.agent.QueryContext
import dev.bifunctor.ide.ui.components.chat.ChatContext
import dev.bifunctor.ide.ui.story
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun ChatContextPreview() {
  // Example contexts showcasing recent files, selection, or both
  val contextExamples = listOf(
    "No Context" to QueryContext(),
    "Just Selection" to QueryContext(selection = "val n = 42 // a snippet"),
    "With Recent Files" to QueryContext(
      recentFiles = listOf(
        MockVirtualFile.file("OneFile.kt"),
        MockVirtualFile.file("MyClass.java")
      )
    ),
    "Files + Selection" to QueryContext(
      recentFiles = listOf(MockVirtualFile.file("SomeService.kt")),
      selection = "fun greet(name: String) { println(\"Hello \$name\") }"
    )
  )

  var selectedExample by remember { mutableStateOf(contextExamples.first()) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // A basic dropdown to pick which context to show
    Dropdown(
      menuContent = {
        contextExamples.forEach { (label, ctx) ->
          selectableItem(
            selected = (selectedExample.first == label),
            iconKey = AllIconsKeys.Actions.Checked, // or null
            onClick = { selectedExample = (label to ctx) }
          ) {
            Text(label)
          }
        }
      }
    ) {
      Text(selectedExample.first)
    }

    Text("Context:")
    ChatContext(chatCtx = selectedExample.second)
  }
}

fun main() = story {
  ChatContextPreview()
}
