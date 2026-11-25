package com.nicolasguillen.taletime.domain

/**
 * The languages Tale Time speaks: its own words (`i18n/`) and the stories' texts. A grown-up picks one in the parents
 * area; until then it's the device's language, when Tale Time speaks it. A story that isn't in the chosen language yet
 * shows in its first language ([com.nicolasguillen.taletime.story.Story.languageFor]).
 *
 * @property code the language code the stories use (docs/story-format.md). Spanish is Latin American Spanish.
 * @property nativeName how the language calls itself: the language picker shows each one in its own words.
 */
enum class Language(
    val code: String,
    val nativeName: String,
) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    ;

    companion object {
        /** The default when neither a grown-up nor the device picked a language Tale Time speaks. */
        val DEFAULT = ENGLISH

        /** The language for a code like "es", "es-MX", "es_419" or "es-419", or null if Tale Time doesn't speak it. */
        fun of(code: String?): Language? {
            val base = code?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return entries.firstOrNull { it.code == base }
        }
    }
}
