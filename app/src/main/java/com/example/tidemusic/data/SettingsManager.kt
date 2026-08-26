package com.example.tidemusic.data

import android.content.Context
import android.content.SharedPreferences
import com.example.tidemusic.ui.common.SortCriteria
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tide_settings", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        _isDarkMode.value = enabled
    }

    /** Frosted-glass (blurred artwork backdrop) effects on the player & mini-player. */
    private val _isGlassEffectsEnabled = MutableStateFlow(prefs.getBoolean("glass_effects", true))
    val isGlassEffectsEnabled: StateFlow<Boolean> = _isGlassEffectsEnabled

    fun setGlassEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("glass_effects", enabled).apply()
        _isGlassEffectsEnabled.value = enabled
    }

    /** Controls whether lyrics automatically scroll and track live audio position. */
    private val _isLyricsAutoScrollEnabled = MutableStateFlow(prefs.getBoolean("lyrics_auto_scroll", true))
    val isLyricsAutoScrollEnabled: StateFlow<Boolean> = _isLyricsAutoScrollEnabled

    fun setLyricsAutoScrollEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("lyrics_auto_scroll", enabled).apply()
        _isLyricsAutoScrollEnabled.value = enabled
    }

    /**
     * Retrieves saved sort criteria for a given playlist (or default screen key).
     * Defaults to A-Z (TITLE_ASC) for all playlists as requested by user.
     */
    fun getSortCriteria(key: String): List<SortCriteria> {
        val saved = prefs.getString("sort_criteria_$key", null) ?: return listOf(SortCriteria.TITLE_ASC)
        if (saved.isBlank()) return listOf(SortCriteria.TITLE_ASC)
        return saved.split(",").mapNotNull { name ->
            try {
                SortCriteria.valueOf(name.trim())
            } catch (e: Exception) {
                null
            }
        }.ifEmpty { listOf(SortCriteria.TITLE_ASC) }
    }

    /**
     * Saves sort criteria for a given playlist or screen key to SharedPreferences.
     */
    fun saveSortCriteria(key: String, criteria: List<SortCriteria>) {
        val str = criteria.joinToString(",") { it.name }
        prefs.edit().putString("sort_criteria_$key", str).apply()
    }

    fun getExcludedFolders(): Set<String> {
        return prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
    }

    fun saveExcludedFolders(folders: Set<String>) {
        prefs.edit().putStringSet("excluded_folders", folders).apply()
    }

    fun getCustomIncludeFolders(): Set<String> {
        return prefs.getStringSet("custom_include_folders", emptySet()) ?: emptySet()
    }

    fun saveCustomIncludeFolders(folders: Set<String>) {
        prefs.edit().putStringSet("custom_include_folders", folders).apply()
    }
}
