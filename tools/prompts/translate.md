# Step 6: Translation

version: 2

Translate a story the user has approved into one more language: the whole story into its own file, `content/stories/<id>/story/<lang>.json`, the bible's title, and the story's **translation sheet**, `content/stories/<id>/translations/<lang>.json`. The sheet keeps the translation consistent and gives that language's narrator what the bible and `shots.json` give the first one. Then hand over to a native speaker's read-through, the gate for this step.

A translation is a retelling for children who will hear it read aloud, not a word-for-word copy. Keep the story, the beats, the fun and the heart. Change the words.

## Input
- The story in its first language (the `story/<lang>.json` without `translatedFrom`, `story/en.json` so far), approved at step 4. Translate from it, never from another translation.
- `bible.json`: the cast, the value, the refrains, the "never" list. `shots.json`: what each picture shows, and each page's `mood`.
- `review.md`: the judgment calls the user already made.
- `craft/ages.md` (words, sentences, feelings for the band) and `craft/values.md` (show, don't preach).
- The target language and its variety. **Spanish (`es`) means Latin American Spanish**, below.
- Example: `content/stories/fox-and-the-lantern/` (`story/es.json` and `translations/es.json`).

## Output
1. **`story/<lang>.json`:** a copy of the first language's file with `"language": "<lang>"`, `"translatedFrom"` set to the first language, and every text in the language: the title, every page, every question, both labels of every fork, every ending title. Everything else stays exactly as in the first language: the same id, version, age band, cover and start, the same nodes in the same order, the same ids, links, images and ending kinds (`story check` fails otherwise). Don't touch the first language's file.
2. **`bible.json`:** the language added to `title`. Change nothing else in the bible.
3. **`translations/<lang>.json`** (`schema/translation.schema.json`), with `"status": "draft"`:
   - `language` and `from` (the story's first language).
   - `pitch` and `tone`, in the language.
   - `cast`: **every** id in the bible's `cast`, `extras` and `props`, with its `name` as the text says it, and its `personality` and `refrain` in the language when the bible has them. The refrain is word for word what the text says. Add a `pronounce` hint, respelled for that language's narrator, for any name a native reader could stumble on, like a name kept from another language.
   - `glossary`: places and recurring words (the pond, the song, the sound words), so every page says them the same way.
   - `moods`: every page and ending's `mood` from `shots.json`, in the language.
4. **`review.md`:** a section `## Translation: <language> (<code>)`, described below.
5. `provenance.json`: a `translate` entry.

Then run `uv run story check <id>` and fix every error (the budgets hold in every language), run `uv run story assets` (it writes `audio/<lang>/REQUESTED_AUDIO.md`, in that language), and stop.

## Rules
1. **Read aloud first.** Rhythm, short sentences, words a child of the band knows. If a line is awkward to say, rewrite it.
2. **The same story on every page.** Same beats, same information, same page turns (a page that ends on "And inside was..." still does). Add nothing the picture would contradict, and drop nothing a later page or the picture depends on. **The pictures are shared by every language:** never change what a page shows.
3. **The budgets hold in every language.** Words per page stay inside the band. Spanish and French run longer than English: tighten the wording, never cut a beat. Aim for the middle of the range.
4. **Names.** Keep a name a child of the language can read and say as written (Mia, Noelia, Pipa). Adapt a name that is a word in the first language or carries a joke, to a short name in the same spirit (Glim, from *glimmer*, becomes *Chispita*; Bramble the hedgehog becomes *Zarza*; Gertie becomes *Gertrudis*). A role takes the language's own word (Grandma is *la abuela*). The same name on every page. **List every adapted name in `review.md`** for the user.
5. **Refrains, running jokes and sound words are re-created, not translated.** A refrain must work aloud in the language, and it's the same, word for word, every time it comes back, twists included. Sound words use the language's own (tap-tap is *toc-toc* in Spanish). A song keeps its tune-like shape; a loose rhyme or assonance is fine in a refrain, but the story itself never rhymes.
6. **Forks.** The question keeps the "this or that?" shape, the two labels stay short (two to four words) and equally tempting, and neither sounds like the right answer.
7. **Merges.** A merge page must read right after every page that leads into it, in this language too. Read the covering paths in the language: `uv run story paths <id> --lang <lang>`.
8. **Heart and safety.** The value is still shown, never stated; no ending gains a moral. The "never" list holds. No new peril, no new risky behavior a child could copy, no word that is rude or odd somewhere the language is spoken.
9. **No text in the pictures**, as always. The app draws every title and label in the reader's language.

## Latin American Spanish (`es`)
One Spanish for every Latin American country: what a child in Mexico, Colombia, Peru, Chile or Argentina understands the same way.
- **Tú**, never *vos* or *vosotros*. *Ustedes* for more than one.
- **Words that travel.** Prefer *pasto, jugo, lentes, papa, durazno, frijoles, cachetes, migajas, manta, pan tostado, de puntitas*. Avoid Spain-only words (*zumo, gafas, patata, melocotón, ordenador, vale, tío*) and words that are rude or mean something else in a big country: *coger* (use *agarrar, tomar*), *mariquita* for a ladybug (say *un bichito rojo con puntitos*), *piña* alone for a pinecone (it's a pineapple in Mexico: *piña de pino*), *tostada* for toast (a fried tortilla in Mexico).
- **Diminutives** (*-ito, -ita*) are warm and natural read aloud: use them, but not on every noun.
- **Punctuation.** Opening marks: *¿…?* and *¡…!*. Dialogue in straight double quotes, like the first language, so every language looks the same in the app: `"¡Hola!", dijo Noelia.` The comma or period goes after the closing quote. No comma before *o* in a question with two choices.
- **Numbers** in words, as in the first language.

## The `review.md` section
`## Translation: <language> (<code>)`, with:
- **Status:** draft, translated from which language, and that a native speaker's read-through of every path is pending. Narration in this language waits for it.
- **The numbers:** words per page (lowest, highest) and total, against the first language.
- **Names:** every name kept, every name adapted and why.
- **Refrains and sound words:** each refrain and running joke as it is now, and the sound words that changed.
- **Judgment calls:** numbered, for the user (a word that's regional, a joke that changed, a line that's tighter than the original).
- **Your read-through:** a checklist that starts with `uv run story paths <id> --lang <lang>`, and ends with setting `"status": "approved"` in `translations/<lang>.json` and running `uv run story assets`.

When the user approves, set `"status": "approved"` in the sheet, run `uv run story assets` (the language's `REQUESTED_AUDIO.md` drops its draft warning), and record it in `provenance.json`.
