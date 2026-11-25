# Step 1: Story bible

version: 6

Write `content/stories/<id>/bible.json`: about one page that every later step reads. It's what keeps 20+ pages and 35+ images consistent. Also write the story's own art style, `content/stories/<id>/style/style.json` (below).

## Input
- The idea (from the user, or a picked seed), the age band, an optional value and tone.
- The craft guides, in full: `craft/values.md`, `craft/structures.md`, `craft/ages.md`.
- The other stories' `style/style.json`: this story's style must be clearly its own, not theirs.
- A recurring character from another story **only if the user asks for it**: then copy its look word for word and set `from` (below).
- Example: `content/stories/fox-and-the-lantern/bible.json`.

## Output
`bible.json` matching `schema/bible.schema.json`. The `id` is a short slug of the title (also the folder name). And `style/style.json` matching `schema/style.schema.json`.

## Write the "never" list first
Before the cast and the plot, write `never`: 5 or more things this story must never do for its age band. It catches problems before they're written. In the spike it caught three:
- **A child alone at night, or far from home.** Give the adventure a safe frame (a garden, a known place) with a caring adult in reach, and come home at the end.
- **Going off with a stranger.** An animal guide is a known friend or neighbor, said in the text.
- **Copyable unsafe acts**: touching wild animals, deep water, fire, climbing high, eating unknown things, hiding from grown-ups. Defuse them in the world rules (for example, "the brook is ankle-deep").

Also always: nobody gets hurt, the dark isn't scary, no sharp teeth or scary faces, adults aren't angry, no one is trapped. Add what `craft/ages.md` says to avoid for the band.

## Value, shape and arc
Then fill `craft`, before the cast and the places, because it decides what they're for:
- `value`: **one** main value from `craft/values.md` that suits the band. `alsoValues`: up to two that come along.
- `structure`: a shape from `craft/structures.md` that suits the band. Its branching notes will shape the outline.
- `arc`: `growth`, `realization` or `impact`. For a growth arc, the hero's flaw is the one the value answers.
- `payoff`: how the child should feel on the last page, in a sentence ("Warm and proud: Mia helped a little star get home").
- `theme` says the value as this story's own sentence. The `pitch` names **one main problem** that calls for the value.

## Cast
- **One hero** (`role: "hero"`): a clear **want**, a small **problem**, and a **friend**. Personality in a sentence, with one small flaw that's fun (impatient, a show-off). The hero's feelings stay within the band (`craft/ages.md`).
- **Nobody is a villain.** A grumpy or unkind character softens, or turns out to have a reason (Gertie the goose).
- **At most 3 recurring characters** besides the hero, and at most 3 one-scene characters (met on one branch only).
- Every invented or unusual name gets `pronounce`, a respelling or rhyme for the narrator (`"MEE-ah"`, `"GLIM, rhymes with him"`). It goes into REQUESTED_AUDIO.md.
- **A name or word from another language is respelled the way that language says it**, not the way an English reader would guess: Spanish *Abuelo* is `ah-BWEH-loh`, not `ah-BWAY-loh`. The narrator reads the respelling literally, so an English-sounding one makes them mispronounce it. (The user corrected exactly this in *Noelia and the Baby Squirrel*.)
- Each gets a **refrain or verbal tic** if they talk a lot. The hero's refrain is something a child can say along, in short, common words (a starting reader can read it with the highlight), and it comes back 2–3 times in the story.
- **A running joke** for the friend if it fits (Tuck's shortcuts are never short).
- **`look` precise enough to draw from**: species, age, size relative to the hero, skin, fur or feather colors, hair, the same clothes in every scene, and **one distinctive item** (a green scarf, red boots). No vague words ("cute", "nice"). Diverse, no stereotypes. Differences are normal, not the lesson, unless the story is about them (`craft/values.md`, Representation).
- **The `look` is a locked description.** `story assets` pastes it word for word into every image prompt the character is in, so it describes **appearance only**, true in every picture: no behavior, moods, story facts or things they sometimes carry. "Usually carries the lantern" puts a lantern in every picture, even a card that shows only her boots; say it in the shots' `state` instead. 2–4 sentences, colors named plainly. Once images exist, don't reword it: a small rewording shifts colors and proportions.
- `sheet: true` for every character who appears in more than 3 pictures, and `expressions`: the 3 the story needs most. A **prop** that's in most pictures (the lantern) goes in `props` with `sheet: true` and `views`.
- Groups of extras (frogs, fireflies) go in `extras` with a look that keeps them cute and simple.

## World
- `places`: 4–7, each a visual description (layout, colors, landmark), arranged so the journey makes geographic sense (where the paths split and where they meet again). Like a `look`, it's pasted word for word into the prompt of every picture set there, so describe the place itself, not a moment in it.
- `worldRules`: what's magical and what isn't, and the safety frame.
- `palette`: 5–8 story accents (name → hex) for the cast's colors and the story's light. They must sit well on the story's own style palette.
- `theme` in a sentence, `tone` 1–2 words, `pitch` in 2–3 sentences.

## Art style: this story's own
Every book has its own look and its own characters. Write `content/stories/<id>/style/style.json`, and create an empty `characters/` folder for the story's reference sheets. **Never reuse another story's style or cast by default**, even when it would be convenient: a style or a character is shared only when the user asks for it, and then it's declared in the bible (`styleFrom: "<story-id>"` instead of writing a style.json; `from: "<story-id>"` on the cast member or prop, with its look copied word for word). `story check` enforces both.
- **Pick a medium and a technique that suit this story's world, tone and light**, and that are clearly different from the other stories' styles (gouache, cut-paper collage, printmaking, colored pencil and wash...).
- **Prefer flat, simple shapes.** Image models keep them consistent from picture to picture, and they read at phone size. Say why the style suits the story in `summary`.
- `prompt`: the style block pasted word for word into every prompt of the story. Medium and technique only, **never an artist's, book's or studio's name**.
- `palette`: 6–8 base colors for the story's world. The bible's `palette` adds the cast's accents on top.
- `anchor`: the story's world with no one in it (a key place, in the story's main light), with `mustShow` for the textures. It's the first picture to make, attached to every later prompt.
- `avoid`: the standard list (no text, no photorealism, no scary faces, no named artists...), plus anything this style needs.
- `status`: "proposed, not yet approved". The user approves the style at the bible gate, with the rest of the bible.

## Before you save
- [ ] Is the "never" list specific to this story, and does the rest of the bible respect it?
- [ ] Could an image model draw each character the same way twice from the `look` alone?
- [ ] Are the colors distinctive enough to tell the cast apart as thumbnails?
- [ ] Is the theme shown by what the hero does, not told?
- [ ] Do the value, shape, arc and themes suit the age band (`craft/ages.md`)? Can the child choose the value at a fork in two good ways?

- [ ] Is the art style this story's own, different from the other stories' styles, and described without naming an artist? Is anything shared with another story only because the user asked?

Then run `uv run story check <id>` and fix every error.
