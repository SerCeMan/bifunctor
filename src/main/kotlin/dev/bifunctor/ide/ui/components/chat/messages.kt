package dev.bifunctor.ide.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import dev.bifunctor.ide.agent.*
import dev.bifunctor.ide.agent.prompts.Prompts
import dev.bifunctor.ide.ui.components.markdown.MarkdownText
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys.RunConfigurations.TestPassed
import kotlin.math.max
import kotlin.math.min

sealed class MessageToRender

data class ToolExecutionMessage(
  val toolRequest: LlmToolExecutionRequest, //
  val toolResult: LlmToolExecutionResultMessage? = null //
) : MessageToRender()

data class LlmMessageRef(
  val ref: LlmMessage
) : MessageToRender()


fun messagesToRender(messages: List<LlmMessage>): List<MessageToRender> {
  // Build a lookup for result messages by their unique id.
  val resultMap = messages.filterIsInstance<LlmToolExecutionResultMessage>().associateBy { it.id }

  return messages.flatMap { msg ->
    when (msg) {
      is LlmAiMessage -> {
        if (msg.toolExecutionRequests.isNullOrEmpty()) {
          listOf(LlmMessageRef(msg))
        } else {
          val aiMsgWithoutTools = msg.copy(toolExecutionRequests = null) //
            .let { //
              when {
                it.text.isNullOrEmpty() -> emptyList()
                else -> listOf(LlmMessageRef(it))
              }
            }
          aiMsgWithoutTools + msg.toolExecutionRequests.map { req ->
            ToolExecutionMessage(
              toolRequest = req, toolResult = resultMap[req.id]
            )
          }
        }
      }
      // Skip standalone result messages as they’re paired already.
      is LlmToolExecutionResultMessage -> emptyList()
      else -> listOf(LlmMessageRef(msg))
    }
  }
}


@Composable
fun ChatMessages(
  messages: List<LlmMessage>, incomingMessage: IncomingLlmMessage?, showNoRenderMessages: Boolean = false
) {
  val listState = rememberLazyListState()

  val allMessages = messages + listOfNotNull(incomingMessage)
  val messagesToRenderList = remember(allMessages) { messagesToRender(allMessages) }

  LaunchedEffect(allMessages.size) {
    if (allMessages.isNotEmpty()) {
      listState.scrollToItem(allMessages.size - 1)
    }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(messagesToRenderList) { message ->
      when (message) {
        is LlmMessageRef -> {
          when (val msg = message.ref) {
            is LlmUserMessage -> UserMessageItem(msg)
            is LlmSystemMessage -> {
              if (showNoRenderMessages) {
                SystemMessageItem(msg)
              }
            }

            is LlmAiMessage -> AiMessageItem(msg)
            is IncomingLlmMessage -> IncomingMessageItem(msg)
            is LlmErrorMessage -> ErrorMessageItem(msg)
            else -> {}
          }
        }

        is ToolExecutionMessage -> {
          ToolExecutionCombinedItem(
            toolRequest = message.toolRequest, //
            toolResult = message.toolResult, //
          )
        }
      }
    }
  }
}

@Composable
fun UserMessageItem(message: LlmUserMessage) {
  val prefix = if (message.name.isNullOrEmpty()) "[User]" else "[User: ${message.name}]"
  val combinedText = message.contents.joinToString("\n") { content ->
    if (content is LlmTextContent) content.text else ""
  }
  val unprompted = Prompts.unpromptUserMessage(combinedText)
  MessageBubble(
    title = prefix,
    text = unprompted,
    bubbleColor = bubbleColorForMessageType("user"),
    alignEnd = true,
    useMarkdown = true
  )
}

@Composable
fun SystemMessageItem(message: LlmSystemMessage) {
  MessageBubble(
    title = "[System]",
    text = message.text,
    bubbleColor = bubbleColorForMessageType("system"),
    alignEnd = false,
    useMarkdown = true
  )
}

@Composable
fun AiMessageItem(message: LlmAiMessage) {
  val textPart = message.text?.takeIf { it.isNotBlank() }
  val toolRequestsPart = if (!message.toolExecutionRequests.isNullOrEmpty()) {
    buildString {
      append("Tool Execution Requests:\n")
      message.toolExecutionRequests.forEach { req ->
        append(" - ${req.name} (id=${req.id})\n")
      }
    }.trim()
  } else null
  val combinedText = listOfNotNull(textPart, toolRequestsPart).joinToString("\n\n")
  val unprompted = Prompts.unpromptAiMessage(combinedText)
  if (unprompted.isEmpty()) {
    return
  }

  MessageBubble(
    title = "[AI]",
    text = unprompted,
    bubbleColor = bubbleColorForMessageType("ai"),
    alignEnd = false,
    useMarkdown = true
  )
}

@Composable
fun IncomingMessageItem(message: IncomingLlmMessage) {
  MessageBubble(
    title = "[Incoming]", text = message.text, bubbleColor = bubbleColorForMessageType("incoming"), alignEnd = false,
    // TODO: figure out a way to make markdown render quickly and scroll nicely
    //   Right now, it's pretty slow, so use plain text for incoming messages
    useMarkdown = false
  )
}

@Composable
fun ErrorMessageItem(message: LlmErrorMessage) {
  MessageBubble(
    title = "[Error]",
    text = message.text,
    bubbleColor = bubbleColorForMessageType("error"),
    alignEnd = false,
    useMarkdown = false
  )
}

@Composable
fun ToolExecutionCombinedItem(
  toolRequest: LlmToolExecutionRequest, toolResult: LlmToolExecutionResultMessage? = null
) {
  var expanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth()
      .clickable { expanded = !expanded }
      .background(bubbleColorForMessageType("tool"), shape = RoundedCornerShape(8.dp))
      .padding(12.dp), //
    horizontalArrangement = Arrangement.SpaceBetween) {
    val toolIcon = toolRequest.toolIcon
    Row {
      if (toolIcon != null) {
        Icon(key = toolIcon, contentDescription = "Tool Icon", modifier = Modifier.size(16.dp))
      }
      Spacer(Modifier.width(4.dp))
      Text(toolRequest.name, style = Typography.h4TextStyle())
    }
    if (toolResult == null) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp))
    } else {
      Icon(key = TestPassed, contentDescription = "Done", modifier = Modifier.size(16.dp))
    }
  }

  AnimatedVisibility(visible = expanded) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
      Text("Request arguments: ${toolRequest.arguments}", style = Typography.labelTextStyle())
      if (toolResult != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Result: ${toolResult.text}", style = Typography.labelTextStyle())
      }
    }
  }

  if (toolRequest.state.value == ToolState.WAITING_FOR_APPROVAL) {
    DefaultApprovalUI(service(), toolRequest.id)
  }
}

@Composable
fun MessageBubble(
  title: String, text: String, bubbleColor: Color, alignEnd: Boolean, useMarkdown: Boolean
) {
  Row(
    modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
  ) {
    Column(
      modifier = Modifier.widthIn(max = 640.dp).clip(RoundedCornerShape(8.dp)).background(bubbleColor).padding(12.dp)
    ) {
      Text(text = title, style = Typography.h4TextStyle())
      Spacer(Modifier.height(4.dp))
      SelectionContainer {
        if (useMarkdown) {
          MarkdownText(content = text)
        } else {
          Text(text)
        }
      }
    }
  }
}

@Composable
fun bubbleColorForMessageType(type: String): Color {
  val baseColor = JewelTheme.defaultTextStyle.background
  val fallback = if (JewelTheme.isDark) Color(0xFF3C3F41) else Color(0xFFF7F7F7)
  val actualBase = if (baseColor.isUnspecified) fallback else baseColor

  return when (type.lowercase()) {
    "user" -> lightenOrDarken(actualBase, factor = if (JewelTheme.isDark) 1.1f else 0.9f)
    "system" -> lightenOrDarken(actualBase, factor = 1.0f)
    "ai" -> lightenOrDarken(actualBase, factor = if (JewelTheme.isDark) 0.85f else 1.05f)
    "tool" -> lightenOrDarken(actualBase, factor = if (JewelTheme.isDark) 0.75f else 1.1f)
    "incoming" -> actualBase
    "error" -> lightenOrDarken(Color.Red, factor = 1.2f)
    else -> actualBase
  }
}

@Composable
fun DefaultApprovalUI(toolStateService: ToolStateService, toolReqId: String) {
  Row(
    modifier = Modifier.fillMaxWidth(), //
    horizontalArrangement = Arrangement.End
  ) {
    OutlinedButton(onClick = {
      toolStateService.approve(toolReqId)
    }) {
      Text("Approve")
    }
    Spacer(modifier = Modifier.width(8.dp))
    OutlinedButton(onClick = {
      toolStateService.reject(toolReqId)
    }) {
      Text("Reject")
    }
  }
}


fun lightenOrDarken(color: Color, factor: Float): Color {
  val r = color.red
  val g = color.green
  val b = color.blue
  val alpha = color.alpha

  val newR = clamp01(r * factor)
  val newG = clamp01(g * factor)
  val newB = clamp01(b * factor)

  return Color(newR, newG, newB, alpha)
}

private fun clamp01(value: Float): Float = max(0f, min(value, 1f))
