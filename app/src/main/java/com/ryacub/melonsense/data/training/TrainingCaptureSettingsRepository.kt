package com.ryacub.melonsense.data.training

import android.content.Context

class TrainingCaptureSettingsRepository(
    private val readStored: () -> Boolean?,
    private val writeStored: (Boolean) -> Unit,
) {
    constructor(context: Context) : this(
        readStored = {
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .let { preferences ->
                    if (preferences.contains(KEY_TRAINING_CAPTURE_ENABLED)) {
                        preferences.getBoolean(KEY_TRAINING_CAPTURE_ENABLED, true)
                    } else {
                        null
                    }
                }
        },
        writeStored = { enabled ->
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_TRAINING_CAPTURE_ENABLED, enabled)
                .apply()
        },
    )

    val isEnabled: Boolean
        get() = readStored() ?: true

    fun setEnabled(enabled: Boolean) {
        writeStored(enabled)
    }

    private companion object {
        const val PREFERENCES_NAME = "training-capture-settings"
        const val KEY_TRAINING_CAPTURE_ENABLED = "training-capture-enabled"
    }
}
