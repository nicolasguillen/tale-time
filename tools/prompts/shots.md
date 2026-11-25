# Step 5: Shot list → asset requests

version: 2

Write `content/stories/<id>/shots.json`: what every image of the story shows. `uv run story assets` turns it into the story's own `REQUESTED_ASSETS.md` (next to `shots.json`), the requests a person works from to make the images.

Each request is a prompt to paste into an image tool, with the images to attach. `story assets` builds it from locked pieces, word for word the same in every prompt of the story: the story's style block, each character's and prop's `look` from the bible, the bible's look of the place. Only the scene part comes from the shot. So a shot says **what happens**, and never describes a character, prop or place again in other words.

**Never generate an image, or draw one in code.** This step only describes images. (AGENTS.md)

## Input
- `bible.json` (looks of the cast, extras, props and places; the palette), `outline.json` (the visual state of every page), `story/<lang>.json` in the first language (the approved text; the pictures are the same in every language).
- `style/style.json`: the story's own art style (or the one its bible shares with `styleFrom`). Don't repeat it in every shot: the story's `REQUESTED_ASSETS.md` prints it once, and every prompt pastes its style block.
- Example: `content/stories/fox-and-the-lantern/shots.json`.

## Output
`shots.json` matching `schema/shots.schema.json`: **exactly one entry for every image id the story uses**, in reading order. That means the cover, then each node, each fork followed by its two cards. `story check` reports any missing, extra or duplicate entry.

Top level:
- `lightArc`: the light across the story in one or two sentences, page ranges included.
- `firstBatch`: the reference sheets of the main cast, plus 2–3 pages and one card pair that together show the look. These get made first.

### Pages, endings and the cover
- `scene` (required): the moment, in 2–4 sentences. Who's where, doing what, and what the eye goes to first. Pick the moment the text is about, not a summary of the whole page.
- `place`: the bible place it's set in, by id (`brook`). Its look goes into the prompt, and the first page or ending set there becomes the **location reference** attached to the later ones. Leave it out for a place the bible doesn't have (a bedroom indoors).
- `setting`: where exactly in that place, in a few words ("the little wooden footbridge"), or the whole place when there's no `place`.
- `cast`: **every** character, extra and prop in the picture (from `visual.present` in the outline), by bible id, with their `state`: pose, expression, what they hold, where they are ("inside the lantern"), and how they differ from the sheet on this page (wet, holding an apple, door open). Never their looks again. A picture with 4 or more named characters is built in layers, one character at a time; a prop named in a character's state ("holding the lantern up") is added together with that character.
- `framing`: wide, medium-wide, medium, medium-close or close. Vary it across the story: wide for new places, close for feelings.
- `light`: follows the light arc. Name the warm light sources.
- `mood`: 2–4 words.
- `editFrom`: an earlier page or ending (an earlier card, for a card) in the same place, light and framing, where only the characters move (Gertie on the bridge, then Gertie bowing on the same bridge). The picture is then made by editing that one, which keeps the background identical. Only when the light and framing really match: a new light or a new framing is a new picture.
- `mustShow`: 2–3 things the picture fails without.
- `avoid`: anything specific to this page, especially the outline's `visual.mustNot` on merge pages.
- **The cover:** the hero and friend large in the upper two thirds, with the bottom quarter calm for the title. Show the promise of the story, not its ending.
- **Endings:** the key moment in the center (the badge is a circle cropped from the middle).

### Choice cards
- `subject` (required): **one clear subject** that shows the *option*, readable as a small thumbnail: the stepping stones, the bridge. Write it as a sentence starting with a capital letter.
- `background` (required): simple and calm.
- **Leave the hero out** unless the option needs them, and then show only part of them (red boots, a yellow sleeve).
- **The two cards of a fork must differ** in main shape and main color, so a child who can't read tells them apart (green tunnel vs orange pumpkins).
- No symbols: no musical notes, arrows, question marks or letters.

## Before you save
- [ ] Does every picture match the text of its page, and the visual state on merge pages?
- [ ] Is each card pair easy to tell apart at thumbnail size?
- [ ] Is the cast's state consistent from page to page (time of day, clothes, what they carry)?
- [ ] Does every page and ending in a bible place name it in `place`? Does each `editFrom` share the place, light and framing of its source?
- [ ] Does every state say only what happens, without describing anyone's look again?

Then run `uv run story check <id>` and fix every error. Run `uv run story assets` and check the story's own `REQUESTED_ASSETS.md`. Tell the user how many images and sheets were requested, and which ones to make first.
