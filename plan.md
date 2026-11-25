# Tale Time — Development Plan

> Living document. Written 2026-11-22, before any code existed. Expect it to change as decisions get made.
>
> **Updated 2026-11-25: no local server; both channels on Firebase.**
> - **`story serve` is gone.** Debug builds download the `draft` channel from Firebase Storage (`stories/draft/`), release builds the `production` channel (`stories/production/`), through the same download code. `story publish draft` and `story publish production` upload them (only when you ask); draft also deletes the files no story uses any more, production never deletes. A debug build now works on any device, anywhere (a real iPhone, TestFlight, a native speaker's phone), at the cost of a publish before every check. The draft folder is readable by anyone who guesses its paths, which is accepted for drafts. No Firestore: `catalog.json` already says which version of each story is current. Both channels fit easily in Firebase's free tier (a story is about 12 MB with one narrated language).
>
> **Updated 2026-11-24 (narration):**
> - **The app reads aloud** every text whose clip is delivered: a page as its words appear, a fork's question and then each label (its card lifts), an ending's title, the title on the cover. Narration pauses in the background and stops when the page is left. A text without its clip still reads at the narrator's pace, so a language can be narrated a few clips at a time on the draft channel. `platform/NarrationPlayer`: MediaPlayer on Android, AVAudioPlayer on iOS (spoken-audio session, plays with the ring switch on silent).
> - **Word timings without forced alignment.** `story package` measures them from each page's clip with ffmpeg alone (`tools/storytool/timings.py`): the pauses in the clip are matched to the text's sentence and clause breaks so each stretch of speech fits its syllables, and the words of each stretch are spread by syllables. Right at every pause, a close guess in between. That's enough for a reveal where each word fades in over 0.3 s, needs no model, no download and no network, and replaces MFA / WhisperX and a separate `story align` step (§5.2 step 8, §5.4, §5.6). The timings ship as `timings-<lang>.json` next to the clips (docs/story-format.md). If the reveal ever drifts noticeably inside long sentences, forced alignment can still replace the measuring behind the same file.
>
> **Updated 2026-11-24 (later): distribution.**
> - **No story ships inside the app.** The shelf shows every story (title and cover, from the catalog); tapping one offers its languages, the app's own first, and downloads that language: text, pictures, narration. It stays on the device for offline reading. Every time the app opens online it fetches the catalog and brings downloaded stories up to date, downloading only the files that changed. This replaces "two stories ship inside the app" and "one zip per story" in §7.3.
> - **Built:** `story package` (dev and release channels, WebP and AAC, content-named files, manifests, catalog), `story serve` (debug builds download from the Mac; replaced 2026-11-25 by the draft channel on Firebase, above), `story publish` (gcloud upload, not run yet), and `HostedStoryLibrary` in the app. Format in docs/story-format.md, "Published stories".
>
> **Updated 2026-11-24:**
> - **Two languages: English and Latin American Spanish (`es`).** Stories are written in English first and translated at the `/new-story` skill's step 6 (`tools/prompts/translate.md`), with a translation sheet per language (`translations/<lang>.json`: names, refrains, moods). Both stories have a Spanish draft waiting for a native read-through (their `review.md`). This answers open question 2.
> - **Narration is requested once per language, inside the folder its clips go into:** `content/stories/<id>/audio/<lang>/REQUESTED_AUDIO.md`, written in that language for that language's narrator. `content/style/voice.json` has one narrator per language. The root `REQUESTED_AUDIO.md` is the index.
> - **The app speaks both** (its own words in `i18n/`, the stories' texts), picked in the parents area, the device's language until then (§3.1).
>
> **Updated 2026-11-23 (later):**
> - **The app is called Tale Time.** Package and app id `com.nicolasguillen.taletime`, Firebase project `tale-time-b537d`.
> - **The images are generated with AI, by you, outside the repo.** Each request in [REQUESTED_ASSETS.md](REQUESTED_ASSETS.md) is a ready-to-paste prompt for an AI image tool that takes reference images (ChatGPT or the GPT Image API), with the images to attach: a style anchor, the reference sheets and the place's first picture. You run it and drop the result into the folder named. The repo, the pipeline and the coding agents still never call an image model. This replaces "no images are generated, by AI or otherwise" below.
> - **No audio is generated either: no text-to-speech in the repo or the pipeline.** Every narration clip is requested in [REQUESTED_AUDIO.md](REQUESTED_AUDIO.md) with its exact script, voice direction and technical spec, and delivered by hand. This replaces the TTS parts of §5.1 step 8, §5.4 and the voice bake-off.
> - **Free and paid stories.** `content/catalog.json` is the shelf: each story is free or a one-time purchase (§7.5). The first story, *The Fox and the Lantern*, is free and ships inside the app.
> - **No new stories for now:** the app comes first (Phase 3). The app scaffold, the `story` engine, the shelf, cover, reader, endings, progress and the parental gate exist (§11).
>
> **Updated 2026-11-23:**
> - ~~**No images are generated, by AI or otherwise.**~~ *Superseded above: generated with AI outside the repo.* Every image is requested in [REQUESTED_ASSETS.md](REQUESTED_ASSETS.md) with its size, style and description, and delivered by hand. Static placeholders stand in until then. This replaces the image-model steps below (§5.1 steps 6–7, §5.4, §6).
> - Provisional defaults: ages 4–7, English at launch, stateless stories.
> - The Phase 0 story spike is written: `content/stories/fox-and-the-lantern/`.
> - **No API key.** The writing steps run inside Claude Code, with the `/new-story` skill following the prompts in `tools/prompts/`. The `story` CLI makes no model calls (§5.6).

## 1. Vision

A picture-book **choose-your-own-adventure** app for kids.

- The child picks a story from a **catalog** (a bookshelf).
- Each page shows an **illustration** and a short **paragraph**, which the app **reads aloud** while the words light up.
- At a fork, two **choice cards** appear, **A or B**. Each has its own small illustration and a short label, and the label is read aloud too. The child taps one and the story goes on down that path.
- Every story has several **endings**. Finding them all is the reason to play again.

Platforms: Android + iOS, phones and **tablets** (most kids will read on a tablet). Same stack as Nim (§7).

### Who makes the stories

The stories are **written with AI tools and curated by you**. You're the editor and art director, not the writer. You give an idea in a sentence. The pipeline writes the story bible, the branching outline, the text and the shot list, and stops after each step so you can approve it or ask for changes.

The **pictures and the narration are never generated by the pipeline**. For each picture, the pipeline writes a request (size, style, description) into the story's own `REQUESTED_ASSETS.md`; for each narration clip, a request (exact script, voice direction) into the story's `audio/<lang>/REQUESTED_AUDIO.md`, one per language, written in that language for its narrator. The image requests sit in the story's folder, next to `images/`, and the narration requests in the language folders they ask clips for; the files at the root are indexes (status, method, narrators, app icon) that link each story's files. Each story's art style and characters are its own. They're made outside the repo and dropped in. Until they arrive, the app shows static placeholders for pictures and plays a story without narration.

**The pipeline is the real product.** The app that plays the stories is simple by comparison.

### Principles

1. **No runtime AI in the app.** Every story is generated ahead of time, reviewed by a person, then published. Nothing a child sees was written a second ago (§2 explains why).
2. **No failure.** No "game over". The worst a choice can lead to is a funny or cozy ending, and "back" is always one tap away.
3. **No reading required to play.** Everything is narrated, including the choices. Navigation uses icons and pictures.
4. **No text inside illustrations.** Image models still garble lettering, and text-free pictures work in every language.
5. **Offline once downloaded.** Bedtime, car, plane.
6. **Collect nothing.** No accounts, no ads, no analytics SDKs. Progress stays on the device (§9).
7. **Short.** One play-through takes 5–10 minutes.

---

## 2. Approaches considered

| Approach | Verdict |
|---|---|
| **Pre-generated, human-reviewed stories**, shipped as downloadable content packs | **Chosen.** Safe (you read every path before a child does), cheap (you pay once per story, not per play), fast (no child staring at a spinner while an image renders), works offline, and fits the store rules for kids' apps. |
| Stories generated live while the child plays | Rejected for v1. Nobody reviews the text or pictures before a child sees them, an image takes seconds to generate, every play costs money, and it needs a server holding API keys. |
| Hybrid: pre-generated stories plus live personalization (the child's name in the text) | Maybe later. Pre-recorded narration can't say the name, and no speech is synthesized (not even on the device), so it would need a hero without a name in the narration. See Open questions. |
| An existing interactive-fiction format (ink, Twine) as the app's format | No. Our stories are small graphs, and ink would need a Kotlin runtime. Exporting the outline to Twine's text format (Twee), so you can see and hand-edit the graph in Twine's visual editor, is a cheap optional extra. |
| A native app per platform, Flutter, or a web app | No. Nim's Kotlin Multiplatform + Compose Multiplatform setup already works for you (builds, releases, store art), and most of it can be reused (§7). |

---

## 3. The experience

### 3.1 Screens

```
Bookshelf ──▶ Story cover ──▶ Reader ──▶ Ending
                (Read / Continue,          (The End, ending badge,
                 endings 2/5, story map)    Try another path / Back to shelf)

Parents area (behind a parental gate): language (English or Latin American Spanish), narration, downloads, purchases,
                                        progress reset, about
```

- **Bookshelf:** the story covers. Downloaded stories are bright, the others carry a small download badge. A paid story that isn't bought yet carries a lock.
- **Story cover:** cover art, title read aloud, **Read** (or **Continue**), the endings found so far as small badges, and the **story map** (§3.3). A locked story shows "Ask a grown-up" instead of Read: the parental gate, then the unlock sheet (price, buy, restore).
- **Reader:** one page at a time (§3.2).
- **Ending:** the ending's illustration, "The End", its badge flying into the collection, then **Try another path** (back to the last fork) or **Back to the shelf**.
- **Parents area:** behind a parental gate (§9).

### 3.2 A page

1. The illustration fills the top of the screen (phone, portrait) or most of it (tablet, landscape), with a slow pan and zoom so it feels alive. It always takes more room than the text: it's shown as large as its middle 80% allows (where the main action is), and the text sits in a window a few lines tall.
2. The narration starts and the paragraph appears **word by word as it's read**, below or beside the picture, which helps early readers follow along. Without narration, the words come at the narrator's pace for the age band (`voice.json`), with a breath after each sentence. When the words reach the bottom of the window, the text slides up and the oldest line fades away. Tapping a word replays it (later).
3. On a **plain page**, a big "next" arrow pulses once the last word is there. In auto mode the page turns by itself.
4. On a **fork**, the narrator asks the question ("Should Mia follow the fox… or cross the stepping stones?") and the two **choice cards** slide in. Each card is an illustration plus a short label. **One tap on a card picks it** (decided 2026-11-23: a second tap to confirm wasn't child friendly).
5. A page-turn animation and sound, then the next page.

**Back and next arrows** sit at the bottom corners like a book's: back goes one page back, and back across a fork opens its cards again. A sunny **home** button in the top corner goes back to the shelf. No punishment, no timers.

### 3.3 Replay

- **Endings collection** per story: "You found 3 of 5 endings". Each ending is a badge with its own small picture.
- **Story map:** the story's graph drawn as a winding path of page thumbnails. Visited pages show their picture, unvisited ones a "?" silhouette, so kids can see where they haven't been yet. Tapping a visited fork jumps back to it.
- Some stories have a **best ending** that needs a couple of the right choices (kind, brave, curious), so there's something to aim for.

### 3.4 Reading modes and comfort

- **Read to me** (default): narration on, word highlight on.
- **I read myself:** narration off, tap any word to hear it.
- Text size, and an early-reader font option (for example **Andika** from SIL, which is designed for beginning readers).
- **Bedtime mode** (later): warmer and dimmer colors, softer music, no page-turn sounds.
- Big touch targets, one finger is enough, nothing depends on reading.
- Phones: portrait. Tablets: both orientations, with landscape as the main layout.

---

## 4. Story format (the contract between the pipeline and the app)

### 4.1 Shape: a small graph, not a tree

A pure binary tree explodes. Six forks in a row means 63 forks and 64 endings: 64 times the writing, illustrations and narration of a single play-through. Instead, stories use **branch and bottleneck**, a pattern from interactive-fiction design (see Sam Kabo Ashwell's essay *Standard Patterns in Choice-Based Games*). Paths split, **merge back** at key moments, and only split for good near the end.

```
start ─▶ p1 ─▶ p2 ─▶ FORK ─┬─▶ p3a ─▶ p4a ─▶ FORK ─┬─▶ ENDING (silly)
                           │                       └─▶ p5 ◀── paths merge here
                           └─▶ p3b ─▶ p4b ────────────▶ p5
                                                       │
                                                       p6 ─▶ FORK ─┬─▶ ENDING (best)
                                                                   └─▶ p7 ─▶ ENDING (cozy)
```

Node kinds:
- **page:** text, illustration, one `next`.
- **fork:** a page that ends with a question and **exactly two** options (A and B). Each option has a short label, a choice-card illustration and a target node.
- **ending:** a page with an ending kind (`best`, `happy`, `cozy`, `silly`) and a title for the collection.

### 4.2 Size budget by age band

| Age band | Words per page | Pages per play-through | Forks per play-through | Pages in total | Endings |
|---|---|---|---|---|---|
| 3–5 | 15–40 | 6–8 | 2–3 | 10–16 | 3 |
| **4–7** (proposed default) | 40–80 | 8–12 | 3–5 | 18–28 | 4–6 |
| 7–10 | 80–150 | 12–18 | 5–7 | 30–45 | 6–8 |

A 4–7 story then needs about **25 page illustrations + 20 choice cards + 1 cover ≈ 45 images**, and about **45 narration clips per language** (pages, fork questions, option labels).

### 4.3 Merges and state

A page where paths merge must make sense **whatever path led there**, in the words *and* in the picture. If Mia picks up an umbrella on one path, the merged page can't mention or show it unless she has it on every path.

v1 stories are **stateless**, and the pipeline checks every merge page against every path that leads to it (§5.2, step 4). Simple **flags** ("has the key", "made friends with the fox") that change a line or unlock an option can come later. They multiply the review work, so they wait.

### 4.4 Files

A published story is a folder:

```
stories/<story-id>/<version>/
  story/<lang>.json           the whole story in one language (text, graph, endings, asset references), one per language
  images/<node>.webp          page illustrations (4:3, about 1600×1200)
  images/<node>-a.webp, -b    choice cards (1:1, about 768×768)
  images/cover.webp
  audio/<lang>/<clip>.m4a     one clip per page, fork question and option label
  timings-<lang>.json         start and end time of every word of each narrated page, for the reveal (measured by `story package`)
```

The app also reads one `catalog.json` that lists every story: id, version, title per language, age band, minutes, languages, download size, cover thumbnail and checksum.

`story/es.json` sketch (the first language's `story/en.json` is the same without `translatedFrom`):

```json
{
  "id": "fox-and-the-lantern",
  "version": 3,
  "ageBand": "4-7",
  "language": "es",
  "translatedFrom": "en",
  "title": "El zorro y el farolito",
  "cover": "cover",
  "start": "p01-meadow",
  "nodes": {
    "p01-meadow": {
      "text": "Mia encontró un farolito entre el pasto alto. Estaba tibio, como un gato dormilón.",
      "image": "p01-meadow",
      "next": "p02-glow"
    },
    "p04-river": {
      "text": "…",
      "image": "p04-river",
      "question": "¿Mia sigue al zorro o cruza por las piedras?",
      "options": [
        { "label": "Seguir al zorro", "image": "p04-river-a", "target": "p05a-burrow" },
        { "label": "Cruzar las piedras", "image": "p04-river-b", "target": "p05b-island" }
      ]
    },
    "e2-big-splash": {
      "text": "…",
      "image": "e2-big-splash",
      "ending": { "kind": "silly", "title": "El gran chapuzón" }
    }
  }
}
```

Rules:
- **Node ids never change** once a version is published. Progress, the endings collection and the story map are keyed by them. A rewrite that changes the structure gets new ids.
- Text and audio are per language: one `story/<lang>.json` per language, each with the same structure. Images are shared by all languages. Node ids are the same in every language, so switching keeps a reader's place.
- A JSON Schema (`story.schema.json`) is the single definition of the format. The pipeline's validator and the app's tests both use it.

### 4.5 Validation (`story check`)

A story can't be published until all of these pass:
- The schema is valid. There's one start, every node is reachable from it, there are no cycles, every path reaches an ending, and every ending is reachable.
- Every fork has exactly two options with different targets. Play-through lengths and fork counts are within the age band's budget (§4.2).
- Word counts per page are within the band. No empty text. Every language is complete.
- Every referenced image and audio file exists and has the right size and format. Every clip has timings that cover all of its words.
- The image and narration counts are within budget (no accidental 120-image story).

---

## 5. Content pipeline

### 5.1 Overview

```
idea (one or two sentences, or picked from generated seeds)
 │
 1. Story bible ......... premise, cast, setting, tone, theme, age band          [LLM]                    ◀ you approve
 2. Outline (graph) ..... nodes, one-line beats, forks, endings, visual state    [LLM + validator]        ◀ you approve on the map
 3. Text ................ every page, question and option label                  [LLM]
 4. Editor pass ......... merges, reading level, voice, safety                   [LLM + automatic checks] ◀ you read every path
 5. Shot list ........... what each illustration shows                           [LLM]
 6. Asset requests ...... <story>/REQUESTED_ASSETS.md: size, references, prompt   [script, no images]      ◀ you run the prompts
 7. Illustrations ....... AI image tool, outside the repo; placeholders until then [optional vision check] ◀ you drop files in
 8. Narration requests .. <story>/audio/<lang>/REQUESTED_AUDIO.md, per language  [script, no audio]       ◀ you record or commission
    then: delivered clips, loudness-normalized, word timings                     [story package]          ◀ you listen
 9. Translation ......... per language: text + translation sheet, then its narration requests [LLM + script] ◀ native read-through
10. Package + publish ... story check, WebP/AAC, upload, catalog                 [script]                 ◀ you say go
```

Every step reads and writes plain files in `content/stories/<id>/`, so any step can run again on its own, for the whole story or for one node (`--node p04-river`). Each output records how it was made (step, prompt version, model, date, seed) in `provenance.json`.

### 5.2 Step by step

**0. Seeds (optional).** No idea? Ask `/new-story` for seeds and it proposes one-line premises in the shape "*a hero* wants *something* but *an obstacle*, in *a place*", each with a value (kindness, courage, sharing, curiosity, self-acceptance…), a shape and a tone, from the craft guides in `tools/prompts/craft/`. You pick one or mix two.

**1. Story bible** (`bible.json`, about one page). Title, one-line pitch, age band, theme, the story's **value, shape and arc** (`craft`), tone (funny, gentle, adventurous), the cast, the places, the rules of the world, and a "never" list for this story. Each character gets a name, age, personality, a **refrain or verbal tic**, and a **visual description** precise enough to draw from: species, colors, clothes, one distinctive item. Every later step gets the bible in its prompt. That's what keeps 25 pages and 45 images consistent.

**2. Outline** (`outline.json`). The whole graph: a one-line beat per page, each fork's question and its two options, each ending's kind and title, and the **visual state** of every page (who's there, what they wear or carry, time of day, weather). The prompt includes the size budget (§4.2) and the craft rules (§5.3). A validator checks the graph rules (§4.5). If anything fails, the errors go back to the LLM to fix, up to three rounds. You review the outline as a map in the previewer (§5.6) and can ask for changes in plain words ("make the second fork about helping someone, not about which path to take").

**3. Text** (`story/<lang>.json` in the first language, `story/en.json` so far). The whole story is written **in one go**, with the bible and outline in and every page out. A 4–7 story is about 1,200–2,000 words, small enough to keep one voice from start to end. `story check` validates it against the story schema and requires the same structure as the outline, and errors go back for a fix.

**4. Editor pass.** A second LLM request reads the story as an editor, with a rubric:
- For every **merge page**, read it after each incoming path and fix contradictions (words and visual state).
- Reading level for the age band, repetition, rhythm when read aloud.
- Both options at each fork sound tempting. No moralizing.
- The safety rubric (§5.5).

Automatic checks run too: word counts, a readability score, a blocklist. Then **you read every path** in the previewer's "play every path" mode, or with `story paths`. Because paths merge, a 4–7 story has many play-throughs (18 in the spike), but a few **covering paths** (5 in the spike) visit every page and take every choice. Reading those is enough to check every merge. This is the one step that can never be skipped.

**5. Shot list** (`shots.json`). For each page illustration: the scene, who's in it (by cast id), their visual state, the setting, mood, light, framing (wide, medium, close), and what must be clearly visible. For **choice cards**: show the *option* (the fox, the stepping stones) as one clear subject on a simple background, so it reads at thumbnail size. **Leave the hero out** of a card when the option doesn't need them. That also halves the character-consistency problem.

**6. Asset requests** (`story assets` → one `REQUESTED_ASSETS.md` per story, in its folder, plus a shared one at the root). The pipeline generates no image. The root file is an index: the status of every story, the method, the delivery rules and the app icon. Each story's file holds its own art style (§6) and style anchor, its own reference sheets, and, for each image, its file name, size, framing, light and mood, scene, the characters with their state, what must and mustn't be shown, and the page text it illustrates. Each request is a **ready-to-paste prompt** for an image tool that takes reference images, with the images to attach: the style anchor, the reference sheets of everyone in the picture, and the first picture of the place as a location reference. The style block, the palette, each `look` and each place go in word for word, so only the scene changes from prompt to prompt. A picture in the same place, light and framing as an earlier one is made by editing it (`editFrom` in `shots.json`), and one with 4 or more named characters is built in layers: the place, then one character at a time. Reference sheets come first: for each recurring character (and key prop), front, side and back views plus expressions, stored in the story's `characters/` folder once delivered. A cast that comes back in a series reuses its sheets only when the user asks for it (`from` in the bible). Each choice-card request names its partner card, so the two get made easy to tell apart.

**7. Illustrations.** You generate each image with an AI image tool outside the repo (ChatGPT or the GPT Image API), from its prompt and the images it attaches, and drop it into the folder its request names. Run `story assets` again and its entry turns ✅. Until then the app and the previewer show the static placeholder for its kind (`content/placeholders/`). Optionally, later: a vision-capable LLM *checks* a delivered image against its shot and the sheets (the right characters, nothing missing, no text). It only checks, never generates. Packaging crops (4:3, 1:1 or 3:4), resizes, converts to WebP and strips metadata.

**8. Narration.** **No audio is generated in this project** (2026-11-23). `story assets` writes one `REQUESTED_AUDIO.md` per story and language, in `audio/<lang>/`, written in that language (2026-11-24), and an index at the root with every story's status per language and the narrators. Each file holds the narrator, the delivery rules and one technical spec (WAV or FLAC, mono, 48 kHz, about −16 LUFS, 0.3 s / 0.5 s of silence), the names with their `pronounce` hints, and for every clip its exact script and a voice direction (the page's mood, how to ask a question, how to read a choice label, the ending's kind). The first language takes its names and moods from the bible and `shots.json`, a translation from its translation sheet. The narrators are described in `content/style/voice.json`, one per language, each in its own language (proposed until approved). The clips are recorded or produced outside the repo and dropped into `audio/<lang>/`. What follows describes the delivered clips. One warm narrator voice per language, slower for younger bands. Each page, fork question and option label is its own clip. Clips are loudness-normalized (`ffmpeg loudnorm`) and encoded as mono AAC at 64 kbps, which is plenty for speech. Word timings are measured from the clip and its known text at packaging (2026-11-24, `tools/storytool/timings.py`): the clip's pauses are matched to the text's sentence and clause breaks, and the words between two pauses are spread by syllables. Not from whoever made the clip, so any recording works and the timings are always produced the same way, with ffmpeg alone (no model). You listen to each clip in the app (debug build, after `story publish draft`) with the words appearing. That also catches mispronounced names, which you fix with a `pronounce` hint in the bible and a new take.

**9. Translation.** Claude translates in Claude Code with the bible at hand (`/new-story` step 6, `tools/prompts/translate.md`): a retelling for reading aloud, inside the same word budgets, with names adapted when they are words or jokes (Glim becomes *Chispita*), refrains and sound words re-created, and the pictures unchanged. It writes the whole story in the language into its own `story/<lang>.json` (the same structure as the first, with `translatedFrom`), the bible's title, a section of `review.md` and the **translation sheet** `translations/<lang>.json` (names, pronunciations, personalities, refrains, glossary and moods in the language), which `story check` holds to the bible and `shots.json`. **Spanish is Latin American Spanish** (2026-11-24): tú, and words that read the same from Mexico to Argentina. The sheet stays `draft` until a native speaker reads every path in the language (`story paths <id> --lang es`); only then is its narration recorded.

**10. Package and publish.** Run `story check`, build the package (§4.4), bump the version, upload it to the content host and update `catalog.json`. Only when you ask for it, like publishing Nim's music.

### 5.3 Craft rules built into the prompts

You don't have to be a storyteller, but the prompts have to be. The knowledge lives in three craft guides in `tools/prompts/craft/`, adapted from the kids-book-writer skill (jamesrochabrun/skills, MIT) without its rhyming parts: **values** (one main value per story, shown and never told, and chosen by the child in two good ways), **structures** (the arc on every path, the shapes and arcs and how each one branches, endings) and **ages** (what each band can follow and feel: words, sentence length, feelings, themes). The short version goes into the prompts:
- A hero the child can be: a clear **want**, a small **problem**, a **friend**.
- **Choices are about character, not luck:** be brave or be careful, help a friend or explore, ask or try. Both options must sound fun. No "obviously wrong" option.
- Consequences show up **soon** (within a page or two), so kids feel their choice mattered.
- One beat per page. Concrete, sensory words. A **refrain** the child can say along. Humor: silly sounds, surprises, a gentle running joke.
- **Endings are kind.** A "silly" ending is a funny mishap, not a failure. The best ending rewards kindness or curiosity, not just cleverness.
- Mild peril is fine for 7–10, barely any for 3–5. No death, injury, abandonment, real brands, existing characters or real people.
- A diverse cast and places, no stereotypes.

### 5.4 Which models

No image or voice model: neither pictures nor narration are generated. Candidates as of November 2026 (check current versions, limits, prices and licenses then):

| Job | Candidates | What matters |
|---|---|---|
| Text: bible, outline, prose, editor pass, shot list, translation, optional check of delivered images | **Claude in Claude Code** (the session's model, e.g. Claude Opus 5.5), through the `/new-story` skill. **No API key**: it runs on your Claude Code plan. | The prompts in `tools/prompts/` (versioned) and the JSON schemas in `schema/`, with `story check` as the repair loop. Vision (reading delivered images) for the optional check. `provenance.json` records the prompt version and model for each step. |
| Illustrations | **An AI image tool you run outside the repo** (2026-11-23): ChatGPT or the GPT Image API, from the prompts in `REQUESTED_ASSETS.md`. The repo never calls it. | Takes several reference images and edits a picture in place; keeps the cast consistent with the reference sheets and the story's own art style; terms that allow commercial use in an app. |
| Narration | **None: narration is not generated** (2026-11-23). It's requested in `REQUESTED_AUDIO.md` and delivered by hand: a recorded narrator, or a voice tool you run yourself outside the repo. | A warm, unhurried storyteller voice in every language you ship, the same across stories, and a release or license that allows distribution in an app. |
| Word timings | **Measured at packaging with ffmpeg** (2026-11-24): pauses matched to the text, words spread by syllables. Analysis of delivered clips, not generation. Forced alignment (Montreal Forced Aligner, WhisperX) stays the upgrade if the reveal drifts. | Local and free, no model or download, independent of who or what made the clip. |

### 5.5 Safety and quality gate

A story isn't published unless:
1. `story check` passes (§4.5).
2. The editor pass's rubric passes: peril suits the age band, no frightening images for the band, no stereotypes, brands, real people or existing characters, no unsafe behavior a child might copy (going off with strangers, playing with fire), and nothing you'd be embarrassed to hear a parent read aloud.
3. You've read every path, looked at every image and heard every clip.
4. Ideally, one other adult, or a child in the age band, has played it.

### 5.6 Tools

- **`tools/`:** a Python project run with `uv`, the same kind of tooling as Nim's `backend/music/*.py` and screenshot scripts. One CLI, `story`:
  ```
  story check <id>       story status <id>   story paths <id>    story assets
  story package          story publish       story preview <id>
  ```
  Built so far: `check` (every stage: bible, outline, story, shots, whether the story matches its outline, plus the catalog and the app images), `status`, `paths` (in any language, `--lang`) and `assets` (writes each story's `REQUESTED_ASSETS.md` and one `audio/<lang>/REQUESTED_AUDIO.md` per language, plus the indexes at the root). `check` also holds every translation sheet to the bible and every language to a narrator. `package` converts the delivered pictures and clips and measures the word timings of each narrated page (no separate `align` step, 2026-11-24); `publish` uploads the draft channel for debug builds and the production channel for release builds (2026-11-25).
  **The CLI never calls a model and needs no API key.** The writing steps (seeds, bible, outline, text, editor pass, shots, translation) are done by Claude in Claude Code through the `/new-story` skill, which follows `tools/prompts/<step>.md`. Unattended runs later could use Claude Code's non-interactive mode (`claude -p`) on the same login. Narration is delivered by hand, never synthesized here.
- **Previewer** (`story preview <id>`): a small local web server and page, with four tabs:
  - **Map:** the graph, colored by node kind. Click a node to open it.
  - **Read:** play through like the app, with audio and the word highlight. "Play every path" walks each path in turn.
  - **Art:** each image's request next to the delivered file, or its placeholder.
  - **Issues:** validator and rubric results.

  Picks and notes are written straight into the story folder.
- **Claude Code skill `/new-story`** (in `.claude/skills/new-story/`, built): does the writing steps and drives the CLI from a conversation. You say "a story about a shy dragon who wants to learn to swim, for 4–7". It runs each step, opens the previewer, and **stops at every review gate** for your approval or your notes in plain words ("make page 7 funnier", "p12 needs Tuck's scarf, add it to the request"). This is how a non-writer edits a story: by giving notes, like an editor does.
- **Draft channel:** debug builds load every story as it is from `stories/draft/` on Firebase Storage (`story publish draft`), so a story can be tried on a phone or tablet before it's published, through the same download code (2026-11-25; it replaced a server on the Mac).

### 5.7 Cost and time per story

Rough numbers for a 4–7 story in one language:

| Item | Estimate |
|---|---|
| All writing steps, including the editor pass and the shot list | Included in your Claude Code plan (uses its usage limits), no API billing |
| Illustrations: ~35–45 images plus reference sheets | Outside the pipeline: the image tool (a ChatGPT plan, or GPT Image API usage), retries and layered steps included |
| Narration: ~7,500 characters, ~10 min per language (`REQUESTED_AUDIO.md` gives the exact count) | Outside the pipeline: whatever recording or a voice tool costs |
| Your time | Target: 1–2 hours of review once the tools exist. The first stories will take much longer. |

So the pipeline itself costs nothing beyond your Claude Code plan. Pictures and narration cost whatever making them outside the repo costs. Replace these with real numbers after Phase 2.

---

## 6. Art direction

**Every story has its own art style** (decided 2026-11-24, replacing one house style for the whole app). *The Fox and the Lantern* proposes a *gouache picture book* look, *Noelia and the Baby Squirrel* a *cut-paper collage*; each is in the story's `style/style.json` (proposed until approved) and rendered in its own `REQUESTED_ASSETS.md`. Flat, simple shapes stay the most consistent across ~40 images, whoever makes them. The app icon has its own look, `content/app/style.json`.

Images are never generated in this repo. To compare styles for a story before committing, have its **first batch** made in each candidate style, outside the repo. The story's `REQUESTED_ASSETS.md` lists it (for the spike: the Mia, Tuck and Glim sheets, p01, p03 and p03's two cards). Candidate styles and what to watch for:
   - **Watercolor and ink picture book:** classic and warm. Risk: details drift from image to image.
   - **Paper cut-out / collage:** textured and distinctive, forgiving about small inconsistencies.
   - **Flat shapes with gouache texture and a limited palette:** modern, very consistent, small files.
   - **Soft 3D, clay or felt toys:** tactile and popular. Risk: generic or uncanny.
   - **Crayon / colored pencil:** cozy, looks hand-made. Risk: messy at phone size.
Score each one: does the character stay the same across all images, is it charming, does it read on a phone, are the choice cards recognizable as thumbnails, how much it costs to make. Show the finalists to a few kids in the age band.

Rules, whatever the style:
- **Each story has its own art style and its own characters** (2026-11-24). The style is written down as a **style guide** in the story's `style/style.json` (medium, shapes, line, color, light, composition, what to avoid), written at the bible step and approved at its gate. It goes into every prompt of that story as one fixed **style block** (`prompt`), and a **style anchor** (one picture of the story's world with no characters, made first, `style/style-anchor`) is attached to every prompt as the style reference. The bible adds the cast's palette accents. Two stories share a style or a character only when the user asks: `styleFrom` (the style and anchor) or `from` on a cast member (the look, word for word, and the sheet), declared in the bible and enforced by `story check`.
- Describe a style by medium, technique and palette. **Never "in the style of" a living illustrator.**
- No text, letters, logos or signs in any image.
- Page illustrations are 4:3, with the main action in the middle 80% so layouts can crop the edges. Choice cards are 1:1, one subject on a simple background. Covers are 3:4 portrait, with a calm bottom quarter where the app draws the title.

The **app's UI** is a separate, quieter design: warm paper colors, rounded shapes, big touch targets, so the illustrations stand out. Its tokens live in `designsystem`, as in Nim.

---

## 7. App

### 7.1 Stack

Reuse Nim's stack and setup. Most of it carries over as is.

| Concern | Choice | From Nim |
|---|---|---|
| Shared code and UI | Kotlin Multiplatform + Compose Multiplatform, one `shared` module | Gradle setup, version catalog, ktlint, XcodeGen `project.yml` |
| DI, navigation, ViewModels | Koin, Navigation Compose (type-safe routes), the `StateViewModel` pattern | Same patterns |
| Story parsing | kotlinx.serialization | |
| Images | Coil 3 (supports Compose Multiplatform) | New |
| Narration | `expect/actual` player that reports its **position** for the word highlight (Media3 ExoPlayer or MediaPlayer on Android, AVAudioPlayer on iOS) | Grows out of Nim's `MusicPlayer` |
| Music and sounds | Optional, like Nim's soundtrack, ducked under the narration | [Nim's music doc](../game-nim/docs/music.md) |
| Progress | Room 3: visited nodes, endings found, last position per story | Same setup |
| Settings | DataStore Preferences | Same |
| Content download | Ktor + an okio cache with checksums, from Firebase Storage (public-read `stories/`) | The `HostedMusicLibrary` pattern |
| Store releases | fastlane lanes for TestFlight and Google Play, scripted screenshot stages | Copy and adapt `fastlane/` |
| Analytics and crash reports | **None.** Use the crash reports and vitals that App Store Connect and the Play Console provide without an SDK | Different from Nim on purpose (§9) |

### 7.2 Packages

```
story/          pure Kotlin (stdlib + kotlinx.serialization): the model, StoryEngine (start, next, choose,
                back, path) and graph checks. Tested thoroughly, like Nim's `game`.
domain/         Catalog, StoryLibrary, ProgressRepository, SettingsRepository (interfaces)
data/           HostedStoryLibrary (catalog, downloads, cache), Room, DataStore
platform/       NarrationPlayer, file paths
designsystem/   colors, type (with the early-reader font), page-turn transition, choice card
feature/
  shelf/        the bookshelf
  cover/        the cover, endings found, story map
  reader/       pages, narration + highlight, forks, back
  ending/       the ending screen and badge
  parents/      parental gate + parents area
```

### 7.3 Content in the app

- **No story ships inside the app** (decided 2026-11-24). Only a copy of the shelf's titles does, for a first launch offline.
- The catalog is fetched at every launch online and kept for offline use. A story downloads when a reader picks a language on its cover (the app's own first): its text, its pictures and that language's narration, file by file with checksums, a few at a time, with progress on its cover. Another language can be added later; the story is read in the one picked last. The parents area can delete downloads.
- Files are named after their contents, so a new version of a story changes its manifest's name in the catalog; the app downloads only the files it doesn't have, switches once all are there, and deletes the old ones at the next launch. Progress carries over because node ids are stable (§4.4). See docs/story-format.md, "Published stories".
- Size: ~45 WebP images ≈ 10 MB, plus ~5 MB of narration per language, so **about 15 MB per story**.
- Hosting: Firebase Storage in the project `tale-time-b537d`, bucket `tale-time-b537d.firebasestorage.app` (us-east1). Rules (`backend/storage.rules`, deployed 2026-11-23): single files under `stories/` are public-read, nobody can list the bucket or write through the API. Two folders: `stories/production/` for release builds and `stories/draft/` for debug builds (2026-11-25). No Firebase SDK in the app: downloads are plain HTTPS. At 15 MB per download the egress cost stays small. Cloudflare R2 (no egress fees) is the fallback if it ever doesn't.

### 7.4 Narration and highlight

Built (2026-11-24). A text without its clip plays without narration: the words appear at the narrator's pace for the age band (`Pacing` in `story/`), and the "next" arrow pulses once the last one is there. A narrated page uses the same reveal, with each word's time from the language's timings and the reading clock read from the player's position every frame, so the words wait whenever the narration does; the arrow pulses once the narrator has said the last word. Without timings for its words (a clip made for an older text), a narrated page reveals at the narrator's pace after the clip's 0.3 s lead.

The whole text is laid out once and the words not said yet are erased while drawing, so there's no text layout work per frame. Narration pauses when the app goes to the background and stops when the page is left; going back across a fork opens its cards without reading the text again. At a fork, the question is read and then each label, and the card being read lifts a little. In auto mode (later) the page turns about 1.5 s after the clip ends.

### 7.5 Free and paid stories

- **The shelf is `content/catalog.json`** (schema/catalog.schema.json): the stories in shelf order, each `free` or `paid`, whether it ships inside the app, and for a paid one its store `product` id. `story check` validates it; the app reads it as is.
- **A paid story is a one-time, non-consumable purchase**, one product per story, with the same id on the App Store and Google Play (`story.<name>`: lowercase, digits, `_` and `.`, since Google Play allows no `-`). No subscriptions, no consumables, no ads. Bundles ("unlock all") can be added later as extra products.
- **The first story is free** (*The Fox and the Lantern*), so a new family can read right away. `story check` warns if the shelf opens with a paid story.
- **Every purchase starts behind the parental gate** (Apple Kids category and Google Play Families both require it). The cover of a locked story says "Ask a grown-up"; after the gate, the unlock sheet shows the price, Buy, Restore purchases and Not now.
- **The stores remember what was bought**, so the app stores nothing about the family. Entitlements come from StoreKit 2 (`Transaction.currentEntitlements`) and Google Play Billing (`queryPurchasesAsync`), behind `domain/Purchases`. Until those are wired in, `UnavailablePurchases` keeps paid stories locked.
- **Paid packages are downloaded like free ones**, from public-read URLs, and the app gates them by entitlement. That's enough for v1. If copying ever matters, a Cloud Function can check the store receipt and hand out a short-lived signed URL instead.

---

## 8. Repository layout

```
tale-time/
  plan.md  AGENTS.md  CLAUDE.md  README.md  REQUESTED_ASSETS.md  REQUESTED_AUDIO.md
  androidApp/  iosApp/  shared/        same shape as Nim
  tools/                               the Python pipeline (uv): storytool/, prompts/, previewer/, tests/
  content/
    catalog.json                       the shelf: order, free or paid
    app/                               app icon request (assets.json), the app's own look (style.json), the delivered icon
    style/                             narrators, one per language (voice.json)
    stories/<id>/                      bible.json, style/ (its own art style and anchor), characters/ (its own sheets),
                                       outline.json, story/<lang>.json, shots.json, translations/, provenance.json, review.md,
                                       images/, audio/<lang>/ (clips and that language's REQUESTED_AUDIO.md)
  backend/                             storage.rules (firebase.json and .firebaserc at the root)
  fastlane/
  docs/                                story-format.md, pipeline.md, art-style.md
  .claude/skills/new-story/            the authoring skill
```

Asset files are big: about 200 MB per story with its narration. Commit the text and JSON; keep the delivered images and audio **out of git, in Firebase Storage** (`originals/` in the bucket, private, named after their contents so nothing is ever overwritten), listed with their hashes in the committed `content/originals.json` (`story originals push` and `pull`); and keep drafts and work in progress in an ignored `content/.work/` folder.

---

## 9. Kids, privacy and the stores

- **Collect nothing.** No accounts, ads, analytics or crash SDKs, no tracking. The only network traffic is the catalog and story downloads. Progress and settings stay on the device. This keeps COPPA (US), GDPR (EU) and the Swiss nFADP simple, and it's the honest choice for a kids' app.
- **Apple Kids category** (see Open questions): no third-party analytics or ads, and a **parental gate** before anything that leaves the app (links, email, purchases). Check the current wording of App Review Guidelines 1.3 and 5.1.4 before submitting.
- **Google Play Families policy** applies as soon as the target audience includes children: the target-audience declaration, the Families content rules, only SDKs that meet the Families requirements, and the Data safety form. Check the current policy before submitting.
- **Parental gate (built):** a multiplication written in words in the app's language ("seven times four", "siete por cuatro"), typed on a number pad. A child who can't read can't solve it, random tapping almost never hits a two-digit answer, and a wrong answer brings a new question. It guards the parents area, links and every purchase.
- **AI disclosure:** say plainly, in the store listing and the parents area, that the stories were written with AI tools and reviewed by a person. Say that the pictures were made with AI image tools too, and how the narration was made. Check each store's current rules on AI-generated content.
- **Licenses:** keep the release or commercial-use terms of each narrator voice, and the terms of the image tool and model that made each image, on file. `provenance.json` records where each asset came from.
- A privacy policy page, even though nothing is collected (Nim's website approach works).

---

## 10. Testing

- **`story` package** (commonTest, JVM + iOS): the engine on fixture stories (plain, forks, merges, every ending), back, resume, and folding progress into endings found.
- **Pipeline** (pytest): the validator on broken fixtures (unreachable node, cycle, missing asset, three-option fork, path too long), package building, catalog generation.
- **One format for both sides:** `story.schema.json` and the sample stories are shared by both test suites, so the app and the pipeline always agree on the format.
- ViewModel tests with Turbine, the downloader with Ktor's MockEngine.
- Visual checks on a phone **and** a tablet, in both orientations on the tablet.
- **Content:** the gate in §5.5 for every story. Playtests with kids in the age band: watch where they hesitate. Can they tell the two cards apart? Do they find "back"?

---

## 11. Roadmap

The risk is the **content**, not the app: can the pipeline make stories that are good to read, with pictures that stay consistent, at a cost and effort you can keep up? So the pipeline comes first, and the app gets built around real stories.

### Phase 0: Decisions and spikes
- [x] Age band and launch languages: **4–7; English and Latin American Spanish** (Spanish added 2026-11-24). The band is a provisional default; confirm (Open question 1)
- [x] **Story spike, by hand:** *The Fox and the Lantern* (bible → outline → text → editor pass → shots), in `content/stories/fox-and-the-lantern/`. Lessons are in its `review.md`
- [ ] **Your read-through** of the spike (`story paths fox-and-the-lantern`) and the three safety calls in its `review.md`
- [x] ~~Art bake-off with image models~~. Replaced: images are generated with AI outside the repo, from the prompts in `REQUESTED_ASSETS.md`. House style proposed in `content/style/style.json`, and every image requested in `REQUESTED_ASSETS.md`
- [ ] **Approve each story's art style** (or compare candidates using its first batch, made outside the repo)
- [x] ~~Voice bake-off with TTS~~. Replaced: no audio is generated. Narrator proposed in `content/style/voice.json`, every clip requested in `REQUESTED_AUDIO.md`
- [ ] **Approve the narrators**, English and Spanish (`voice.json`), and choose who or what records the clips, outside the repo
- [x] Name: **Tale Time**

### Phase 1: Story format and text pipeline
- [x] `schema/story.schema.json` and `docs/story-format.md`
- [x] `story` CLI: `check` (format, graph, budgets, languages, shots, assets), `paths` (covering paths for the read-through), `assets` (REQUESTED_ASSETS.md). With pytest
- [x] Writing steps without an API key: the `/new-story` skill + versioned prompts in `tools/prompts/` (seeds, bible, outline, text, edit, shots) + schemas for bible, outline and shots + `provenance.json`
- [x] `story check` on every stage (the repair loop), `story status`; `story paths` also reads an outline
- [ ] Previewer: Map, Read (text only), Issues
- [ ] Three text-only stories in different bands and tones (1 of 3: the spike). Read every path and tune the prompts

### Phase 2: Asset requests and voice pipeline
- [x] House style guide (proposed) and static placeholders (`content/placeholders/`)
- [x] Asset requests: `shots.json` → `REQUESTED_ASSETS.md` (size, style, description per image; reference sheets first)
- [ ] Packaging of delivered images (crop, resize, WebP, strip metadata); optional vision check of delivered images
- [ ] Previewer: Art tab (request next to the delivered file or placeholder)
- [x] Narration requests: `story/<lang>.json` + `voice.json` + bible `pronounce` hints (or the translation sheet) → `audio/<lang>/REQUESTED_AUDIO.md`, one per language, written in it (script, direction, spec per clip)
- [x] Translation step (`translate.md`, translation sheets, checks); Latin American Spanish drafts of both stories
- [x] Word timings from delivered clips, measured by `story package` (pauses matched to the text, no forced alignment), shipped as `timings-<lang>.json`
- [ ] Previewer: Read tab with audio and the word reveal
- [x] Packaging: `story package` (WebP, AAC at even loudness, content-named files, manifests, catalog; draft and production channels)
- [ ] Two complete stories end to end. Measure the real cost and your time

### Phase 3: App MVP (offline)
- [x] Project scaffold from Nim's setup (Gradle, XcodeGen, ktlint, Koin, navigation, Room 3, DataStore, Coil 3 with SVG), plus AGENTS.md and CLAUDE.md
- [x] `story` engine and its tests (JVM and iOS simulator), on the pipeline's own fixtures and every real story
- [x] Reader: pages, forks with choice cards (one tap picks), back a page and next arrows, endings with "The End", badge and "Try another path"
- [x] Reader: the text appears word by word at the narrator's pace in a window a few lines tall, under a picture that takes most of the screen
- [x] Reader: narration, the words appearing as they're said; fork questions and labels, ending titles and the cover's title read aloud; pauses in the background
- [x] ~~Bundled content~~ replaced by downloads (2026-11-24); placeholders until pictures arrive
- [x] The bookshelf and the cover (Read / Continue / Start over, endings found)
- [x] Progress in Room (path, pages visited, endings found), the endings collection on the cover
- [x] Free and paid stories in the catalog; locked covers; parental gate; unlock sheet (store not connected yet)
- [x] Parents area: text size, reset progress, about (AI disclosure, nothing collected)
- [x] Language: English or Latin American Spanish, picked in the parents area (the device's until then), for the app's own words and the stories; a story not in it yet shows in its first language
- [x] App icon: delivered 2026-11-25 into `content/app/`; `story icons` puts it into both apps (Android adaptive launcher icon, iOS AppIcon)
- [ ] Try it on a phone and a tablet. First playtest with kids

### Phase 4: Catalog, downloads and purchases
- [x] Firebase project (`tale-time-b537d`) and Storage rules (deployed)
- [x] `story publish draft|production`, the hosted `catalog.json` per channel (built, not run yet)
- [ ] In-app purchases: StoreKit 2 and Google Play Billing behind `domain/Purchases`, products in both stores, restore
- [x] Downloads per language with progress, checksums, offline cache, updates at launch and delete
- [x] Draft channel for debug builds (`story publish draft`, `stories/draft/` on Firebase; replaced `story serve` 2026-11-25)
- [ ] Story map

### Phase 5: Polish and kid-proofing
- [ ] Page-turn animations, sounds, optional music ducked under the narration
- [ ] Parents area, the rest: narration on/off, auto-advance, downloads, purchases
- [ ] Tablet layouts in both orientations, accessibility pass, reduced motion
- [ ] "I read myself" mode with tap-a-word

### Phase 6: Launch content
- [ ] 8–10 stories across bands, themes and tones (a mix of funny, gentle and adventurous)
- [ ] Native read-throughs of the Spanish drafts (both stories), then `"status": "approved"` in their translation sheets
- [ ] Spanish for every launch story, with a native read-through each

### Phase 7: Release
- [ ] Store listings, screenshots (scripted stages, like Nim), icon
- [ ] Privacy policy, privacy labels and Data safety, Kids category and Families declarations
- [ ] TestFlight and Play internal testing, playtests with kids, launch

### Later
- Flags (stories that remember a choice), three-option forks for older kids
- A recurring cast and story series, reusing the reference sheets
- A voice per character, ambient sound per scene
- Bedtime mode
- Personalization with the child's name, if there's a way to narrate it that fits the privacy rules
- More languages

---

## 12. Conventions (once code exists)

- One `shared` module, layers by package. `story` stays pure Kotlin (stdlib + serialization).
- Node ids never change once published.
- No text in images. No runtime AI in the app. No SDK that sends data about the child.
- **No image generation in the repo.** Images are generated with AI outside it, from the prompts in `REQUESTED_ASSETS.md`, and dropped in. Static placeholders stand in until then.
- Every asset comes from a command that can run again. Hand edits to text are fine, but they live in the story folder, in git.
- Publishing content and store uploads only happen when you ask for them.

---

## 13. Open questions

1. **Age band:** 3–5, **4–7** (proposed), or 7–10? It sets the page length, the number of forks, how much peril is OK, and how much the choices rely on narration. *Using 4–7 for now.*
2. ~~**Languages at launch:** English only, or also German, French or Spanish?~~ *Decided 2026-11-24: English and Latin American Spanish.* Each extra language costs a translation review and a narration pass per story.
3. **Art style:** one per story (2026-11-24): gouache proposed for the fox, cut-paper collage for Noelia (each story's `style/style.json`). Images: an AI image tool, outside the repo, from the prompts in `REQUESTED_ASSETS.md`. Still open: which tool and model, and its terms for commercial use.
4. **Narrators:** who records the clips in each language, or which voice tool you use outside the repo. The voices themselves are proposed in `content/style/voice.json`, one per language.
5. **Apple Kids category**, or a general family app? The Kids category has stricter rules but earns more trust and visibility with parents.
6. **Monetization:** *Decided 2026-11-23: free and paid stories.* Proposed: each paid story is its own one-time purchase (§7.5). Still open: prices, and whether to add an "unlock all" bundle.
7. **Silly endings** for every age, or only kind endings for 3–5?
8. ~~**Picking a card:** one tap, or tap to hear then tap again to pick?~~ *Decided 2026-11-23: one tap.*
9. **Stateless stories only** in v1 (proposed), or flags from the start?
10. **Personalization** with the child's name: only in the text (narration can't say it, and nothing is synthesized), or never?
11. ~~**The app's name.**~~ *Decided: Tale Time.*
