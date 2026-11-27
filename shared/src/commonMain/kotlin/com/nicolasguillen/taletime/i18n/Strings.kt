package com.nicolasguillen.taletime.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.nicolasguillen.taletime.domain.Language

/**
 * Every word the app itself shows or reads to a screen reader, in one language. The stories bring their own words
 * (`story/<lang>.json`); these are the app's. Each language is one complete [Strings], so the compiler catches a missing one.
 * Kid-facing screens use as few words as possible: pictures carry them.
 */
data class Strings(
    // Shelf and covers
    val myBooks: String,
    val store: String,
    val noBooksYet: String,
    val everyBookIsYours: String,
    val free: String,
    val locked: String,
    // A book's caption on its cover: "Ages: 4–7", "Endings: 5", "Languages: …", "Price: Free"
    val agesKey: String,
    val ageRange: (band: String) -> String,
    val endingsKey: String,
    val languagesKey: String,
    val priceKey: String,
    val read: String,
    val continueReading: String,
    val startOver: String,
    val endingsFoundOf: (found: Int, total: Int) -> String,
    val storyMissing: String,
    val backToShelf: String,
    // Downloads
    val download: String,
    val downloading: String,
    val downloadFailed: String,
    val needsInternet: String,
    val tryAgain: String,
    val megabytes: (bytes: Long) -> String,
    // Reader
    val storyCantOpen: String,
    val nextPage: String,
    val theEnd: String,
    val newEnding: String,
    val endingsFound: (found: Int, total: Int) -> String,
    val tryAnotherPath: String,
    // Unlocking a paid story
    val unlock: (title: String) -> String,
    val unlockExplained: String,
    val buy: String,
    val buyFor: (price: String) -> String,
    val buyForever: String,
    val restorePurchases: String,
    val notNow: String,
    val storeUnavailable: String,
    // Parental gate
    val forGrownUps: String,
    val close: String,
    val gateAsk: String,
    val gateRetry: String,
    val times: (a: Int, b: Int) -> String,
    val erase: String,
    // Languages
    val storyLanguage: String,
    // Parents area
    val language: String,
    val languageExplained: String,
    val textSize: String,
    val progress: String,
    val progressExplained: String,
    val forgetEverything: String,
    val yesForget: String,
    val keep: String,
    val resetProgress: String,
    val resetDone: String,
    val downloads: String,
    val downloadsExplained: (size: String) -> String,
    val deleteDownloadsAsk: String,
    val yesDelete: String,
    val deleteDownloads: String,
    val downloadsDeleted: String,
    val aboutTaleTime: String,
    val aboutStories: String,
    val aboutPrivacy: String,
    val version: (version: String) -> String,
)

/** The numbers the parental gate writes out, 3 to 9. */
private fun numberWords(vararg words: String): (Int) -> String = { n -> words.getOrNull(n - 3) ?: n.toString() }

/** "1.3 MB", "12 MB", with the language's decimal mark: at least 0.1 MB, whole megabytes from 10 MB. */
private fun megabytes(decimal: Char): (Long) -> String =
    { bytes ->
        val tenths = ((bytes + 50_000) / 100_000).coerceAtLeast(1)
        if (tenths >= 100) "${(tenths + 5) / 10} MB" else "${tenths / 10}$decimal${tenths % 10} MB"
    }

private val englishNumber = numberWords("three", "four", "five", "six", "seven", "eight", "nine")
private val spanishNumber = numberWords("tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve")

val EnglishStrings =
    Strings(
        myBooks = "My Books",
        store = "Store",
        noBooksYet = "No books here yet. Pick one in the Store!",
        everyBookIsYours = "You have every book!",
        free = "Free",
        locked = "Locked",
        agesKey = "Ages",
        ageRange = { band -> band.replace("-", "–") },
        endingsKey = "Endings",
        languagesKey = "Languages",
        priceKey = "Price",
        read = "Read",
        continueReading = "Continue",
        startOver = "Start over",
        endingsFoundOf = { found, total -> "Endings found: $found of $total" },
        storyMissing = "This story isn't here.",
        backToShelf = "Back to the shelf",
        download = "Download",
        downloading = "Downloading",
        downloadFailed = "The story didn't download. Check the internet connection and try again.",
        needsInternet = "Connect to the internet to download this story.",
        tryAgain = "Try again",
        megabytes = megabytes('.'),
        storyCantOpen = "This story can't be opened.",
        nextPage = "Next",
        theEnd = "The End",
        newEnding = "New!",
        endingsFound = { found, total -> "$found of $total endings found" },
        tryAnotherPath = "Try another path",
        unlock = { title -> "Unlock “$title”" },
        unlockExplained = "A one-time purchase. It stays unlocked on every device signed in to this store account.",
        buy = "Buy",
        buyFor = { price -> "Buy for $price" },
        buyForever = "Buy forever",
        restorePurchases = "Restore purchases",
        notNow = "Not now",
        storeUnavailable = "The store isn't available right now.",
        forGrownUps = "For grown-ups",
        close = "Close",
        gateAsk = "To continue, type the answer:",
        gateRetry = "Not quite. Try this one:",
        times = { a, b -> "${englishNumber(a)} times ${englishNumber(b)}" },
        erase = "Erase",
        storyLanguage = "Story language",
        language = "Language",
        languageExplained =
            "The app's words, and the language a new story is read in first. Each story can switch to another of its " +
                "languages with the cog on its cover.",
        textSize = "Text size",
        progress = "Progress",
        progressExplained = "Saved pages and the endings your child has found stay on this device only.",
        forgetEverything = "Forget every story's progress and endings found?",
        yesForget = "Yes, forget",
        keep = "Keep",
        resetProgress = "Reset all progress",
        resetDone = "Done: every story starts fresh.",
        downloads = "Downloaded stories",
        downloadsExplained = { size ->
            "They take $size on this device and read without the internet. When the app opens online, changes to " +
                "a story download by themselves."
        },
        deleteDownloadsAsk =
            "Delete every downloaded story? Progress and endings found stay, and a story downloads again when it's opened.",
        yesDelete = "Yes, delete",
        deleteDownloads = "Delete downloaded stories",
        downloadsDeleted = "Done: each story downloads again when it's opened.",
        aboutTaleTime = "About Tale Time",
        aboutStories =
            "Every story is written ahead of time with the help of AI tools, then read and reviewed by a person, " +
                "path by path, before it's published. Nothing is generated while your child reads.",
        aboutPrivacy =
            "Tale Time collects nothing: no accounts, no ads, no analytics, no tracking. Progress and settings " +
                "never leave this device.",
        version = { version -> "Version $version" },
    )

/** Latin American Spanish: tú, and words that read the same from Mexico to Argentina (tools/prompts/translate.md). */
val SpanishStrings =
    Strings(
        myBooks = "Mis libros",
        store = "Tienda",
        noBooksYet = "Todavía no hay libros aquí. ¡Elige uno en la tienda!",
        everyBookIsYours = "¡Ya tienes todos los libros!",
        free = "Gratis",
        locked = "Bloqueado",
        agesKey = "Edad",
        ageRange = { band -> "de ${band.replace("-", " a ")} años" },
        endingsKey = "Finales",
        languagesKey = "Idiomas",
        priceKey = "Precio",
        read = "Leer",
        continueReading = "Seguir leyendo",
        startOver = "Empezar de nuevo",
        endingsFoundOf = { found, total -> "Finales encontrados: $found de $total" },
        storyMissing = "Este cuento no está aquí.",
        backToShelf = "Volver a los cuentos",
        download = "Descargar",
        downloading = "Descargando",
        downloadFailed = "El cuento no se descargó. Revisa la conexión a internet e inténtalo de nuevo.",
        needsInternet = "Conéctate a internet para descargar este cuento.",
        tryAgain = "Intentar de nuevo",
        megabytes = megabytes(','),
        storyCantOpen = "Este cuento no se puede abrir.",
        nextPage = "Siguiente",
        theEnd = "Fin",
        newEnding = "¡Nuevo!",
        endingsFound = { found, total -> "$found de $total finales encontrados" },
        tryAnotherPath = "Probar otro camino",
        unlock = { title -> "Desbloquear “$title”" },
        unlockExplained =
            "Una sola compra. Queda desbloqueado en todos los dispositivos que usen esta misma cuenta de la tienda.",
        buy = "Comprar",
        buyFor = { price -> "Comprar por $price" },
        buyForever = "Comprar para siempre",
        restorePurchases = "Restaurar compras",
        notNow = "Ahora no",
        storeUnavailable = "La tienda no está disponible en este momento.",
        forGrownUps = "Para adultos",
        close = "Cerrar",
        gateAsk = "Para seguir, escribe la respuesta:",
        gateRetry = "Casi. Prueba con esta:",
        times = { a, b -> "${spanishNumber(a)} por ${spanishNumber(b)}" },
        erase = "Borrar",
        storyLanguage = "Idioma del cuento",
        language = "Idioma",
        languageExplained =
            "Las palabras de la app y el idioma en que se lee primero un cuento nuevo. Cada cuento puede cambiar a " +
                "otro de sus idiomas con el engranaje de su portada.",
        textSize = "Tamaño de la letra",
        progress = "Progreso",
        progressExplained =
            "Las páginas guardadas y los finales que encontró tu hijo o hija se quedan solo en este dispositivo.",
        forgetEverything = "¿Olvidar el progreso y los finales encontrados de todos los cuentos?",
        yesForget = "Sí, olvidar",
        keep = "Conservar",
        resetProgress = "Borrar todo el progreso",
        resetDone = "Listo: todos los cuentos empiezan de cero.",
        downloads = "Cuentos descargados",
        downloadsExplained = { size ->
            "Ocupan $size en este dispositivo y se leen sin internet. Cuando la app se abre con conexión, los " +
                "cambios de un cuento se descargan solos."
        },
        deleteDownloadsAsk =
            "¿Borrar todos los cuentos descargados? El progreso y los finales encontrados se conservan, y cada cuento " +
                "se vuelve a descargar al abrirlo.",
        yesDelete = "Sí, borrar",
        deleteDownloads = "Borrar los cuentos descargados",
        downloadsDeleted = "Listo: cada cuento se vuelve a descargar al abrirlo.",
        aboutTaleTime = "Sobre Tale Time",
        aboutStories =
            "Cada cuento se escribe con anticipación, con ayuda de herramientas de IA, y luego una persona lo lee y lo " +
                "revisa, camino por camino, antes de publicarlo. Nada se genera mientras tu hijo o hija lee.",
        aboutPrivacy =
            "Tale Time no recopila nada: ni cuentas, ni anuncios, ni analíticas, ni rastreo. El progreso y los " +
                "ajustes nunca salen de este dispositivo.",
        version = { version -> "Versión $version" },
    )

fun stringsFor(language: Language): Strings =
    when (language) {
        Language.ENGLISH -> EnglishStrings
        Language.SPANISH -> SpanishStrings
    }

/** The app's words in the language Tale Time speaks right now. */
val LocalStrings = staticCompositionLocalOf { EnglishStrings }

/** The language Tale Time speaks right now: its words, and the language a story is offered in first. */
val LocalLanguage = staticCompositionLocalOf { Language.DEFAULT }

/** Everything inside speaks [language]. */
@Composable
fun ProvideLanguage(
    language: Language,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLanguage provides language, LocalStrings provides stringsFor(language), content = content)
}
