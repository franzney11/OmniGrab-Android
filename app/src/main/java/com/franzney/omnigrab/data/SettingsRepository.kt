package com.franzney.omnigrab.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.franzney.omnigrab.model.CreditPack
import com.franzney.omnigrab.model.FolderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "omnigrab_prefs")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_CREDITS = intPreferencesKey("pro_credits")
        private val KEY_VIDEO_URI = stringPreferencesKey("video_tree_uri")
        private val KEY_VIDEO_LABEL = stringPreferencesKey("video_folder_label")
        private val KEY_VIDEO_AUTO = booleanPreferencesKey("video_auto_save")
        private val KEY_AUDIO_URI = stringPreferencesKey("audio_tree_uri")
        private val KEY_AUDIO_LABEL = stringPreferencesKey("audio_folder_label")
        private val KEY_AUDIO_AUTO = booleanPreferencesKey("audio_auto_save")

        val PACKS = listOf(
            CreditPack("pack_1", 1, 0.99, "1 reescalado"),
            CreditPack("pack_5", 5, 3.99, "5 reescalados"),
            CreditPack("pack_20", 20, 12.99, "20 reescalados")
        )
    }

    val creditsFlow: Flow<Int> = context.dataStore.data.map { it[KEY_CREDITS] ?: 0 }

    val folderPrefsFlow: Flow<FolderPreferences> = context.dataStore.data.map { prefs ->
        FolderPreferences(
            videoTreeUri = prefs[KEY_VIDEO_URI],
            videoFolderLabel = prefs[KEY_VIDEO_LABEL] ?: "Movies/OmniGrab",
            videoAutoSave = prefs[KEY_VIDEO_AUTO] ?: true,
            audioTreeUri = prefs[KEY_AUDIO_URI],
            audioFolderLabel = prefs[KEY_AUDIO_LABEL] ?: "Music/OmniGrab",
            audioAutoSave = prefs[KEY_AUDIO_AUTO] ?: true
        )
    }

    suspend fun getCredits(): Int = creditsFlow.first()

    suspend fun addCredits(amount: Int): Int {
        var result = 0
        context.dataStore.edit { prefs ->
            result = (prefs[KEY_CREDITS] ?: 0) + amount
            prefs[KEY_CREDITS] = result
        }
        return result
    }

    suspend fun consumeCredit(): Boolean {
        var ok = false
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CREDITS] ?: 0
            if (current >= 1) {
                prefs[KEY_CREDITS] = current - 1
                ok = true
            }
        }
        return ok
    }

    suspend fun setVideoFolder(uri: Uri) {
        val label = resolveReadableLabel(uri, "Movies/OmniGrab")
        context.dataStore.edit { prefs ->
            prefs[KEY_VIDEO_URI] = uri.toString()
            prefs[KEY_VIDEO_LABEL] = label
        }
    }

    suspend fun setAudioFolder(uri: Uri) {
        val label = resolveReadableLabel(uri, "Music/OmniGrab")
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_URI] = uri.toString()
            prefs[KEY_AUDIO_LABEL] = label
        }
    }

    suspend fun setVideoAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIDEO_AUTO] = enabled }
    }

    suspend fun setAudioAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_AUTO] = enabled }
    }

    private fun resolveReadableLabel(uri: Uri, fallback: String): String {
        return try {
            val doc = DocumentFile.fromTreeUri(context, uri)
            val name = doc?.name
            val pathSegment = uri.lastPathSegment?.substringAfter(':', "")
            when {
                !pathSegment.isNullOrBlank() -> pathSegment
                !name.isNullOrBlank() -> name
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
