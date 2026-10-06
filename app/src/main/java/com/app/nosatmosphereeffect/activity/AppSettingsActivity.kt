package com.app.nosatmosphereeffect.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.app.nosatmosphereeffect.helper.CustomFontInfo
import com.app.nosatmosphereeffect.helper.CustomFontManager
import com.app.nosatmosphereeffect.helper.LocaleHelper
import com.app.nosatmosphereeffect.ui.screens.SettingsScreen
import com.app.nosatmosphereeffect.ui.theme.AppearancePreferences
import com.app.nosatmosphereeffect.ui.theme.AppThemeMode
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme

class AppSettingsActivity : ComponentActivity() {

    private var currentLanguage by mutableStateOf(LocaleHelper.LANG_SYSTEM)
    private var expressiveThemeEnabled by mutableStateOf(true)
    private var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
    private var pitchBlackEnabled by mutableStateOf(false)
    private var customFonts by mutableStateOf<List<CustomFontInfo>>(emptyList())

    private val pickFontFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val imported = CustomFontManager.importFontFromUri(this, it)
            if (imported != null) {
                Toast.makeText(this, "Police importée : ${imported.name}", Toast.LENGTH_SHORT).show()
                refreshCustomFonts()
            } else {
                Toast.makeText(this, "Impossible de charger ce fichier de police.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        currentLanguage = LocaleHelper.getLanguage(this)
        expressiveThemeEnabled = AppearancePreferences.isExpressiveEnabled(this)
        themeMode = AppearancePreferences.getThemeMode(this)
        pitchBlackEnabled = AppearancePreferences.isPitchBlackEnabled(this)
        refreshCustomFonts()

        setContent {
            AtmoEngineTheme(
                expressive = expressiveThemeEnabled,
                themeMode = themeMode,
                pitchBlack = pitchBlackEnabled
            ) {
                SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageChange = { newLang ->
                        currentLanguage = newLang
                        LocaleHelper.setLanguage(this, newLang)
                        recreate()
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        AppearancePreferences.setThemeMode(this, mode)
                    },
                    pitchBlackEnabled = pitchBlackEnabled,
                    onPitchBlackChange = { enabled ->
                        pitchBlackEnabled = enabled
                        AppearancePreferences.setPitchBlackEnabled(this, enabled)
                    },
                    customFonts = customFonts,
                    onImportFontClick = {
                        pickFontFile.launch("*/*")
                    },
                    onDeleteFontClick = { fontId ->
                        CustomFontManager.deleteFont(this, fontId)
                        refreshCustomFonts()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun refreshCustomFonts() {
        customFonts = CustomFontManager.getCustomFonts(this)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AppSettingsActivity::class.java)
    }
}
