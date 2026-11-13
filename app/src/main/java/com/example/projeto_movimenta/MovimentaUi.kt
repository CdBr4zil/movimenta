package com.example.projeto_movimenta

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.projeto_movimenta.ui.theme.Layout

@Composable
internal fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription = null, modifier = modifier.size(22.dp), tint = tint)
}

@Composable
internal fun PageBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.background(Brush.verticalGradient(listOf(lerp(colors.background, colors.primaryContainer, .22f), colors.background)))) {
        drawCircle(
            color = colors.primaryContainer.copy(alpha = .68f),
            radius = 185.dp.toPx(),
            center = Offset(size.width - 8.dp.toPx(), -112.dp.toPx()),
        )
    }
}

@Composable
internal fun SectionCard(
    title: String? = null,
    @DrawableRes icon: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, theme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(Layout.gutter), verticalArrangement = Arrangement.spacedBy(Layout.gap)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Layout.compactGap)) {
                    icon?.let { AppIcon(it, tint = theme.primary) }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
            }
            content()
        }
    }
}

@Composable
internal fun ActionButton(
    text: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick, modifier.heightIn(min = Layout.target), enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AppIcon(icon)
        Spacer(Modifier.width(Layout.compactGap))
        Text(text)
    }
}

@Composable
internal fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick, modifier.heightIn(min = Layout.target), enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) {
        AppIcon(icon, tint = if (enabled) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        Spacer(Modifier.width(Layout.compactGap))
        Text(text, color = if (enabled) MaterialTheme.colorScheme.onSurface else LocalContentColor.current)
    }
}

@Composable
internal fun StartupContent(failed: Boolean, busy: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Layout.gutter).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Layout.gap),
    ) {
        if (failed) {
            AppIcon(R.drawable.ic_mv_alert, tint = MaterialTheme.colorScheme.error)
            Text("Não foi possível carregar o estoque.", textAlign = TextAlign.Center)
            Button(onClick = onRetry, enabled = !busy) { Text("Tentar novamente") }
        } else {
            Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
                Image(painterResource(R.drawable.movimenta_logo), contentDescription = null, modifier = Modifier.size(128.dp).padding(10.dp))
            }
            Text("Carregando estoque…", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
