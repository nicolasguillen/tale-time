package com.nicolasguillen.taletime.story

/**
 * A story as `schema/story.schema.json` defines it (docs/story-format.md): a small graph of pages, forks and endings,
 * in every language it has. On disk each language is its own file (`story/<lang>.json`); [StoryParser] reads them
 * into one. This package is pure Kotlin (stdlib + kotlinx.serialization for parsing), like the rest of the reading
 * rules.
 *
 * @property languages the language it was written in first, then its translations.
 * @property nodes in reading order, as written in its files.
 */
data class Story(
    val id: String,
    val version: Int,
    val ageBand: String,
    val languages: List<String>,
    val title: Text,
    val cover: String,
    val start: String,
    val nodes: Map<String, Node>,
) {
    fun node(id: String): Node = nodes[id] ?: error("Story '${this.id}' has no node '$id'")

    /** The language to read this story in: [preferred] if the story has it, else its first language. */
    fun languageFor(preferred: String): String = if (preferred in languages) preferred else languages.first()

    /** Every ending, in reading order. */
    val endings: List<Ending> get() = nodes.values.filterIsInstance<Ending>()
}

/** A text in every language of the story. */
data class Text(
    val values: Map<String, String>,
) {
    /** The text in [language], or in the story's first language if it has none in that one. */
    operator fun get(language: String): String = values[language] ?: values.values.first()

    /** This text in its languages and in [other]'s. */
    operator fun plus(other: Text): Text = Text(values + other.values)
}

sealed interface Node {
    val id: String
    val text: Text

    /** Image id of the page illustration. */
    val image: String
}

/** A plain page: text, illustration, one next node. */
data class Page(
    override val id: String,
    override val text: Text,
    override val image: String,
    val next: String,
) : Node

/** A page that ends with a question and exactly two options, A then B. */
data class Fork(
    override val id: String,
    override val text: Text,
    override val image: String,
    val question: Text,
    val options: List<Option>,
) : Node

data class Option(
    val label: Text,
    /** Image id of the choice card (square). */
    val image: String,
    val target: String,
)

/** A final page. Its illustration doubles as the ending's badge. */
data class Ending(
    override val id: String,
    override val text: Text,
    override val image: String,
    val kind: EndingKind,
    val title: Text,
) : Node

enum class EndingKind { BEST, HAPPY, COZY, SILLY }
