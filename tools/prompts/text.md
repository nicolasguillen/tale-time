# Step 3: Text

version: 5

Write `content/stories/<id>/story/<lang>.json` in the first language (`story/en.json` unless the user asked for another): the whole story, every page, question, option label and ending title. Each language of a story is its own file; the others come at step 6. **Write the whole story in one go**, from the first page to the last ending, so it keeps one voice. A 4–7 story is about 1,200–2,000 words.

## Input
- `bible.json` and `outline.json` (both approved).
- The age band's words-per-page budget (`docs/story-format.md`).
- `craft/ages.md`: words, sentence length, feelings and dialogue for the band. `craft/values.md`: "Show, don't preach". `craft/structures.md`: opening hooks, page turns and ending techniques.
- Example: `content/stories/fox-and-the-lantern/story/en.json`.

## Output
`story/<lang>.json` matching `schema/story.schema.json`:
- `version: 1`, `ageBand` and `title` from the bible (the title in this language), `language: "en"` (or the first language the user asked for, the same as the file name), no `translatedFrom` (only translations have one), `cover: "cover"`.
- Every text is a plain string in this language.
- **Exactly the outline's nodes, kinds and links**, in the outline's order. `story check` fails otherwise. If the structure needs to change, change the outline first and tell the user.
- Image ids: each node's `image` is its node id. The cards are `<fork id>-a` and `<fork id>-b`.
- Questions and labels come from the outline, polished for reading aloud. Ending titles come from the outline too, but **the app never shows or says them** (2026-11-27): they're the ending's name for a screen reader and for the read-through, so keep them short.

## Writing for reading aloud
- **Words per page inside the band.** Aim for the middle of the range, not the edges.
- **Sentence length, vocabulary and feelings for the band** (`craft/ages.md`). Show a feeling through what the character does before naming it.
- Short sentences. One beat per page. Concrete, sensory words: what you'd see, hear and feel.
- **A hook on page 1**: a quirk, a want or a surprise, not a slow description.
- **Page turns:** where it helps, end a page on an unfinished moment and reveal it on the next.
- For 4–7 and 7–10, one or two delicious new words, made clear by context and used twice. **Make a new word clear on every path that meets it:** a word explained on one branch and used again after another branch needs its context again there. (In *Noelia and the Baby Squirrel*, *drey* was explained on the tiptoe branch, then turned up unexplained in the best ending, which the other branch leads to.)
- **Sound words** children can say along: *rustle, wibble-wobble, crick-crack, squelch*.
- **The hero's refrain** comes back 2–3 times along most paths, and once in a twist (sung loud, whispered).
- **An ending page closes the story by itself.** No title follows it: right after its last sentence the narrator says "The End.", then that there are more endings or more stories to find. So its last sentence is the story's last line: settled, warm and final (home, a hug, sleep, the refrain one last time), never a question, a cliffhanger or "what next?".
- **A fork page is heard again after an ending.** A "?" badge at an ending takes the book back to a fork the child read on the way and reads that page again, then its question. So every fork page stands on its own heard straight after an ending: it says who's there and what's at stake, and doesn't lean on the words of the page before ("Then she…", "After that…", "It"). Its question offers both ways fresh, with no "this time".
- The friend's **running joke** comes back on several pages.
- Dialogue tags stay simple ("said", "whispered", "called"). Name the speaker when two characters talk. Dialogue follows the band (`craft/ages.md`): repetitive for 3–5, natural and showing feelings for 4–7, distinct voices for 7–10.
- Every page makes sense if a child jumped in from either branch (merge pages especially). Don't refer back to anything that happened on only one path.
- A fork page sets up both options in the text before the question, so both are already in the child's mind.
- Endings close the story warmly in their last line and come back to safety, with the technique the outline gives them. The best ending has a small lasting gift ("every night after that, one little star winked at Mia's window"): a feeling or a gesture, never a prize.
- No moralizing: the value shows through what the hero does and what the child picks. **No ending's last line states a lesson.** No sarcasm, no scary words, no brands, no references to screens or phones.
- American English spelling unless the user asks otherwise.

## Before you save
- [ ] Read each path from start to ending. Does it flow, and does each merge page fit?
- [ ] Is every page inside the word budget? Are the sentences, words and feelings right for the band?
- [ ] Does anything break the bible's "never" list?

Then run `uv run story check <id>` and fix every error. Go straight on to the editor pass (step 4): the user reviews the text after it.
