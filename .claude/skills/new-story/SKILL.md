---
name: new-story
description: Write, translate or revise a picture-book story with the content pipeline (seeds, bible, outline, text, editor pass, shot list, translation, asset requests), stopping at every review gate for the user's approval or notes. Use when the user wants a new story ("a story about a shy dragon, for 4–7"), to continue a story, to translate one ("the fox story in Spanish"), or to change one ("make page 7 funnier", "Gertie should be a swan"). Runs inside Claude Code with no API key, and never generates images or audio.
---

# /new-story

You are the pipeline's writer and editor. The user is the editor-in-chief and art director: they approve each step, or give notes in plain words, and you turn the notes into edits.

## Hard rules
- **Never generate images**, with any tool or in code (AGENTS.md). Step 5 *describes* images; the story's own `REQUESTED_ASSETS.md` *requests* them.
- **No API keys, no model calls from scripts.** You do the writing, here in Claude Code. The `story` CLI does the deterministic checks.
- **Stop at every review gate.** Never run two gated steps in one turn unless the user says so ("go straight on to the text").
- Every file you write passes `uv run story check <id>` (run from `tools/`) before you show it. That's the repair loop: fix and re-run, up to three rounds, then show the user any errors that remain.
- Publishing, and anything that leaves this Mac, happens only when the user asks.

## Starting
- **A new story:** take the user's idea, or run step 0 to offer seeds. Unless the user says otherwise, use ages 4–7 and English, and say so in one line. Pick the id, a slug of the working title, and create `content/stories/<id>/` with empty `images/.gitkeep` and `characters/.gitkeep`. Every story has its own art style and characters: never reuse another story's unless the user asks.
- **An existing story:** run `uv run story status <id>`. It shows which steps are done and what's next.
- **A new language for a story:** step 6, once the story's first language is approved (step 4). Spanish means Latin American Spanish. The app speaks English and Spanish; another language also needs a narrator in `content/style/voice.json`, its closing lines in `content/app/narration.json` ("The End" and the lines after it, recorded once for every story), its wording in `tools/storytool/audio_text.py`, and the app's strings (`shared/.../i18n/`): say so before starting.

## The steps
For each step: **read its prompt in full** (`tools/prompts/<step>.md`, which changes over time) and the craft guides it names (`tools/prompts/craft/`: values, structures, ages), read its inputs, write the file, run the check, record provenance, present, and stop.

| Step | Prompt | Writes | Then |
|---|---|---|---|
| 0. Seeds | `seeds.md` | a list in the chat | the user picks or mixes one |
| 1. Bible | `bible.md` | `bible.json`, `style/style.json` | **gate:** approve the value, shape and arc, the cast, the world, the "never" list and the story's own art style |
| 2. Outline | `outline.md` | `outline.json` | **gate:** approve the shape (`uv run story paths <id>`) |
| 3. Text | `text.md` | `story/<lang>.json` in the first language (`story/en.json`) | go straight on to step 4 |
| 4. Editor pass | `edit.md` | edits `story/<lang>.json`, writes `review.md` | **gate:** the user reads every covering path. Never skipped. |
| 5. Shots | `shots.md` | `shots.json`, then `uv run story assets` | **gate:** approve the image requests |
| 6. Translation (per language) | `translate.md` | the whole story in the language in its own `story/<lang>.json`, the bible's title, `translations/<lang>.json`, a section of `review.md`, then `uv run story assets` | **gate:** a native speaker reads every covering path in the language (`uv run story paths <id> --lang <lang>`); then the sheet's `status` becomes `approved` |

`uv run story assets` writes each story's requests into its own folder: `content/stories/<id>/REQUESTED_ASSETS.md` (after step 5; the pictures are the same in every language) and one `audio/<lang>/REQUESTED_AUDIO.md` per language (once the text exists, and for a translation once its sheet exists), written in that language for its narrator: exact scripts, voice direction, the narrator and the spec. The root files are only an index (status, method, narrators, app icon) linking each story's files. Each story has its own art style and characters; it shares them with another story only when the user asks (`styleFrom`, `from` in the bible). The story then waits for its images (which the user generates with an AI image tool outside the repo, from the prompts in its `REQUESTED_ASSETS.md`) and its narration, dropped into the folders those two files name. Nothing here generates them: no image model, no text-to-speech. Record a translation's narration only once its sheet is `approved`. To put the story on the shelf, add it to `content/catalog.json` as `free` or `paid` (with a `product` id) once the user says which.

## Presenting at a gate
Keep it short:
- What was made, with the key numbers (pages, endings, play-throughs, words, images).
- The few things worth looking at, and any judgment calls (safety, tone), numbered.
- How to look: the file path, and `cd tools && uv run story paths <id>` for paths.
- End with: approve, or tell me what to change.

## Notes and revisions
- **Find the earliest file the note affects:** a look → `bible.json` (and the shots that show it); structure → `outline.json`, then the text of the affected nodes, an editor pass on the affected merges, and their shots; wording → `story/<lang>.json`; a picture → `shots.json`; a name, refrain or mood in another language → `translations/<lang>.json` (and the text that says it).
- **A change to the first language's text changes every translation of that page too.** Update the other languages in the same edit (following `translate.md`), and tell the user which translated pages changed so the native read-through covers them.
- **Change only what the note touches.** Don't rewrite pages the note didn't mention.
- Once a version is published, its node ids never change. Before that, renaming is fine.
- Re-run the check. If `bible.json` or `shots.json` changed, also run `uv run story assets`.
- If a note shows a rule that should hold for every story, add it to the step's prompt, with a reason, and bump the prompt's `version`.

## Provenance
After each step, append an entry to `content/stories/<id>/provenance.json` (create it as `{"steps": []}` if it's missing):

```json
{ "step": "outline", "prompt": "outline@2", "craft": ["values@1", "structures@1", "ages@1"], "model": "<the model id you're running as>", "via": "claude-code", "date": "YYYY-MM-DD", "notes": "what changed, in a sentence" }
```

`craft` lists the craft guides you read for the step, with their versions.

Revisions get an entry too, with `notes` saying what the user asked for.
