package com.nicolasguillen.taletime.story

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A story the app can't read. The message says what's wrong, for logs and tests. */
class StoryFormatException(
    message: String,
) : Exception(message)

/**
 * Reads a story from its files, one per language (`story/<lang>.json`), into one [Story] that speaks every language.
 * Each file is the complete story in its language; the one without `translatedFrom` is the language it was written
 * in, and every translation must have its structure (nodes, kinds, links, images, ending kinds). It checks the shapes
 * the app relies on and leaves the rest of the format to the pipeline's `story check`; [StoryGraph.problems] covers
 * the graph.
 */
object StoryParser {
    /**
     * @param files language → the text of its `story/<lang>.json`. A device may have only some of a story's languages
     *   (a reader downloads the ones they pick), so the language it was written in may be missing: the translations
     *   must then all come from the same one, and the first of them carries the structure.
     */
    fun parse(files: Map<String, String>): Story {
        val parsed = files.map { (language, text) -> file(language, text) }
        if (parsed.isEmpty()) throw StoryFormatException("No language to read")
        val originals = parsed.filter { it.translatedFrom == null }
        if (originals.size > 1) {
            throw StoryFormatException(
                "Exactly one language must be the one the story was written in (no translatedFrom), found ${originals.size}",
            )
        }
        val source =
            originals
                .singleOrNull()
                ?.story
                ?.languages
                ?.single() ?: parsed.first().translatedFrom
        val first = originals.singleOrNull() ?: parsed.first()
        return parsed.filter { it !== first }.fold(first.story) { story, translation ->
            val language = translation.story.languages.single()
            if (translation.translatedFrom != source) {
                throw StoryFormatException(
                    "'$language' is translated from '${translation.translatedFrom}', the story's first language is '$source'",
                )
            }
            merge(story, translation.story)
        }
    }

    private class LanguageFile(
        val story: Story,
        val translatedFrom: String?,
    )

    private fun file(
        language: String,
        text: String,
    ): LanguageFile =
        try {
            file(language, Json.parseToJsonElement(text).jsonObject)
        } catch (e: StoryFormatException) {
            throw StoryFormatException("story/$language.json: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw StoryFormatException("story/$language.json is not a story: ${e.message}")
        }

    private fun file(
        language: String,
        root: JsonObject,
    ): LanguageFile {
        if (root.string("language") != language) throw StoryFormatException("it says its language is '${root.string("language")}'")
        val text = Texts(language)
        val nodes = root.obj("nodes").mapValues { (id, value) -> node(id, value.jsonObject, text) }
        val story =
            Story(
                id = root.string("id"),
                version = root.require("version").jsonPrimitive.int,
                ageBand = root.string("ageBand"),
                languages = listOf(language),
                title = text(root, "title"),
                cover = root.string("cover"),
                start = root.string("start"),
                nodes = nodes,
            )
        return LanguageFile(story, root["translatedFrom"]?.let { root.string("translatedFrom") })
    }

    private fun node(
        id: String,
        o: JsonObject,
        text: Texts,
    ): Node {
        val image = o.string("image")
        return when {
            "ending" in o -> {
                val ending = o.obj("ending")
                val kind =
                    EndingKind.entries.firstOrNull { it.name.equals(ending.string("kind"), ignoreCase = true) }
                        ?: throw StoryFormatException("Node '$id': unknown ending kind '${ending.string("kind")}'")
                Ending(id, text(o, "text"), image, kind, text(ending, "title"))
            }

            "options" in o -> {
                val options = (o.require("options") as? JsonArray ?: throw StoryFormatException("Node '$id': options isn't a list"))
                if (options.size != 2) throw StoryFormatException("Node '$id': a fork needs exactly 2 options, it has ${options.size}")
                Fork(
                    id = id,
                    text = text(o, "text"),
                    image = image,
                    question = text(o, "question"),
                    options =
                        options.map {
                            val option = it.jsonObject
                            Option(text(option, "label"), option.string("image"), option.string("target"))
                        },
                )
            }

            "next" in o -> {
                Page(id, text(o, "text"), image, o.string("next"))
            }

            else -> {
                throw StoryFormatException("Node '$id' is neither a page, a fork nor an ending")
            }
        }
    }

    /** Reads a text of one language's file as a [Text] in that language. */
    private class Texts(
        val language: String,
    ) {
        operator fun invoke(
            o: JsonObject,
            key: String,
        ): Text {
            val value = o.string(key)
            if (value.isBlank()) throw StoryFormatException("'$key' is empty")
            return Text(mapOf(language to value))
        }
    }

    /** [story] with the texts of [translation], a single language with the very same structure, added to its own. */
    private fun merge(
        story: Story,
        translation: Story,
    ): Story {
        val language = translation.languages.single()

        fun mismatch(what: String): Nothing =
            throw StoryFormatException("story/$language.json doesn't have the structure of the story's first language: $what")

        fun frame(s: Story) = listOf(s.id, s.version, s.ageBand, s.cover, s.start, s.nodes.keys.toList())

        if (language in story.languages) mismatch("'$language' twice")
        if (frame(story) != frame(translation)) mismatch("its id, version, age band, cover, start or nodes differ")
        val nodes = story.nodes.mapValues { (id, node) -> merge(node, translation.node(id)) ?: mismatch("node '$id' differs") }
        return story.copy(languages = story.languages + language, title = story.title + translation.title, nodes = nodes)
    }

    /** The two nodes' texts together, or null if they're not the same node in two languages. */
    private fun merge(
        a: Node,
        b: Node,
    ): Node? =
        when {
            a is Page && b is Page && a.copy(text = b.text) == b -> {
                a.copy(text = a.text + b.text)
            }

            a is Fork && b is Fork && a.image == b.image &&
                a.options.map { it.image to it.target } == b.options.map { it.image to it.target } -> {
                a.copy(
                    text = a.text + b.text,
                    question = a.question + b.question,
                    options = a.options.zip(b.options) { x, y -> x.copy(label = x.label + y.label) },
                )
            }

            a is Ending && b is Ending && a.copy(text = b.text, title = b.title) == b -> {
                a.copy(text = a.text + b.text, title = a.title + b.title)
            }

            else -> {
                null
            }
        }

    private fun JsonObject.require(key: String): JsonElement = this[key] ?: throw StoryFormatException("Missing '$key'")

    private fun JsonObject.obj(key: String): JsonObject =
        require(key) as? JsonObject ?: throw StoryFormatException("'$key' isn't an object")

    private fun JsonObject.string(key: String): String =
        (require(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw StoryFormatException("'$key' isn't a string")
}
