package com.app.nosatmosphereeffect.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.app.nosatmosphereeffect.helper.*
import com.app.nosatmosphereeffect.image.BitmapDecoder
import com.app.nosatmosphereeffect.image.BitmapStore
import com.app.nosatmosphereeffect.storage.FileTransactions
import com.app.nosatmosphereeffect.storage.SharedPreferencesTransactions
import com.app.nosatmosphereeffect.storage.WallpaperStorageCoordinator
import com.app.nosatmosphereeffect.ui.model.ThemePresetCatalog
import com.app.nosatmosphereeffect.ui.screens.LockscreenEditorScreen
import com.app.nosatmosphereeffect.ui.theme.AppearancePreferences
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class LockscreenEditorActivity : ComponentActivity() {

    private var wallpaperBitmap by androidx.compose.runtime.mutableStateOf<Bitmap?>(null)
    private var maskBitmap by androidx.compose.runtime.mutableStateOf<Bitmap?>(null)
    private var isSegmenting by androidx.compose.runtime.mutableStateOf(false)
    private var maskFailureReason by androidx.compose.runtime.mutableStateOf<String?>(null)

    private var activeEffectId by androidx.compose.runtime.mutableStateOf("ORIGINAL")
    private var isAtmosphereGlassEnabled by androidx.compose.runtime.mutableStateOf(false)
    private var clockStyle by androidx.compose.runtime.mutableStateOf(ClockStyle.GLASS)
    private var customFontId by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var clockSize by androidx.compose.runtime.mutableFloatStateOf(AtmosphereClockPolicy.DEFAULT_HEIGHT)
    private var clockOpacity by androidx.compose.runtime.mutableFloatStateOf(1.0f)
    private var clockColor by androidx.compose.runtime.mutableIntStateOf(ClockPalette.AUTO)
    private var depthEnabled by androidx.compose.runtime.mutableStateOf(true)

    private var customFonts by androidx.compose.runtime.mutableStateOf<List<CustomFontInfo>>(emptyList())
    private var isSaving by androidx.compose.runtime.mutableStateOf(false)

    private var coordinator: SubjectMaskCoordinator? = null

    private val pickFontFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val imported = CustomFontManager.importFontFromUri(this, it)
            if (imported != null) {
                Toast.makeText(this, "Police importée : ${imported.name}", Toast.LENGTH_SHORT).show()
                customFonts = CustomFontManager.getCustomFonts(this)
                customFontId = imported.id
            } else {
                Toast.makeText(this, "Impossible d'importer cette police.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        customFonts = CustomFontManager.getCustomFonts(this)
        readInitialParameters()
        loadImage()

        setContent {
            val expressive = AppearancePreferences.isExpressiveEnabled(this)
            val themeMode = AppearancePreferences.getThemeMode(this)
            val pitchBlack = AppearancePreferences.isPitchBlackEnabled(this)

            AtmoEngineTheme(
                expressive = expressive,
                themeMode = themeMode,
                pitchBlack = pitchBlack
            ) {
                LockscreenEditorScreen(
                    wallpaperBitmap = wallpaperBitmap,
                    maskBitmap = maskBitmap,
                    isSegmenting = isSegmenting,
                    maskFailureReason = maskFailureReason,
                    activeEffectId = activeEffectId,
                    onEffectChange = { activeEffectId = it },
                    isAtmosphereGlassEnabled = isAtmosphereGlassEnabled,
                    onAtmosphereGlassEnabledChange = { isAtmosphereGlassEnabled = it },
                    clockStyle = clockStyle,
                    onClockStyleChange = { clockStyle = it },
                    customFontId = customFontId,
                    onCustomFontIdChange = { customFontId = it },
                    customFonts = customFonts,
                    onImportFontClick = { pickFontFile.launch("*/*") },
                    clockSize = clockSize,
                    onClockSizeChange = { clockSize = it },
                    clockOpacity = clockOpacity,
                    onClockOpacityChange = { clockOpacity = it },
                    clockColor = clockColor,
                    onClockColorChange = { clockColor = it },
                    depthEnabled = depthEnabled,
                    onDepthEnabledChange = { depthEnabled = it },
                    isSaving = isSaving,
                    onCancel = { finish() },
                    onApply = { applyWallpaper() }
                )
            }
        }
    }

    private fun readInitialParameters() {
        val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
        if (presetId != null) {
            val preset = ThemePresetCatalog.find(presetId)
            activeEffectId = preset.effectId
            isAtmosphereGlassEnabled = preset.isGlassEnabled
            clockStyle = preset.clockStyle
            customFontId = preset.customFontId
            depthEnabled = preset.depthEnabled
            clockColor = preset.clockColor
            clockOpacity = preset.clockOpacity
            clockSize = preset.clockHeightFraction
        } else {
            activeEffectId = intent.getStringExtra(EXTRA_EFFECT_ID) ?: "ORIGINAL"
            isAtmosphereGlassEnabled = intent.getBooleanExtra(EXTRA_GLASS_ENABLED, false)
            val styleId = intent.getStringExtra(EXTRA_CLOCK_STYLE)
            if (styleId != null) clockStyle = ClockStyle.fromId(styleId)
            customFontId = intent.getStringExtra(EXTRA_CUSTOM_FONT_ID)
            depthEnabled = intent.getBooleanExtra(EXTRA_DEPTH_ENABLED, true)
        }
    }

    private fun loadImage() {
        val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
        val imageUri = intent.data

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = when {
                presetId != null -> {
                    val preset = ThemePresetCatalog.find(presetId)
                    renderDrawableToBitmap(preset.previewRes)
                }
                imageUri != null -> {
                    try {
                        BitmapDecoder.decodeUriFullSize(this@LockscreenEditorActivity, imageUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding image uri", e)
                        null
                    }
                }
                else -> {
                    val file = File(filesDir, "wallpaper.jpg")
                    if (file.exists()) BitmapDecoder.decodePreview(file) else null
                }
            }

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) {
                    bitmap?.recycle()
                    return@withContext
                }
                wallpaperBitmap = bitmap
                if (bitmap != null) {
                    requestSubjectSegmentation(bitmap)
                }
            }
        }
    }

    private fun renderDrawableToBitmap(drawableResId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableResId)!!
        val width = 1080
        val height = 2400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun requestSubjectSegmentation(bitmap: Bitmap) {
        isSegmenting = true
        coordinator?.close()
        val coord = SubjectMaskCoordinator(this) {
            val pending = coord?.takePending()
            if (pending != null && !isFinishing && !isDestroyed) {
                runOnUiThread {
                    maskBitmap = pending.bitmap
                    isSegmenting = false
                }
            }
        }
        coordinator = coord
        coord.configure(true)
        lifecycleScope.launch(Dispatchers.Default) {
            coord.request(bitmap, 1L)
            maskFailureReason = SubjectMaskDiagnostics.lastFailure
        }
    }

    private fun applyWallpaper() {
        val bitmap = wallpaperBitmap ?: return
        if (isSaving) return
        isSaving = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                WallpaperStorageCoordinator.runExclusive {
                    val appPrefs = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
                    val wallpaperPrefs = getSharedPreferences(WALLPAPER_PREFERENCES, MODE_PRIVATE)

                    val transactions = mutableListOf<FileTransactions.ReplacementTransaction>()
                    val token = UUID.randomUUID().toString()
                    val stagedWallpaper = File(filesDir, ".wallpaper-$token.staged")
                    val stagedSource = File(filesDir, ".wallpaper-source-$token.staged")

                    BitmapStore.writeJpegAtomically(bitmap, stagedWallpaper, quality = 100)
                    BitmapStore.writeJpegAtomically(bitmap, stagedSource, quality = 100)

                    transactions += FileTransactions.beginReplacingFiles(
                        listOf(
                            stagedWallpaper to File(filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE),
                            stagedSource to File(filesDir, WallpaperFitHelper.ACTIVE_SOURCE_FILE)
                        )
                    )

                    // Write app prefs for Clock & Glass
                    appPrefs.edit {
                        putBoolean(AtmosphereClockPolicy.ENABLED_KEY, true)
                        putBoolean(AtmosphereClockPolicy.DEPTH_KEY, depthEnabled)
                        putString(AtmosphereClockPolicy.STYLE_KEY, clockStyle.id)
                        putString(AtmosphereClockPolicy.CUSTOM_FONT_ID_KEY, customFontId)
                        putFloat(AtmosphereClockPolicy.OPACITY_KEY, clockOpacity)
                        putFloat(AtmosphereClockPolicy.HEIGHT_KEY, clockSize)
                        putInt(AtmosphereClockPolicy.COLOR_KEY, clockColor)
                        putBoolean(AtmosphereGlassPolicy.ENABLED_KEY, isAtmosphereGlassEnabled)
                    }

                    wallpaperPrefs.edit {
                        putString(PlaylistModeManager.KEY_MODE, PlaylistModeManager.MODE_SINGLE)
                    }

                    WallpaperFitHelper.setActiveModes(this@LockscreenEditorActivity, WallpaperFitHelper.MODE_FILL, WallpaperFitHelper.FILL_BLACK)
                    FileTransactions.commitAll(transactions)
                }

                withContext(Dispatchers.Main) {
                    sendBroadcast(Intent(ACTION_RELOAD_WALLPAPER).setPackage(packageName))
                    sendBroadcast(Intent(ACTION_UPDATE_CONFIG).setPackage(packageName))

                    Toast.makeText(
                        this@LockscreenEditorActivity,
                        "Configuration appliquée avec succès !",
                        Toast.LENGTH_LONG
                    ).show()

                    if (WallpaperEffectServices.launchPicker(this@LockscreenEditorActivity, activeEffectId)) {
                        finish()
                    } else {
                        Toast.makeText(
                            this@LockscreenEditorActivity,
                            "Fond d'écran configuré. Veuillez activer Atmo Engine dans vos fonds d'écran animés.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying wallpaper", e)
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(this@LockscreenEditorActivity, "Erreur lors de l'enregistrement.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        coordinator?.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LockscreenEditor"
        const val EXTRA_PRESET_ID = "EXTRA_PRESET_ID"
        const val EXTRA_EFFECT_ID = "EXTRA_EFFECT_ID"
        const val EXTRA_GLASS_ENABLED = "EXTRA_GLASS_ENABLED"
        const val EXTRA_CLOCK_STYLE = "EXTRA_CLOCK_STYLE"
        const val EXTRA_CUSTOM_FONT_ID = "EXTRA_CUSTOM_FONT_ID"
        const val EXTRA_DEPTH_ENABLED = "EXTRA_DEPTH_ENABLED"

        private const val APP_PREFERENCES = "app_prefs"
        private const val WALLPAPER_PREFERENCES = "wallpaper_prefs"
        private const val ACTION_RELOAD_WALLPAPER = "com.app.nosatmosphereeffect.RELOAD_WALLPAPER"
        private const val ACTION_UPDATE_CONFIG = "com.app.nosatmosphereeffect.UPDATE_CONFIG"

        fun intentForPreset(context: Context, presetId: String): Intent {
            return Intent(context, LockscreenEditorActivity::class.java).apply {
                putExtra(EXTRA_PRESET_ID, presetId)
            }
        }

        fun intentForUri(context: Context, uri: Uri): Intent {
            return Intent(context, LockscreenEditorActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
