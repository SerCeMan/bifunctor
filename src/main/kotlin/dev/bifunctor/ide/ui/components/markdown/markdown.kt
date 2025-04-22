package dev.bifunctor.ide.ui.components.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme

private val TextColors.code: Color
  get() = this.normal

@Composable
fun markdownTypography(): MarkdownTypography = DefaultMarkdownTypography(
  h1 = JewelTheme.defaultTextStyle.copy(fontSize = 24.sp),
  h2 = JewelTheme.defaultTextStyle.copy(fontSize = 20.sp),
  h3 = JewelTheme.defaultTextStyle.copy(fontSize = 18.sp),
  h4 = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp),
  h5 = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
  h6 = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
  text = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
  paragraph = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
  code = JewelTheme.defaultTextStyle.copy(color = JewelTheme.globalColors.text.code),
  inlineCode = JewelTheme.defaultTextStyle.copy(color = JewelTheme.globalColors.text.code),
  quote = JewelTheme.defaultTextStyle.copy(fontStyle = FontStyle.Italic),
  ordered = JewelTheme.defaultTextStyle,
  bullet = JewelTheme.defaultTextStyle,
  list = JewelTheme.defaultTextStyle,
  link = JewelTheme.defaultTextStyle,
  textLink = TextLinkStyles(),
  table = JewelTheme.defaultTextStyle,
)

@Composable
private fun markdownColors(): DefaultMarkdownColors = DefaultMarkdownColors(
  text = JewelTheme.globalColors.text.normal,
  codeText = JewelTheme.globalColors.text.code,
  inlineCodeText = JewelTheme.globalColors.text.normal,
  linkText = JewelTheme.globalColors.text.normal,
  codeBackground = JewelTheme.globalColors.panelBackground,
  inlineCodeBackground = JewelTheme.globalColors.panelBackground,
  dividerColor = JewelTheme.globalColors.borders.normal,
  tableText = JewelTheme.globalColors.text.normal,
  tableBackground = JewelTheme.globalColors.panelBackground,
)

@Composable
fun MarkdownText(content: String) {
  Markdown(
    content = content,
    colors = markdownColors(),
    typography = markdownTypography(),
  )
}
