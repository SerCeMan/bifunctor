package dev.bifunctor.ide.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import com.intellij.mock.MockApplication
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.*
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.RadioButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

private enum class IntUiThemes {
  System,
  Light,
  Dark;

  fun isDark() = (if (this == System) fromSystemTheme(currentSystemTheme) else this) == Dark

  companion object {
    fun fromSystemTheme(systemTheme: SystemTheme) = if (systemTheme == SystemTheme.LIGHT) Light else Dark
  }
}

private class StoryViewModel(initialTheme: IntUiThemes = IntUiThemes.System) {
  var theme: MutableState<IntUiThemes> = mutableStateOf(initialTheme)
}

@Composable
private fun getThemeDefinition(theme: IntUiThemes): ThemeDefinition {
  @Composable
  fun lightTheme() = JewelTheme.lightThemeDefinition(
    defaultTextStyle = JewelTheme.createDefaultTextStyle(), //
    editorTextStyle = JewelTheme.createEditorTextStyle() //
  )

  @Composable
  fun darkTheme() = JewelTheme.darkThemeDefinition(
    defaultTextStyle = JewelTheme.createDefaultTextStyle(), //
    editorTextStyle = JewelTheme.createEditorTextStyle() //
  )

  return when (theme) {
    IntUiThemes.Light -> lightTheme()
    IntUiThemes.Dark -> darkTheme()
    IntUiThemes.System -> when (currentSystemTheme) {
      SystemTheme.DARK -> darkTheme()
      else -> lightTheme()
    }
  }
}


@Composable
private fun MainScreen(viewModel: StoryViewModel, component: @Composable () -> Unit = {}, onExit: () -> Unit) {
  val currentTheme by viewModel.theme
  val themeDefinition = getThemeDefinition(currentTheme)
  val windowStyling = if (currentTheme.isDark()) {
    ComponentStyling.dark().decoratedWindow(
      titleBarStyle = TitleBarStyle.dark()
    )
  } else {
    ComponentStyling.default().decoratedWindow(
      titleBarStyle = TitleBarStyle.light()
    )
  }

  IntUiTheme(
    theme = themeDefinition, //
    styling = windowStyling, //
    swingCompatMode = true //
  ) {
    DecoratedWindow(
      onCloseRequest = { onExit() },
      title = "BiFunctor Storybook",
    ) {
      // Fill the entire background, so we get a consistent dark or light behind everything
      Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        Column(Modifier.fillMaxSize()) {
          ThemeSwitcherRow(
            currentTheme = currentTheme, onThemeSelect = { newTheme -> viewModel.theme.value = newTheme })
          Box(
            Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center
          ) {
            component()
          }
        }
      }
    }
  }
}

@Composable
private fun ThemeSwitcherRow(currentTheme: IntUiThemes, onThemeSelect: (IntUiThemes) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth() //
      .background(JewelTheme.globalColors.panelBackground) //
      .padding(horizontal = 16.dp, vertical = 8.dp), //
    verticalAlignment = Alignment.CenterVertically
  ) {
    IntUiThemes.entries.forEach { themeOption ->
      Row(
        Modifier.clickable { onThemeSelect(themeOption) } //
          .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        RadioButton(
          selected = (currentTheme == themeOption),
          onClick = { onThemeSelect(themeOption) },
        )
        Spacer(Modifier.width(4.dp))
        Text(themeOption.name)
      }
    }
  }
}

fun story(component: @Composable () -> Unit) {
  // init the mock application as various components might request them
  MockApplication.setUp({})
  val viewModel = StoryViewModel()
  application {
    MainScreen(viewModel, component, onExit = { exitApplication() })
  }
}

@Preview
@Composable
fun ExampleComponent() {
  Text("Hello, Storybook!")
}

fun main() = story {
  ExampleComponent()
}

