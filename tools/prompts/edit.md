# Step 4: Editor pass

version: 3

Read the story as a picture-book editor, fix what needs fixing in the story's text (`story/<lang>.json` in its first language), and write `content/stories/<id>/review.md` for the user. Then hand over to the user's read-through, **the one gate that can never be skipped**.

Take a fresh look: read the text from the file, path by path, and don't rely on memory from writing it.

## Input
- `bible.json`, `outline.json`, `story/<lang>.json` (the first language).
- `uv run story paths <id>`: the covering paths, which together visit every page and take every choice.
- The craft guides: `craft/values.md`, `craft/structures.md`, `craft/ages.md`.
- Example: `content/stories/fox-and-the-lantern/review.md`.

## The rubric

**1. Merges.** For every page with more than one way in, read it right after each incoming page. Does anything contradict, in the text or in what the picture would show (`visual.mustNot`)? Side characters gone? Clothes and props the same on every path? Fix contradictions on the incoming pages or the merge page.

**2. Reading aloud and age fit.** Words within the band. Sentence length, vocabulary, feelings and dialogue for the band (`craft/ages.md`). No sentence a parent would stumble over. Rhythm. No word repeated too close together (except on purpose). Refrain and running joke present. Sound words.

**3. Choices.** Both options at each fork sound tempting. Neither is obviously wrong, and neither is punished. The consequence shows within a page or two. The best ending's choices come after the last merge.

**4. Heart** (`craft/values.md`). The bible's value is shown, never stated, and no ending's last line is a moral. At the forks that carry the value, both options are good forms of it, never its opposite. Consequences come naturally (a feeling, a friend, pride), not as prizes. Nobody is a villain or is punished. The stakes are real and small.

**5. Shape** (`craft/structures.md`). The problem appears by page 2, and there's one main problem. Every path has the whole arc, with the low point on a trunk page. The hero solves the problem, with help, and isn't rescued from nowhere. The resolution isn't rushed. For a growth arc, the hero changes on every path. For an impact arc, every ending shows the impact.

**6. Endings.** Each ending feels like an ending (not a cut-off), is kind, and comes back to safety. A silly ending is funny, not a failure. Its page's last sentence ends the story by itself, since the app shows and says no ending title: read it, then "The End.", and check it lands. **Every fork page makes sense heard again straight after an ending** (a "?" badge takes the book back to it): read it right after each ending on its paths; it must say who's there and what's at stake without the page before.

**7. Safety** (plan.md §5.5):
- Peril suits the age band: barely any for 3–5, mild for 4–7, mild for 7–10.
- Nothing frightening in the text or in the pictures it implies.
- No stereotypes, brands, real people or existing characters.
- **No unsafe behavior a child might copy**: going off with strangers, wandering off alone, deep water, fire, climbing high, touching wild animals, eating unknown things, hiding from adults.
- Nothing you'd be embarrassed to hear a parent read aloud.
- The bible's "never" list holds.

When a point is a judgment call (a defused risk the user might still not want), **don't decide it silently**. List it in `review.md` with how it's handled, for the user to decide.

## Output
1. Edits applied to `story/<lang>.json`. If a story already has translations, the same edits go into every other `story/<lang>.json` (following `translate.md`). If a beat changed, also update `outline.json`, but the structure stays the same unless the user agreed to change it.
2. `review.md`, with these sections:
   - **Status:** draft, which language, and that the user's read-through is pending.
   - **The numbers:** age band, pages, words per page, total words, play-throughs and their length, endings with titles and kinds, image count, and how many covering paths there are.
   - **Editor pass:** a merge-page table (page, reached from, what was checked), read-aloud qualities, choices, endings.
   - **Heart and shape:** the bible's value, shape and arc; the trunk page with the low point; the forks where the child picks the value; and how each ending shows it.
   - **Safety rubric:** passes or not, then the numbered judgment calls for the user.
   - **What this story taught us:** anything a prompt rule could have prevented. Also update the prompt itself, and bump its version.
   - **Your read-through:** a checklist that starts with `uv run story paths <id>`.

Then run `uv run story check <id>`, fix every error, and stop. Tell the user how to read the paths and list the judgment calls. Wait for approval or notes.
