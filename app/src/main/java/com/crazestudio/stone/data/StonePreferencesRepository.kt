package com.crazestudio.stone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stoneDataStore: DataStore<Preferences> by preferencesDataStore(name = "stone_preferences")

data class StonePreferences(
    val displayName: String = "CrazeStudio Dev",
    val themeName: String = "Stone Dark",
    val editorFontSizeSp: Float = 14f,
    val tabSize: Int = 4,
    val autoSaveEnabled: Boolean = false,
    val wordWrapEnabled: Boolean = false,
    val gitAuthorName: String = "Stone User",
    val gitAuthorEmail: String = "user@stone.local",
    val enabledProfiles: Set<String> = setOf("HTML / CSS / JavaScript", "Kotlin", "Python")
)

class StonePreferencesRepository(private val context: Context) {
    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val FONT_SIZE = floatPreferencesKey("editor_font_size")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_enabled")
        val WORD_WRAP = booleanPreferencesKey("word_wrap_enabled")
        val GIT_AUTHOR_NAME = stringPreferencesKey("git_author_name")
        val GIT_AUTHOR_EMAIL = stringPreferencesKey("git_author_email")
        val ENABLED_PROFILES = stringSetPreferencesKey("enabled_profiles")
    }

    val preferences: Flow<StonePreferences> = context.stoneDataStore.data.map { prefs ->
        StonePreferences(
            displayName = prefs[Keys.DISPLAY_NAME]?.takeIf { it.isNotBlank() } ?: "CrazeStudio Dev",
            themeName = prefs[Keys.THEME_NAME] ?: "Stone Dark",
            editorFontSizeSp = (prefs[Keys.FONT_SIZE] ?: 14f).coerceIn(11f, 22f),
            tabSize = (prefs[Keys.TAB_SIZE] ?: 4).let { if (it in listOf(2, 4, 8)) it else 4 },
            autoSaveEnabled = prefs[Keys.AUTO_SAVE] ?: false,
            wordWrapEnabled = prefs[Keys.WORD_WRAP] ?: false,
            gitAuthorName = prefs[Keys.GIT_AUTHOR_NAME]?.takeIf { it.isNotBlank() } ?: "Stone User",
            gitAuthorEmail = prefs[Keys.GIT_AUTHOR_EMAIL]?.takeIf { it.isNotBlank() } ?: "user@stone.local",
            enabledProfiles = prefs[Keys.ENABLED_PROFILES] ?: setOf("HTML / CSS / JavaScript", "Kotlin", "Python")
        )
    }

    suspend fun setDisplayName(value: String) {
        val clean = value.trim().take(36).ifBlank { "CrazeStudio Dev" }
        context.stoneDataStore.edit { it[Keys.DISPLAY_NAME] = clean }
    }

    suspend fun setThemeName(value: String) {
        context.stoneDataStore.edit { it[Keys.THEME_NAME] = value }
    }

    suspend fun setEditorFontSize(value: Float) {
        context.stoneDataStore.edit { it[Keys.FONT_SIZE] = value.coerceIn(11f, 22f) }
    }

    suspend fun setTabSize(value: Int) {
        context.stoneDataStore.edit { it[Keys.TAB_SIZE] = if (value in listOf(2, 4, 8)) value else 4 }
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        context.stoneDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }

    suspend fun setWordWrapEnabled(enabled: Boolean) {
        context.stoneDataStore.edit { it[Keys.WORD_WRAP] = enabled }
    }

    suspend fun setGitAuthor(name: String, email: String) {
        context.stoneDataStore.edit {
            it[Keys.GIT_AUTHOR_NAME] = name.trim().take(48).ifBlank { "Stone User" }
            it[Keys.GIT_AUTHOR_EMAIL] = email.trim().take(64).ifBlank { "user@stone.local" }
        }
    }

    suspend fun toggleProfile(profileName: String) {
        context.stoneDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_PROFILES] ?: setOf("HTML / CSS / JavaScript", "Kotlin", "Python")
            prefs[Keys.ENABLED_PROFILES] = if (profileName in current) current - profileName else current + profileName
        }
    }
}
