package dev.bifunctor.ide.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import dev.bifunctor.ide.agent.LlmProvider
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

val LOG: Logger = Logger.getInstance("dev.bifunctor.ide.ui")

class BifSettingsConfigurable : Configurable {
  private val llmKeys = service<LlmKeyService>()
  private var openAiKey: String? = null
  private var anthropicKey: String? = null
  private var xAiKey: String? = null
  private var completionEnabled: Boolean? = null

  override fun getDisplayName(): String = "BiFunctor Settings"

  override fun createComponent() = JewelComposePanel {
    val bifSettings = BifSettingsImpl.instance
    BifSettingsPanel(
      isInitialized = llmKeys.initializedState.value,

      initialOpenAiKey = llmKeys.openAiKey.keyValue.orEmpty(),
      initialAnthropicKey = llmKeys.anthropicKey.keyValue.orEmpty(),
      initialXAiKey = llmKeys.xAiKey.keyValue.orEmpty(),

      onOpenAiKeyChange = { openAiKey = it },
      onAnthropicKeyChange = { anthropicKey = it },
      onXAiKeyChange = { xAiKey = it },

      initialCompletionEnabled = bifSettings.completionEnabled,
      onCompletionEnabledChange = { completionEnabled = it }
    )
  }

  override fun isModified(): Boolean {
    val bifSettings = BifSettingsImpl.instance
    return llmKeys.openAiKey.keyValue != openAiKey ||
      llmKeys.anthropicKey.keyValue != anthropicKey ||
      llmKeys.xAiKey.keyValue != xAiKey ||  // check for changes
      (completionEnabled != null && completionEnabled != bifSettings.completionEnabled)
  }

  override fun apply() {
    val llmSettings = service<LlmKeyService>()
    // Save the new keys
    llmSettings.openAiKey.save(openAiKey)
    llmSettings.anthropicKey.save(anthropicKey)
    llmSettings.xAiKey.save(xAiKey)

    if (completionEnabled != null) {
      BifSettingsImpl.instance.completionEnabled = completionEnabled!!
    }
  }
}

@Composable
fun BifSettingsPanel(
  isInitialized: Boolean,
  initialOpenAiKey: String,
  initialAnthropicKey: String,
  onOpenAiKeyChange: (String) -> Unit,
  onAnthropicKeyChange: (String) -> Unit,
  initialXAiKey: String,
  onXAiKeyChange: (String) -> Unit,
  initialCompletionEnabled: Boolean,
  onCompletionEnabledChange: (Boolean) -> Unit
) {
  if (!isInitialized) {
    Text("Loading...")
    return
  }

  val openAiKeyState = rememberTextFieldState(initialOpenAiKey)
  LaunchedEffect(openAiKeyState.text) {
    onOpenAiKeyChange(openAiKeyState.text.toString())
  }

  val anthropicKeyState = rememberTextFieldState(initialAnthropicKey)
  LaunchedEffect(anthropicKeyState.text) {
    onAnthropicKeyChange(anthropicKeyState.text.toString())
  }

  val xAiKeyState = rememberTextFieldState(initialXAiKey)
  LaunchedEffect(xAiKeyState.text) {
    onXAiKeyChange(xAiKeyState.text.toString())
  }

  val completionEnabledState = remember { mutableStateOf(initialCompletionEnabled) }
  LaunchedEffect(completionEnabledState.value) {
    onCompletionEnabledChange(completionEnabledState.value)
  }

  Column {
    Text("BiFunctor Settings")
    Spacer(Modifier.height(8.dp))

    ModelApiKey("OpenAI Key", openAiKeyState)
    Spacer(Modifier.height(8.dp))

    ModelApiKey("Anthropic Key", anthropicKeyState)
    Spacer(Modifier.height(8.dp))

    ModelApiKey("xAI Key (for Grok)", xAiKeyState)
    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = completionEnabledState.value,
        onCheckedChange = { completionEnabledState.value = it }
      )
      Spacer(Modifier.width(8.dp))
      Text("Enable Inline Completion")
    }
  }
}

@Composable
fun ModelApiKey(text: String, apiKeyField: TextFieldState) {
  Text(text)
  Spacer(modifier = Modifier.height(4.dp))
  TextField(
    apiKeyField
  )
}

fun isLifecycleMainThread(): Boolean = try {
  // The method isn't available compile time, so call reflectively.
  val clazz = Class.forName("androidx.lifecycle.MainDispatcherChecker")
  val instance = clazz
    .getDeclaredField("INSTANCE")
    .apply { isAccessible = true }
    .get(null)
  val method = clazz
    .getDeclaredMethod("isMainDispatcherThread")
    .apply { isAccessible = true }
  method.invoke(instance) as Boolean
} catch (e: Throwable) {
  LOG.error("error initializing lifecycle main thread", e)
  true
}

@Suppress("UnstableApiUsage")
class BifAppInitializedListener : ApplicationInitializedListener {
  override suspend fun execute() {
    // Opening settings without first opening the chat window causes the IDE to freeze due to a race.
    // As a workaround, ensure that the method causing a race is invoked during the initialization phase.
    isLifecycleMainThread()
    ApplicationManager.getApplication().executeOnPooledThread {
      val llmKeyService = service<LlmKeyService>()
      llmKeyService.init()
    }
  }
}

class LlmSecretKey(val name: String, val modelProvider: LlmProvider) {
  private val keyRef = AtomicReference<String?>()
  val keyState = mutableStateOf<String?>(null)

  var keyValue: String?
    get() = keyRef.get()
    private set(value) {
      keyRef.set(value)
      ApplicationManager.getApplication().invokeLater {
        keyState.value = value
      }
    }

  fun save(value: String?) {
    keyValue = value
    saveSecretKey(name, value)
  }

  fun init() {
    val value = loadSecretKey(name)
    keyValue = value
  }

  private fun saveSecretKey(name: String, key: String?) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val attributes = CredentialAttributes(generateServiceName("BiFunctorSettings", name))
      PasswordSafe.instance.set(attributes, Credentials("user", key))
    }
  }

  private fun loadSecretKey(name: String): String? {
    val attributes = CredentialAttributes(generateServiceName("BiFunctorSettings", name))
    return PasswordSafe.instance.get(attributes)?.getPasswordAsString()
  }
}

@Service(Service.Level.APP)
class LlmKeyService {
  val initializedState = mutableStateOf(false)
  val initialized = AtomicBoolean(false)

  val openAiKey = LlmSecretKey("openAiKey", LlmProvider.OPEN_AI)
  val anthropicKey = LlmSecretKey("anthropicKey", LlmProvider.ANTHROPIC)
  val xAiKey = LlmSecretKey("xAiKey", LlmProvider.XAI)

  val allKeys = listOf(openAiKey, anthropicKey, xAiKey)

  fun init() {
    ensureLocalesAreLoaded()
    openAiKey.init()
    anthropicKey.init()
    xAiKey.init()
    initialized.set(true)
    ApplicationManager.getApplication().invokeLater {
      initializedState.value = true
    }
  }

  fun getKeyFor(modelProvider: LlmProvider): LlmSecretKey? {
    return allKeys.find { it.modelProvider == modelProvider }
  }

  private fun ensureLocalesAreLoaded() {
    // a workaround for
    // com.jetbrains.rdserver.unattendedHost.portForwarding.ui.data.ForwardedPortUiData <clinit> requests
    // com.intellij.l10n.LocalizationStateService instance. Class initialization must not depend on services.
    // Consider using instance of the service on-demand instead.
    @Suppress("UnstableApiUsage") LocalizationStateService.getInstance()
  }
}

interface BifSettings {
  var lastUsedModelId: String?
  var completionEnabled: Boolean
}

@Service(Service.Level.APP)
@State(
  name = "BiFunctorSettings", storages = [Storage("BiFunctorSettings.xml")]
)
class BifSettingsImpl : PersistentStateComponent<BifSettingsImpl.State>, BifSettings {
  companion object {
    val instance: BifSettingsImpl
      get() = service()
  }

  data class State(var lastUsedModelId: String? = null, var completionEnabled: Boolean = false)

  private var state = State()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  override var lastUsedModelId: String?
    get() = state.lastUsedModelId
    set(value) {
      state.lastUsedModelId = value
    }

  override var completionEnabled: Boolean
    get() = state.completionEnabled
    set(value) {
      state.completionEnabled = value
    }
}
