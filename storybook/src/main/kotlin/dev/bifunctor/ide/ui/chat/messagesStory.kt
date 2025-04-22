package dev.bifunctor.ide.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bifunctor.ide.agent.ChatFixtures
import dev.bifunctor.ide.agent.IncomingLlmMessage
import dev.bifunctor.ide.ui.components.chat.ChatMessages
import dev.bifunctor.ide.ui.story
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ChatMessagesPreview() {
  val conversationExamples = listOf(
    "Fake Conversation" to ChatFixtures.fakeConversation,
    "Error Messages Only" to ChatFixtures.withErrorMessages,
  )

  var selectedConversation by remember { mutableStateOf(conversationExamples.first()) }
  val incompleteExample = IncomingLlmMessage("And the final answer is ...")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.Top
  ) {
    Dropdown(
      menuContent = {
        conversationExamples.forEach { (label, messages) ->
          selectableItem(
            selected = (selectedConversation.first == label),
            onClick = { selectedConversation = (label to messages) }
          ) {
            Text(label)
          }
        }
      }
    ) {
      Text(selectedConversation.first)
    }

    Spacer(Modifier.height(16.dp))
    ChatMessages(
      messages = selectedConversation.second,
      incomingMessage = incompleteExample
    )
  }
}

fun main() = story {
  ChatMessagesPreview()
}
