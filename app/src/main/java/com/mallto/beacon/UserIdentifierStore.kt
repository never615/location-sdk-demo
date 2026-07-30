package com.mallto.beacon

import android.content.Context
import android.provider.Settings

object UserIdentifierStore {
    private const val PREFS_NAME = "app"
    private const val PREF_USER_IDENTIFIER = "user_identifier"
    private const val PREF_ANDROID_ID_OFFSET = "android_id_offset"
    private const val ANDROID_ID_EXTRACT_SIZE = 3
    private const val ANDROID_ID_BYTES = 8

    @JvmStatic
    fun ensureGenerated(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIdentifier = prefs.getString(PREF_USER_IDENTIFIER, "").orEmpty()
        if (savedIdentifier.isNotEmpty()) return savedIdentifier

        val generatedIdentifier = generateFromAndroidId(context, advance = false).orEmpty()
        if (generatedIdentifier.isNotEmpty()) {
            prefs.edit().putString(PREF_USER_IDENTIFIER, generatedIdentifier).apply()
        }
        return generatedIdentifier
    }

    fun generateFromAndroidId(context: Context, advance: Boolean): String? {
        val rawId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (rawId.isNullOrBlank()) return null

        val hex = rawId.trim().lowercase().replace(Regex("[^0-9a-f]"), "")
            .padStart(ANDROID_ID_BYTES * 2, '0')
        if (hex.length < ANDROID_ID_BYTES * 2) return null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var offset = prefs.getInt(PREF_ANDROID_ID_OFFSET, 0)
        if (advance) {
            offset = (offset + 1) % (ANDROID_ID_BYTES - ANDROID_ID_EXTRACT_SIZE + 1)
        }
        val identifier = hex.substring(
            offset * 2,
            (offset + ANDROID_ID_EXTRACT_SIZE) * 2
        )

        prefs.edit().putInt(PREF_ANDROID_ID_OFFSET, offset).apply()
        return identifier
    }
}
