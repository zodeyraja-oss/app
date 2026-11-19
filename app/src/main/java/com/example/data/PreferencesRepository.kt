package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kinChatDataStore: DataStore<Preferences> by preferencesDataStore(name = "kinchat_accessibility_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_COLOR = stringPreferencesKey("app_color")
        val CHAT_COLOR = stringPreferencesKey("chat_color")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val EASY_MODE = booleanPreferencesKey("easy_mode")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
    }

    val preferencesFlow: Flow<AccessibilityPreferences> = context.kinChatDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val appColorName = prefs[Keys.APP_COLOR] ?: AppColorTheme.EMERALD.name
        val chatColorName = prefs[Keys.CHAT_COLOR] ?: ChatColorTheme.MATCH_APP.name
        val sizeName = prefs[Keys.TEXT_SIZE] ?: TextSizePreset.COMFORT.name
        val voiceName = prefs[Keys.TTS_VOICE] ?: GeminiTtsVoice.KORE.name
        AccessibilityPreferences(
            themeMode = enumValues<ThemeMode>().firstOrNull { it.name == themeName }
                ?: ThemeMode.SYSTEM,
            appColor = enumValues<AppColorTheme>().firstOrNull { it.name == appColorName }
                ?: AppColorTheme.EMERALD,
            chatColor = enumValues<ChatColorTheme>().firstOrNull { it.name == chatColorName }
                ?: ChatColorTheme.MATCH_APP,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            textSizePreset = enumValues<TextSizePreset>().firstOrNull { it.name == sizeName }
                ?: TextSizePreset.COMFORT,
            easyMode = prefs[Keys.EASY_MODE] ?: false,
            ttsVoice = enumValues<GeminiTtsVoice>().firstOrNull { it.name == voiceName }
                ?: GeminiTtsVoice.KORE
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.kinChatDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAppColor(color: AppColorTheme) {
        context.kinChatDataStore.edit { it[Keys.APP_COLOR] = color.name }
    }

    suspend fun setChatColor(color: ChatColorTheme) {
        context.kinChatDataStore.edit { it[Keys.CHAT_COLOR] = color.name }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.kinChatDataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    suspend fun setTextSizePreset(preset: TextSizePreset) {
        context.kinChatDataStore.edit { it[Keys.TEXT_SIZE] = preset.name }
    }

    suspend fun setEasyMode(enabled: Boolean) {
        context.kinChatDataStore.edit { it[Keys.EASY_MODE] = enabled }
    }

    suspend fun setTtsVoice(voice: GeminiTtsVoice) {
        context.kinChatDataStore.edit { it[Keys.TTS_VOICE] = voice.name }
    }

    suspend fun applyCloudProfilePreferences(profile: UserPrivate) {
        context.kinChatDataStore.edit { prefs ->
            profile.themeMode?.let { raw ->
                enumValues<ThemeMode>().firstOrNull { it.name == raw }?.let {
                    prefs[Keys.THEME_MODE] = it.name
                }
            }
            profile.appColor?.let { raw ->
                enumValues<AppColorTheme>().firstOrNull { it.name == raw }?.let {
                    prefs[Keys.APP_COLOR] = it.name
                }
            }
            profile.chatColor?.let { raw ->
                enumValues<ChatColorTheme>().firstOrNull { it.name == raw }?.let {
                    prefs[Keys.CHAT_COLOR] = it.name
                }
            }
            profile.highContrast?.let { prefs[Keys.HIGH_CONTRAST] = it }
            profile.textSizePreset?.let { raw ->
                enumValues<TextSizePreset>().firstOrNull { it.name == raw }?.let {
                    prefs[Keys.TEXT_SIZE] = it.name
                }
            }
            profile.easyMode?.let { prefs[Keys.EASY_MODE] = it }
            profile.ttsVoice?.let { raw ->
                enumValues<GeminiTtsVoice>().firstOrNull { it.name == raw }?.let {
                    prefs[Keys.TTS_VOICE] = it.name
                }
            }
        }
    }
}
