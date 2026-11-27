package com.nicolasguillen.taletime.domain

/**
 * The languages Tale Time speaks: its own words (`i18n/`) and the stories' texts. A grown-up picks one in the parents
 * area; until then it's [DEFAULT]. A story that isn't in the chosen language yet shows in its first language
 * ([com.nicolasguillen.taletime.story.Story.languageFor]).
 *
 * @property code the language code the stories use (docs/story-format.md). Spanish is Latin American Spanish.
 * @property nativeName how the language calls itself: the language picker shows each one in its own words.
 * @property flag the flag a book's cover shows for it, as an emoji: the United States for English, Mexico for Latin
 *   American Spanish.
 */
enum class Language(
    val code: String,
    val nativeName: String,
    val flag: String,
) {
    ENGLISH("en", "English", "\uD83C\uDDFA\uD83C\uDDF8"),
    SPANISH("es", "Español", "\uD83C\uDDF2\uD83C\uDDFD"),
    ;

    companion object {
        /**
         * The language until a grown-up picks one: Spanish for now, whatever the device speaks, since the stories are
         * published in Spanish first (content/catalog.json, `approved`).
         */
        val DEFAULT = SPANISH

        /** The language for a code like "es", "es-MX", "es_419" or "es-419", or null if Tale Time doesn't speak it. */
        fun of(code: String?): Language? {
            val base = code?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return entries.firstOrNull { it.code == base }
        }
    }
}
