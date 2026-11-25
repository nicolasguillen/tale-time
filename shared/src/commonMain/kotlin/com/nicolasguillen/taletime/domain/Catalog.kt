package com.nicolasguillen.taletime.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A file in a story's folder on the content host, named after its contents, with its size and SHA-256. */
@Serializable
data class HostedFile(
    val file: String,
    val bytes: Long,
    val sha256: String,
)

/**
 * A language a reader can download a story in.
 *
 * @property bytes the download in this language alone: the pictures, the text and the narration.
 * @property narration the pages are read aloud in this language.
 */
@Serializable
data class LanguageOffer(
    val bytes: Long,
    val narration: Boolean = false,
)

/**
 * One book on the shelf, from the published catalog (`stories/<channel>/catalog.json`, schema/published-catalog.schema.json),
 * in shelf order.
 *
 * @property product the store's one-time purchase that unlocks a [Access.PAID] story, the same id on the App Store
 *   and Google Play. Null for free stories.
 * @property title the title in every language the story is published in, its first language first.
 * @property languages the languages a reader can download it in, its first language first. Empty in the shelf that
 *   ships inside the app (`files/shelf.json`), which knows only the titles until the app has been online once.
 * @property cover the cover picture, shown before the story is downloaded. Null while it isn't delivered.
 * @property manifest the story's current manifest: every file of this version. A new name means the story changed.
 */
@Serializable
data class CatalogEntry(
    val id: String,
    val access: Access,
    val product: String? = null,
    val ageBand: String = "",
    val title: Map<String, String> = emptyMap(),
    val languages: Map<String, LanguageOffer> = emptyMap(),
    val cover: HostedFile? = null,
    val manifest: HostedFile? = null,
) {
    /** The title in [language], else in the story's first language. */
    fun title(language: String): String = title[language] ?: title.values.firstOrNull() ?: id

    /** The language to offer first: [preferred] when the story is published in it, else its first language. */
    fun firstChoice(preferred: String): String? = preferred.takeIf { it in languages } ?: languages.keys.firstOrNull()

    /** It can be downloaded: the catalog it comes from was fetched from the host. */
    val downloadable: Boolean get() = manifest != null && languages.isNotEmpty()
}

@Serializable
enum class Access {
    @SerialName("free")
    FREE,

    @SerialName("paid")
    PAID,
}

@Serializable
private class CatalogFile(
    val stories: List<CatalogEntry>,
)

object CatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Unknown fields are skipped, so an older app can read a newer catalog. */
    fun parse(text: String): List<CatalogEntry> =
        json
            .decodeFromString<CatalogFile>(text)
            .stories
            .filter { it.access == Access.FREE || it.product != null }
}

/** Whether a child can open this book now: free, or its product has been bought on this account. */
fun CatalogEntry.isOpen(owned: Set<String>): Boolean = access == Access.FREE || product in owned
