package com.app.nosatmosphereeffect.ui.model

import androidx.annotation.DrawableRes
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.AtmosphereClockPolicy
import com.app.nosatmosphereeffect.helper.ClockPalette
import com.app.nosatmosphereeffect.helper.ClockStyle

data class ThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val previewRes: Int,
    val effectId: String = "ORIGINAL",
    val clockStyle: ClockStyle = ClockStyle.GLASS,
    val customFontId: String? = null,
    val depthEnabled: Boolean = true,
    val clockColor: Int = ClockPalette.AUTO,
    val clockOpacity: Float = 1.0f,
    val clockHeightFraction: Float = AtmosphereClockPolicy.DEFAULT_HEIGHT,
    val isGlassEnabled: Boolean = false
)

object ThemePresetCatalog {
    val presets = listOf(
        ThemePreset(
            id = "preset_apple_glass",
            title = "Apple Glass Minimal",
            subtitle = "Effet de verre rehaussé avec horloge translucide",
            previewRes = R.drawable.preset_apple_glass,
            effectId = "GLASS",
            clockStyle = ClockStyle.GLASS,
            depthEnabled = true,
            isGlassEnabled = true
        ),
        ThemePreset(
            id = "preset_cyber_neon",
            title = "Cyberpunk Neon",
            subtitle = "Tracés lumineux et horloge rétro futuriste",
            previewRes = R.drawable.preset_cyber_neon,
            effectId = "NEON",
            clockStyle = ClockStyle.TRANSLUCENT,
            depthEnabled = false,
            clockColor = 0xFF00F5D4.toInt(),
            clockOpacity = 0.95f
        ),
        ThemePreset(
            id = "preset_depth_portrait",
            title = "Studio Portrait",
            subtitle = "Effet de profondeur iPhone avec sujet détouré",
            previewRes = R.drawable.preset_depth_portrait,
            effectId = "ORIGINAL",
            clockStyle = ClockStyle.GLASS,
            depthEnabled = true,
            clockColor = 0xFFFFF7ED.toInt()
        ),
        ThemePreset(
            id = "preset_frosted_dusk",
            title = "Frosted Twilight",
            subtitle = "Atmosphère vaporeuse et texture givrée douce",
            previewRes = R.drawable.preset_frosted_dusk,
            effectId = "FROSTED",
            clockStyle = ClockStyle.TRANSLUCENT_STACKED,
            depthEnabled = false,
            clockOpacity = 0.9f
        ),
        ThemePreset(
            id = "preset_editorial_serif",
            title = "Editorial Serif",
            subtitle = "Élégance classique avec horloge sérif superposée",
            previewRes = R.drawable.preset_editorial_serif,
            effectId = "ORIGINAL",
            clockStyle = ClockStyle.GLASS_STACKED,
            depthEnabled = true,
            clockColor = 0xFFF59E0B.toInt()
        )
    )

    fun find(id: String?): ThemePreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
