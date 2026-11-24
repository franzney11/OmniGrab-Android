package com.franzney.omnigrab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF09090B)
val GlassSurface = Color(0xFF141418)
val GlassBorder = Color(0xFF27272A)
val RiftIndigo = Color(0xFF6366F1)
val RiftCyan = Color(0xFF22D3EE)
val RiftViolet = Color(0xFFA855F7)
val EmeraldGreen = Color(0xFF10B981)
val ProAmber = Color(0xFFF59E0B)

private val DarkColors = darkColorScheme(
    primary = RiftIndigo,
    secondary = RiftCyan,
    tertiary = RiftViolet,
    background = ObsidianBg,
    surface = GlassSurface,
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5)
)

@Composable
fun OmniGrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
