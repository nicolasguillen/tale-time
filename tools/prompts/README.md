# Prompts

The instructions for each writing step of the pipeline. They're run **in Claude Code** by the `/new-story` skill (`.claude/skills/new-story/`), so no API key is involved. Claude reads the prompt, writes the step's file, runs `uv run story check <id>` and fixes every error before showing the result to the user.

| Step | Prompt | Writes | Format | Review gate |
|---|---|---|---|---|
| 0 | [seeds.md](seeds.md) | nothing (a list in the chat) | | the user picks or mixes one |
| 1 | [bible.md](bible.md) | `bible.json` | `schema/bible.schema.json` | the user approves |
| 2 | [outline.md](outline.md) | `outline.json` | `schema/outline.schema.json` | the user approves the map (`story paths`) |
| 3 | [text.md](text.md) | `story/<lang>.json` in the first language | `schema/story.schema.json` | (straight on to step 4) |
| 4 | [edit.md](edit.md) | edits `story/<lang>.json`, writes `review.md` | | **the user reads every covering path** |
| 5 | [shots.md](shots.md) | `shots.json` → the story's `REQUESTED_ASSETS.md` | `schema/shots.schema.json` | the user approves the requests |
| 6 | [translate.md](translate.md) | the whole story in the language, `story/<lang>.json`, and `translations/<lang>.json` → the story's `audio/<lang>/REQUESTED_AUDIO.md` | `schema/translation.schema.json` | **a native speaker reads every covering path in the language** |

Images and audio are never generated, in any step (AGENTS.md).

## Craft guides

Reference guides the step prompts point to. They hold the storytelling knowledge, adapted from the kids-book-writer skill ([jamesrochabrun/skills](https://github.com/jamesrochabrun/skills/tree/main/skills/kids-book-writer), MIT) for branching, read-aloud stories, without its rhyming, phonics and song parts.

| Guide | What it holds | Read by |
|---|---|---|
| [craft/values.md](craft/values.md) | The values (the ids of `craft.value`), showing without preaching, fork pairs that fit each value, the values for each band, pitfalls, representation | seeds, bible, outline, text, edit, translate |
| [craft/structures.md](craft/structures.md) | The arc on every path, pacing, page turns, the shapes (`craft.structure`) and arcs (`craft.arc`) and how each one branches, endings, hooks, mistakes | seeds, bible, outline, text, edit, translate |
| [craft/ages.md](craft/ages.md) | What each band can follow and feel: words, sentence length, feelings, dialogue, themes, what to avoid | every step |

## Rules for editing prompts

- Each prompt has a `version` line. Bump it when you change what the prompt asks for, and the skill records `<step>@<version>` in the story's `provenance.json`.
- The craft guides have a `version` line too. Bump it when you change a guide. The skill records the guides it read in the entry's `craft` list (`["values@1", "ages@1"]`).
- The worked example for every step is `content/stories/fox-and-the-lantern/`. Keep it passing `story check`.
- When a review turns up a problem a rule could have prevented, add the rule to the prompt, with a short reason.
