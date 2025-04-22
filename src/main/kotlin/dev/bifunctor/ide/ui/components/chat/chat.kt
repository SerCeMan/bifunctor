package dev.bifunctor.ide.ui.components.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import dev.bifunctor.ide.agent.*
import dev.bifunctor.ide.ui.BifIcons
import dev.bifunctor.ide.ui.settings.BifSettings
import dev.bifunctor.ide.ui.settings.BifSettingsConfigurable
import dev.bifunctor.ide.ui.settings.BifSettingsImpl
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.Icon

class BifChatWindow : ToolWindowFactory {
  override val icon: Icon = BifIcons.toolWindowIcon

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val llm = project.getService(LlmService::class.java)
    val settings = BifSettingsImpl.instance
    val selectionService = project.getService(SelectionService::class.java)
    val openSettings: () -> Unit = {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, BifSettingsConfigurable::class.java)
    }
    toolWindow.addComposeTab {
      ChatContentContainer(openSettings, llm, settings, selectionService)
    }
  }
}

@Composable
fun ChatContentContainer(
  openSettings: () -> Unit,
  llm: LlmService,
  settings: BifSettings,
  selectionService: SelectionService
) {
  val userMessageState = rememberTextFieldState()
  val lastSelectionCtx = selectionService.lastSelectionCtx
  var conversation: Conversation? by remember { mutableStateOf(null) }

  LaunchedEffect(lastSelectionCtx.value) {
    conversation?.addContext(lastSelectionCtx.value)
  }

  var agenticLoop: AgenticLoop? by remember { mutableStateOf(null) }

  val models = llm.availableModels.value
  val isInitialized = llm.initializedState
  var selectedModel by remember {
    val defaultSelectedModel = llm.chooseModel()
    mutableStateOf(defaultSelectedModel)
  }

  LaunchedEffect(selectedModel) {
    selectedModel?.let { model ->
      settings.lastUsedModelId = model.id
    }
  }

  val messages = conversation?.messages ?: emptyList()
  val incompleteMessage = conversation?.incompleteMessage?.value

  fun sendMessage() {
    val userMessage = userMessageState.text.toString()
    if (userMessage.isNotBlank()) {
      val model = selectedModel //
        ?: throw Exception("No model selected")
      val conv = conversation ?: llm.startConversation(model).also { conversation = it }
      agenticLoop = conv.runAgenticLoop(userMessage, lastSelectionCtx.value)
    }
  }

  val isResponding = conversation?.isResponding?.value ?: false

  val messagesComponent = @Composable {
    ChatMessages(messages = messages, incomingMessage = incompleteMessage)
  }
  val modelSelectorComponent = @Composable {
    ModelSelector(
      models = models,
      selectedModel = selectedModel,
      onModelSelected = { newModel -> selectedModel = newModel },
      enabled = true
    )
  }
  val chatContextComponent = @Composable {
    val chatCtx = conversation?.convCtx?.value ?: lastSelectionCtx.value
    if (chatCtx != null) {
      ChatContext(chatCtx = chatCtx)
    }
  }
  val actionButtonComponent = @Composable {
    val isSendButtonEnabled = !isResponding //
      && selectedModel != null //
      && userMessageState.text.isNotEmpty()

    val onStop = {
      agenticLoop?.interrupt()
      agenticLoop = null
    }

    val actionButton: ChatActionButton = when (isResponding) {
      true -> ChatActionButton.STOP
      false -> ChatActionButton.SEND
    }

    when (actionButton) {
      ChatActionButton.SEND -> OutlinedButton(
        enabled = isSendButtonEnabled, //
        onClick = {
          val text = userMessageState.text.toString()
          if (text.isNotBlank()) {
            sendMessage()
            userMessageState.setTextAndPlaceCursorAtEnd("")
          }
        }) {
        Text("Send")
      }

      ChatActionButton.STOP -> OutlinedButton(onClick = onStop) {
        Text("Stop")
      }
    }
  }

  val messageAreaComponent = @Composable {
    TextArea(
      state = userMessageState,
      enabled = models.isNotEmpty(),
      // a text field that's about 20% of the screen looks
      // most aesthetically pleasing to my eyes
      modifier = Modifier.fillMaxWidth() //
        .fillMaxHeight(0.20f) //
        .onPreviewKeyEvent { keyEvent ->
          if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
            if (keyEvent.isShiftPressed) {
              val withNewLine = "${userMessageState.text}\n"
              userMessageState.setTextAndPlaceCursorAtEnd(withNewLine)
              true
            } else if (!isResponding) {
              val text = userMessageState.text
              if (text.isNotEmpty()) {
                sendMessage()
                userMessageState.setTextAndPlaceCursorAtEnd("")
              }
              true
            } else {
              false
            }
          } else {
            false
          }
        },
    )
  }

  val newChatButtonComponent = @Composable {
    val onNewChat = {
      agenticLoop?.interrupt()
      agenticLoop = null
      conversation = null
    }
    IconButton(onClick = onNewChat) {
      Icon( //
        key = AllIconsKeys.Actions.AddMulticaret, //
        contentDescription = "New Chat", //
        modifier = Modifier.size(16.dp)
      )
    }
  }

  var approvalMode by remember { mutableStateOf(ApprovalMode.ALWAYS_ASK) }

  LaunchedEffect(conversation, approvalMode) {
    val toolStateService = service<ToolStateService>()
    conversation?.let { conv ->
      toolStateService.setApprovalMode(conv.id, approvalMode)
    }
  }

  val approveLevelComponent = @Composable {
    fun textForMode(mode: ApprovalMode): String = when (mode) {
      ApprovalMode.ALWAYS_ASK -> "Always Ask For Approval"
      ApprovalMode.ASK_FOR_SHELL_COMMANDS -> "Ask for Shell Commands"
      ApprovalMode.AUTO_APPROVE -> "Auto-Approve All Actions"
    }

    Box {
      Dropdown(
        enabled = true,
        menuContent = {
          val approvalModes = ApprovalMode.entries
          approvalModes.forEach { mode ->
            selectableItem(
              selected = (mode == approvalMode),
              onClick = {
                approvalMode = mode
              }
            ) {
              Text(textForMode(mode))
            }
          }
        }
      ) {
        Text(textForMode(approvalMode))
      }
    }
  }

  val noModelsComponent = @Composable {
    val noModelsText = remember {
      buildAnnotatedString {
        append("No AI models have been configured.\n")
        pushLink(
          LinkAnnotation.Clickable(
            tag = "settings",
            linkInteractionListener = LinkInteractionListener { link ->
              openSettings()
            }
          )
        )
        append("Open ")
        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        append("BiFunctor Settings")
        pop() // style
        pop() // link
        append(" and enter at least one API key to start.")
      }
    }

    Text(
      text = noModelsText
    )
  }

  ChatContentLayout(
    noModelsComponent = noModelsComponent,
    chatContextComponent = chatContextComponent,
    modelSelectorComponent = modelSelectorComponent,
    messagesComponent = messagesComponent,
    actionButtonComponent = actionButtonComponent,
    messageAreaComponent = messageAreaComponent,
    newChatButtonComponent = newChatButtonComponent,
    approveLevelComponent = approveLevelComponent,
    isResponding = isResponding,
    modelsAvailable = models.isNotEmpty(),
    isLoading = !isInitialized.value
  )
}

enum class ChatActionButton {
  SEND,
  STOP
}

@Composable
fun ChatContentLayout(
  noModelsComponent: @Composable () -> Unit,
  chatContextComponent: @Composable () -> Unit,
  modelSelectorComponent: @Composable () -> Unit,
  messagesComponent: @Composable () -> Unit,
  actionButtonComponent: @Composable () -> Unit,
  messageAreaComponent: @Composable () -> Unit,
  newChatButtonComponent: @Composable () -> Unit,
  approveLevelComponent: @Composable () -> Unit,
  isResponding: Boolean,
  isLoading: Boolean = false,
  modelsAvailable: Boolean = false
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp), //
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    if (isLoading) {
      Text("Loading...")
      return@Column
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("Chat")
        if (isResponding) {
          CircularProgressIndicator()
        }
      }
      newChatButtonComponent()
    }
    Box(
      modifier = Modifier.weight(1f).fillMaxWidth().fillMaxHeight()
    ) {
      if (!modelsAvailable) {
        noModelsComponent()
      } else {
        messagesComponent()
      }
    }
    messageAreaComponent()
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        modelSelectorComponent()
        chatContextComponent()
        approveLevelComponent()
      }
      actionButtonComponent()
    }
  }
}
