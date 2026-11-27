# Story format

The contract between the content pipeline (`tools/`) and the app. The single definition is [`schema/story.schema.json`](../schema/story.schema.json). This page explains it. The pipeline's own files have schemas too (`schema/bible.schema.json`, `outline.schema.json`, `shots.schema.json`, `translation.schema.json`, `voice.schema.json`, `app-narration.schema.json`); the prompts in `tools/prompts/` explain them. Rules that JSON Schema can't express are checked by `story check` (see below).

## Folders

Source content, in git:

```
content/
  catalog.json                       the shelf: which stories, in what order, free or paid (below)
  style/voice.json                   the narrators, one per language, each written in its language (rendered into that
                                     language's REQUESTED_AUDIO.md files)
  app/assets.json, app/style.json    the app icon request and the app's own look; the delivered icon files go next to them
  app/narration.json                 the lines the narrator says at the end of every story, per language (below)
  app/audio/<lang>/REQUESTED_AUDIO.md  generated: those lines still needed in that language, written in it
  app/audio/<lang>/<clip>.wav|flac|m4a  delivered: those lines (none is generated)
  placeholders/                      static placeholders: page.svg (4:3), card.svg (1:1), cover.svg (3:4), sheet.svg (3:2)
  stories/<story-id>/
    bible.json                       step 1: premise, value, shape and arc, cast (with looks), places, world rules, "never" list, palette
    style/style.json                 step 1: the story's own art style (schema/style.schema.json), proposed until approved
    style/style-anchor.png           the delivered style anchor, made first
    characters/<id>.png              the story's delivered reference sheets (characters and props)
    outline.json                     step 2: one-line beats, forks, endings, visual state per page
    story/<lang>.json                step 3: the published format (below), the whole story in one language, one
                                     file per language (step 6 adds the others). Same structure as the outline
    review.md                        step 4: editor notes and the read-through checklist
    shots.json                       step 5: what every image shows, the source of the story's REQUESTED_ASSETS.md
    translations/<lang>.json         step 6: one per language after the first: names, refrains, moods in that language
    REQUESTED_ASSETS.md              generated: every image the story still needs, with its prompt (the same in every
                                     language)
    provenance.json                  which prompt version and model made each step, and when
    images/<image-id>.png|webp|jpg   delivered images (missing ones use a placeholder)
    audio/<lang>/REQUESTED_AUDIO.md  generated: every clip still needed in that language, with its script, written in
                                     that language for its narrator
    audio/<lang>/<clip>.wav|flac|m4a delivered narration (none is generated)
```

**Every story has its own art style and characters.** A story shares them with another only when its bible declares it: `styleFrom: "<story-id>"` uses that story's `style/` (style and anchor), and `from: "<story-id>"` on a cast member or prop uses that story's character, whose look must match word for word and whose sheet is in that story's `characters/` (`story check` enforces both).

What the app downloads is built from these by `story package` (see "Published stories" below).

## `story/<lang>.json`

Each language of a story is its own file, and each file is the **whole story in that language**: `story/en.json` in English, `story/es.json` in Spanish. Every language has the same structure (the same nodes in the same order, with the same ids, kinds, links, images and ending kinds); only the words differ.

| Field | |
|---|---|
| `id` | Slug (`a-z`, `0-9`, `-`), the same as the folder name. The same in every language. |
| `version` | Integer, the same in every language. Bump it when the story's structure changes. The app doesn't need it to see an update: any changed file changes the story's manifest (below). |
| `ageBand` | `3-5`, `4-7` or `7-10`. Sets the budgets below. |
| `language` | The language of this file, the same as its file name. |
| `translatedFrom` | Only in a translation: the story's first language, the one it was written in. Exactly one file, the first language's, has none. |
| `title` | The title. |
| `cover` | Image id of the cover. |
| `start` | Id of the first node. |
| `nodes` | Node id → node. |

Every text is a plain string in the file's language.

### Nodes

Each node is exactly one of three kinds:

- **page:** `text`, `image`, `next`.
- **fork:** `text`, `image`, `question`, and `options`, an array of exactly 2 items, each `{ label, image, target }`. Option A comes first.
- **ending:** `text`, `image`, `ending: { kind, title }`, where `kind` is `best`, `happy`, `cozy` or `silly`. The ending's illustration doubles as its badge (a circle cropped from the center).

**Node ids never change once a version is published.** Progress, endings found and the story map are keyed by them. A rewrite that changes the structure gets new ids.

### Images

Image ids are slugs. By convention:

| Image | Id | Aspect | Delivered at least | Packaged as |
|---|---|---|---|---|
| Page or ending | the node id | 4:3 | 1600×1200 | `images/<id>.webp`, about 1600×1200 |
| Choice card | `<fork id>-a`, `<fork id>-b` | 1:1 | 768×768 | `images/<id>.webp`, about 768×768 |
| Cover | `cover` | 3:4 | 1200×1600 | `images/cover.webp` |

No image contains text. The app draws every title and label. While an image isn't delivered, the app and the previewer show the matching static placeholder from `content/placeholders/`. **Nothing in the repo generates images**: they're generated with an AI image tool outside it, from the prompts (see [AGENTS.md](../AGENTS.md)). Every image is requested in the story's own `REQUESTED_ASSETS.md`, which `story assets` builds from `shots.json`; the shared [REQUESTED_ASSETS.md](../REQUESTED_ASSETS.md) at the root has the method and links each story's file. The story's file holds its own art style and style anchor too.

### Narration clips

The same clip ids in every language. Each language is requested in its own file, inside the folder its clips go into: `audio/<lang>/REQUESTED_AUDIO.md`, written in that language for that language's narrator (the English one in English, the Spanish one in Latin American Spanish). Each file is complete on its own: the narrator, how to deliver, the technical spec, who's in the story, and every clip with its exact script and direction. The [REQUESTED_AUDIO.md](../REQUESTED_AUDIO.md) at the root is the index. Clips are delivered as `audio/<lang>/<clip>.wav` (or `.flac` / `.m4a`) and packaged as `.m4a` (mono AAC). **Nothing in this project generates audio.**

| Clip | Read aloud |
|---|---|
| `title` | the story title, on the cover |
| `<node>` | the page text |
| `<fork>-q` | the fork's question |
| `<fork>-a`, `<fork>-b` | the option labels |

The app reads aloud every text whose clip is delivered: a page's text as its words appear, a fork's question and then its two labels as the cards come out (the card being read lifts a little), and the title on the cover. An ending's title (`ending.title`) has no clip: the ending screen doesn't show or say it, and closes instead with "The End" and a closing line, the same for every story (below). The title stays in the story for screen readers and the read-through. An `<ending>-end` clip delivered before that is left alone and ignored: it isn't checked, requested or packaged. A text without its clip shows without narration, at the narrator's pace. So a language can be narrated a few clips at a time on the `draft` channel.

**The end of every story.** A few lines are the same for every story, so they're recorded once per language, not per story, by the same narrator. Their scripts and directions, written in each language, are in `content/app/narration.json` ([schema](../schema/app-narration.schema.json)): one entry per language, one for every narrator in `voice.json`, each with exactly these three clips, `{ "script", "direction" }`. They're requested in `content/app/audio/<lang>/REQUESTED_AUDIO.md`, written in that language, delivered next to it as `<clip>.wav` (or `.flac` / `.m4a`), and published with the catalog (below). The app plays them by these ids:

| Clip | Played |
|---|---|
| `the-end` | as "The End" rises on the ending screen, after the story's last page |
| `more-endings` | after "The End" and the ending badges, while the story still has endings the child hasn't found: tap a "?" badge to find another, and there are more stories to discover |
| `all-endings` | instead of `more-endings`, once every ending of the story is found |

**Word timings.** A narrated page's words appear as the narrator says them. `story package` measures when that is from each page's delivered clip and its text (`tools/storytool/timings.py`), with ffmpeg and no model: it finds the pauses in the clip, matches them in order to the places in the text where a narrator stops (sentence ends first, then commas, colons, dashes and quotes, rarely mid-phrase) so each stretch of speech lasts about as long as its syllables take at the clip's own pace, and spreads the words of each stretch by their syllables. That lands on the voice at every pause and is a close guess in between, which is what the reveal needs. Nothing is written into `audio/<lang>/`: the timings are made at packaging, cached by the clip's and the text's contents, and shipped next to the clips (below). A clip recorded for other words than the text's (the text changed after recording) falls back to the narrator's pace.

The narrators are in `content/style/voice.json` ([schema](../schema/voice.schema.json)): one per language (`narrators.<lang>`), each written in its own language, with its voice, accent, delivery, the direction for each kind of clip and its status (`proposed` until the user approves it). The pace, in words per minute per age band, is shared by every language, and the app paces a story without narration at the same speed.

Names in the bible can carry a `pronounce` hint (`"MEE-ah"`), which the English requests pass on to the narrator. A translation has its own, respelled for its narrator, in its translation sheet.

## Languages

A story is written in one language first (the file without `translatedFrom`, English so far) and translated into the others at step 6 ([`tools/prompts/translate.md`](../tools/prompts/translate.md)), each into its own `story/<lang>.json` with `"translatedFrom"` naming the first. The pictures stay the same, and node ids are the same in every language, so progress and endings found carry over when a reader switches. The app reads every file of a story into one `Story` (`StoryParser`) and refuses a translation whose structure differs from the first language's.

**Spanish (`es`) is Latin American Spanish:** tú (never vos or vosotros), and words that read the same from Mexico to Argentina.

Each language after the first has a **translation sheet**, `translations/<lang>.json` ([schema](../schema/translation.schema.json)). It keeps the translation consistent and gives that language's narrator what the bible and `shots.json` give the first one:

| Field | |
|---|---|
| `language`, `from` | This language, and the story's first language it's translated from. |
| `status` | `draft` until a native speaker has read every path in the language (`story paths <id> --lang <lang>`), then `approved`. Its `REQUESTED_AUDIO.md` asks not to record a draft. |
| `pitch`, `tone` | The bible's, in this language. |
| `cast` | Every id in the bible's cast, extras and props: its `name` as the text says it, and its `personality` and `refrain` (word for word as the text says it) when the bible has them, plus an optional `pronounce` hint for this language's narrator. |
| `glossary` | Places and recurring words, so every page says them the same way. |
| `moods` | Every page and ending's mood from `shots.json`, in this language. |

The bible's `title` holds every language too. A reader downloads a story once, in every language it's published in; it's read in the app's language first, and the cog on its cover switches to another of its languages, offline.

## `catalog.json`

`content/catalog.json` ([schema](../schema/catalog.schema.json)) is the bookshelf, hand-edited:

```json
{
  "stories": [
    { "id": "fox-and-the-lantern", "access": "free", "approved": ["en", "es"] },
    { "id": "the-shy-dragon", "access": "paid", "product": "story.shy_dragon" }
  ]
}
```

| Field | |
|---|---|
| `id` | A story folder in `content/stories/`, with its text (`story/<lang>.json`). Order = shelf order. `app` is reserved (the published lines at the end of every story are in `app/`). |
| `access` | `free`, or `paid`: a one-time purchase, unlocked behind the parental gate. |
| `product` | Paid only: the store product id, the same on the App Store and Google Play. Lowercase letters, digits, `_` and `.` (Google Play allows no `-`). |
| `approved` | The languages approved for the production channel (release builds), e.g. `["es"]`: the story is published there in these and no other, the first language included only if it's listed. A translation listed must also be approved by a native speaker (`"status": "approved"` in `translations/<lang>.json`). Without it the story isn't published to production. The app reads a story in the app's language when it's there, else in the first one published. The draft channel has every language. |

`story check` checks that every entry is a written story, ids and products are unique (and no id is `app`), each approved language is one the story is written in (and a translation a native speaker approved), and warns if the shelf doesn't open with a free story. No story ships inside the app: `story package` turns this file into the published catalog (below), and the app's build keeps only a copy of the titles (`files/shelf.json`) for a first launch offline.

## Published stories

What the app downloads, built by `uv run story package [draft] [production]` into `content/.build/stories/<channel>/` and uploaded by `uv run story publish <channel>` to the same folder on the content host (Firebase Storage, bucket `tale-time-b537d.firebasestorage.app`). Each channel's folder holds:

```
stories/<channel>/catalog.json                 the shelf (schema/published-catalog.schema.json), fetched at every launch
stories/<channel>/app/<lang>-<clip>-<hash>.m4a  the lines at the end of every story, listed in the catalog's `narration`
stories/<channel>/<id>/manifest-<hash>.json    every file of the story (schema/manifest.schema.json)
stories/<channel>/<id>/<name>-<hash>.<ext>     the files: pictures (.webp), each language's text (story-<lang>.json),
                                               clips (<lang>-<clip>.m4a) and word timings (timings-<lang>.json)
```

Every file is named after its contents (the first 10 hex characters of its SHA-256), so a changed picture, clip or text gets a new name, the manifest that lists it changes, and so does the manifest's name in the catalog. The app compares that name with the one it has: a new one means the story changed, and it downloads only the files it doesn't have yet. Nothing on the host is overwritten, so every file but the catalog can be cached forever. Production never deletes a file either; `story publish draft` deletes the draft files no story uses any more.

**Catalog:** `format` (1), `narration`, and `stories`, one catalog entry per story. `narration` holds the lines at the end of every story: language → clip id → `{ file, bytes, sha256 }`, e.g. `{"es": {"the-end": {"file": "es-the-end-0123456789.m4a", …}, "more-endings": {…}, "all-endings": {…}}}`. A language is there once it has a clip (on production, all three); the key is left out while no language has one. Their files are in `app/`, next to the stories' folders, so no story can be called `app`.

**Catalog entry:** `id`, `access`, `product`, `ageBand`, `endings` (how many, shown on its cover before it's downloaded), `title` (every published language, the first first), `languages` (per language: `bytes`, the story in that language alone, and `narration`), `cover` and `manifest` (each `{ file, bytes, sha256 }`).

**Manifest:** `images` (image id → file, the same in every language), `languages` (per language: `story`, its text, `audio`, clip id → file, and `timings`, once a page's clip is there), and `files` (every file named: `bytes` and `sha256`). The app downloads every file it lists (the pictures, and every language's text, clips and timings), so a story can switch language offline, and checks every file's size and SHA-256.

**Timings** (`timings-<lang>-<hash>.json`): `{"format": 1, "clips": {"<page id>": [[start, end], …]}}`, the start and end of each word of the page's text in its clip, in milliseconds. A word is a run of non-space characters (spaces, tabs and line breaks), in order, exactly as the app splits the text, so the n-th pair is the n-th word on screen.

**Conversions** (the only changes packaging makes): pictures to WebP, scaled down to fit 900×1200 (cover), 1600×1200 (pages and endings) or 768×768 (cards), never up; clips to mono AAC at 64 kbps, at −16 LUFS integrated and −1.5 dBTP. Neither moves nor trims the sound, so the timings measured on the delivered clip hold for the converted one. The same source always gives the same file. Converted files and timings are cached in `content/.build/cache/`.

| Channel | What's in it | Who reads it |
|---|---|---|
| `draft` | Every story on the shelf as it is: every language (drafts too), the pictures and clips delivered so far. A missing picture shows the placeholder; a page without its clip reads without narration. The lines at the end of every story delivered so far, in every language. | Debug builds, from `stories/draft/`, after `uv run story publish draft` (only when the user asks). |
| `production` | Only stories that pass `story check --strict` (every picture delivered), in their first language and each translation whose sheet is `approved`. A language's narration ships once all its clips are delivered; until then that language reads without narration. The lines at the end of every story ship per language once all three are delivered. | Release builds, from `stories/production/`, after `uv run story publish production` (only when the user asks). |

## Budgets per age band

"Pages" counts every node. A play-through runs from the start to an ending.

| Band | Words per page | Pages per play-through | Forks per play-through | Pages in total | Endings | Images at most |
|---|---|---|---|---|---|---|
| 3–5 | 15–40 | 6–8 | 2–3 | 10–16 | 3 | 30 |
| 4–7 | 40–80 | 8–12 | 3–5 | 18–28 | 4–6 | 50 |
| 7–10 | 80–150 | 12–18 | 5–7 | 30–45 | 6–8 | 80 |

These live in `tools/storytool/budgets.py`. They hold in every language: a translation tightens its wording rather than going over.

## `story check`

From `tools/`: `uv run story check [<id> …] [--strict] [-v]`

It checks every pipeline file present in the story folder, so it works from step 1 on and is the repair loop of each writing step. It reports errors, which fail the check, warnings, and requests: assets not delivered yet, which block only with `--strict`, the publishing mode.

- `catalog.json`, `app/assets.json`, `app/narration.json` and `style/voice.json` match their schemas (above), when `check` runs without story ids. Every narrator has its lines in `app/narration.json` and every language there has a narrator; those lines not delivered yet are requests, and warnings in `--strict` mode.
- `bible.json` matches its schema, ids are unique, and there's one hero.
- `outline.json` matches its schema and passes the graph and structure-budget rules below.
- Every `story/<lang>.json` matches the schema and says its own `language`. Exactly one, the first language, has no `translatedFrom`, and every other is translated from it.
- The first language's file has exactly the outline's nodes, kinds, links and ending kinds, and every other language has exactly the first language's structure: id, version, age band, cover, start, and the same nodes in the same order with the same kinds, images, links, cards and ending kinds.
- The start exists, there are no dangling targets, no cycles, and every node is reachable from the start (so every ending is reachable and every path ends).
- Both options of a fork lead to different nodes.
- No text is empty, and the budgets hold in every language.
- Every language after the first has its `translations/<lang>.json`, which names everyone in the bible, keeps every refrain and personality, and has a mood for every page and ending that has one. Every language has a narrator in `voice.json`.
- The age band's budgets above hold.
- `shots.json` describes every image the story uses, once each.
- Every image is delivered: missing ones are requests, and errors in `--strict` mode (publishing). Narration missing in a language is a request, and in `--strict` mode a warning: that language is published without narration until all its clips are delivered.

`uv run story paths <id>` prints a small set of play-throughs that together visit every page and take every choice, for the read-through. Add `--all` for every play-through, and `--lang es` to read them in another language.
