package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class ThemeSettings(
    val mode: AppThemeMode = AppThemeMode.DARK,
    val accentColorArgb: Int = 0xFFFF5A36.toInt()
)

val PresetAccentColors = listOf(
    0xFFFF5A36.toInt() to "Coral",
    0xFF2EE6A8.toInt() to "Mint",
    0xFF4E8BFF.toInt() to "Cobalt",
    0xFFFFB84D.toInt() to "Amber",
    0xFF9E7BFF.toInt() to "Violet",
    0xFFFF4D8D.toInt() to "Rose"
)

class StudioPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("htype_studio_prefs", Context.MODE_PRIVATE)

    private val _themeSettings = MutableStateFlow(readTheme())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private val _favoriteFonts = MutableStateFlow(readFavoriteFonts())
    val favoriteFonts: StateFlow<Set<String>> = _favoriteFonts.asStateFlow()

    private val _savedColors = MutableStateFlow(readColorList(KEY_SAVED_COLORS, DEFAULT_SAVED_COLORS))
    val savedColors: StateFlow<List<Int>> = _savedColors.asStateFlow()

    private val _recentColors = MutableStateFlow(readColorList(KEY_RECENT_COLORS, emptyList()))
    val recentColors: StateFlow<List<Int>> = _recentColors.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeSettings.value = _themeSettings.value.copy(mode = mode)
    }

    fun setAccentColor(argb: Int) {
        prefs.edit().putInt(KEY_ACCENT_COLOR, argb).apply()
        _themeSettings.value = _themeSettings.value.copy(accentColorArgb = argb)
    }

    fun toggleFavoriteFontFamily(familyKey: String) {
        val current = _favoriteFonts.value.toMutableSet()
        if (!current.add(familyKey)) {
            current.remove(familyKey)
        }
        prefs.edit().putStringSet(KEY_FAVORITE_FONTS, current).apply()
        _favoriteFonts.value = current
    }

    fun addSavedColor(argb: Int) {
        val current = _savedColors.value.toMutableList()
        current.remove(argb)
        current.add(0, argb)
        val trimmed = current.take(16)
        writeColorList(KEY_SAVED_COLORS, trimmed)
        _savedColors.value = trimmed
    }

    fun removeSavedColor(argb: Int) {
        val updated = _savedColors.value.filterNot { it == argb }
        writeColorList(KEY_SAVED_COLORS, updated)
        _savedColors.value = updated
    }

    fun recordRecentColor(argb: Int) {
        val current = _recentColors.value.toMutableList()
        current.remove(argb)
        current.add(0, argb)
        val trimmed = current.take(12)
        writeColorList(KEY_RECENT_COLORS, trimmed)
        _recentColors.value = trimmed
    }

    private fun readTheme(): ThemeSettings {
        val modeName = prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val mode = runCatching { AppThemeMode.valueOf(modeName) }.getOrDefault(AppThemeMode.DARK)
        val accent = prefs.getInt(KEY_ACCENT_COLOR, 0xFFFF5A36.toInt())
        return ThemeSettings(mode = mode, accentColorArgb = accent)
    }

    private fun readFavoriteFonts(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_FONTS, emptySet())?.toSet() ?: emptySet()
    }

    private fun readColorList(key: String, defaults: List<Int>): List<Int> {
        val raw = prefs.getString(key, null) ?: return defaults
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    private fun writeColorList(key: String, list: List<Int>) {
        prefs.edit().putString(key, list.joinToString(",")).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_FAVORITE_FONTS = "favorite_fonts"
        private const val KEY_SAVED_COLORS = "saved_colors"
        private const val KEY_RECENT_COLORS = "recent_colors"

        private val DEFAULT_SAVED_COLORS = listOf(
            0xFFFFFFFF.toInt(),
            0xFF0E1015.toInt(),
            0xFFFF5A36.toInt(),
            0xFFFFB84D.toInt(),
            0xFF2EE6A8.toInt(),
            0xFF5C9DFF.toInt(),
            0xFFE94D89.toInt(),
            0xFFD4AF37.toInt()
        )
        @Volatile
        private var INSTANCE: StudioPreferences? = null

        fun getInstance(context: Context): StudioPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StudioPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
