package io.github.hardbas.wearsync.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ShapeBar     = RoundedCornerShape(4.dp)   // progress bars / sleep stages
val ShapeChip    = RoundedCornerShape(12.dp)  // chips y elementos internos chicos
val ShapeCard    = RoundedCornerShape(22.dp)  // cards de contenido
val ShapePod     = RoundedCornerShape(26.dp)  // pod del hero / contenedor de chart
val ShapeStadium = RoundedCornerShape(32.dp)  // contenedores de pantalla
val ShapePill    = RoundedCornerShape(50)     // cápsulas y botones

// Flagship Glassmorphic Obsidian Tokens (Semi-translucent with specular directional lighting)
val GlassObsidianCardBg = Color(0xFF16191E).copy(alpha = 0.75f)
val GlassObsidianCardBgDeep = Color(0xFF101216).copy(alpha = 0.80f)

val GlassObsidianCardBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF22262E).copy(alpha = 0.78f),
        Color(0xFF121418).copy(alpha = 0.72f)
    )
)

val GlassObsidianCardSubtleBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF2A2F38).copy(alpha = 0.70f),
        Color(0xFF171A20).copy(alpha = 0.64f)
    )
)

val SpecularBorderCard = BorderStroke(
    width = 1.dp,
    brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.04f)
        )
    )
)

val SpecularBorderChip = BorderStroke(
    width = 1.dp,
    brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.03f)
        )
    )
)

fun specularBorderAccent(accentColor: Color, startAlpha: Float = 0.40f, endAlpha: Float = 0.08f): BorderStroke {
    return BorderStroke(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = startAlpha),
                accentColor.copy(alpha = endAlpha)
            )
        )
    )
}
