package com.example.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =========================================================
// Reusable Liquid Glass Panel Container (Apple visionOS style)
// =========================================================
@Composable
fun GlassPanelContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 30.dp,
    content: @Composable RowScope.() -> Unit
) {
    val glassShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .graphicsLayer {
                // Background blur effect for Android 12+ (API 31+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect
                        .createBlurEffect(40f, 40f, Shader.TileMode.MIRROR)
                        .asComposeRenderEffect()
                }
            }
            .clip(glassShape)
            .background(Color.White.copy(alpha = 0.20f)) // Translucent glass fill
            .border(1.dp, Color.White.copy(alpha = 0.35f), glassShape) // Glass border
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}
