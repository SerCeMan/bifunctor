package dev.bifunctor.ide.ui.components.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bifunctor.ide.agent.QueryContext
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.Stroke.Alignment
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatContext(chatCtx: QueryContext) {
  if (chatCtx.recentFiles.isNotEmpty() || chatCtx.selection?.isNotEmpty() == true) {
    TooltipArea(tooltip = {
      Column(
        modifier = Modifier.background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp)).padding(8.dp)
      ) {
        Text("Context Files:", color = JewelTheme.globalColors.text.info)
        chatCtx.recentFiles.forEach { file ->
          Text(file.name, color = JewelTheme.globalColors.text.info)
        }
        chatCtx.selection?.let { selection ->
          Text("Selection:", color = JewelTheme.globalColors.text.info)
          Text(selection, color = JewelTheme.globalColors.text.info)
        }
      }
    }, content = {
      Box(
        modifier = Modifier //
          .background(
            JewelTheme.globalColors.panelBackground
          ) //
          .border(
            shape = RoundedCornerShape(2.dp),
            stroke = Stroke(
              width = JewelTheme.globalMetrics.outlineWidth,
              color = JewelTheme.globalColors.borders.normal,
              alignment = Alignment.Outside,
            ),
          ) //
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text("Context", color = JewelTheme.globalColors.text.normal)
      }
    })
  }
}
