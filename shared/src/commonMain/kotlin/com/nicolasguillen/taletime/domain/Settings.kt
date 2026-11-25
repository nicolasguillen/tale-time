package com.nicolasguillen.taletime.domain

import kotlinx.coroutines.flow.Flow

enum class TextSize(
    val scale: Float,
) {
    SMALL(0.85f),
    MEDIUM(1f),
    LARGE(1.2f),
}

/**
 * The parents area's settings, kept on the device.
 *
 * @property language the language the app and its stories speak: the one a grown-up picked, or the device's own
 *   until then (when Tale Time speaks it).
 */
data class Settings(
    val textSize: TextSize = TextSize.MEDIUM,
    val language: Language = Language.DEFAULT,
)

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
