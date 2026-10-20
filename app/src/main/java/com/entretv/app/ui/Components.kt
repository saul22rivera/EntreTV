package com.entretv.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark

/**
 * Prueba las imágenes en orden y pasa a la siguiente si una falla; sin ninguna muestra el
 * marcador de la marca. Evita las tarjetas negras cuando el panel no manda imagen.
 */
@Composable
fun FallbackImage(
    urls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var index by remember(urls) { mutableIntStateOf(0) }
    Box(modifier) {
        if (index >= urls.size) {
            BrandPlaceholder(Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = urls[index],
                contentDescription = contentDescription,
                contentScale = contentScale,
                onError = { index++ },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun BrandPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF1A2A4A), EntreBrand.Ink))),
        contentAlignment = Alignment.Center
    ) {
        EntreWordmark(fontSize = 20.sp, modifier = Modifier.graphicsLayer { alpha = 0.55f })
    }
}

/** Botón de la marca: blanco sólido el principal, translúcido el resto; foco visible en TV. */
@Composable
fun EntreButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    compact: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "entreButton")
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        primary -> Color.White
        focused -> Color.White.copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val content = if (primary) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .then(if (focused) Modifier.border(2.dp, EntreBrand.Accent, shape) else Modifier)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = text.ifEmpty { null }, tint = content, modifier = Modifier.size(22.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
