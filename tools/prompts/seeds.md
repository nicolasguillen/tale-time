# Step 0: Seeds

version: 2

The user has no idea yet, or wants options. Propose story premises in the chat. Write no files.

## Input
- Age band (default 4–7) and how many (default 12).
- Optional: a value, a tone, a setting, a recurring cast to reuse from another story (only if the user asks: `from` in the bible).
- The craft guides: `craft/values.md` (the values, and which suit each band), `craft/structures.md` (the shapes, and which suit each band), `craft/ages.md` (the themes each band lives).
- `content/stories/*/bible.json`: what already exists, including each story's `craft.value` and `craft.structure`, so the new ideas don't repeat it.

## Output
A numbered list. Each item is one line in the shape *"a hero wants something, but an obstacle, in a place"*, followed by `value · shape · tone`, using the ids from the craft guides. For example:

> 3. A shy little dragon wants to join the frogs' swimming race, but she sinks like a stone, in a lily-pad lake. *self-acceptance · rule-of-three · funny*

## Rules
- Original: no existing characters, brands, real people or retellings of a specific known book or film.
- The hero is someone a child can be: a child, or an animal child, with a clear want.
- Built for choices about character (be brave or careful, help or explore, ask or try), with natural places where paths can split and meet again.
- The problem calls for the value, and the value can be chosen at a fork in two good ways (`craft/values.md`).
- Values rotate across `craft/values.md`: at most two seeds per value, and favor values and shapes no existing story uses yet. Only values, shapes and themes that suit the band (`craft/ages.md`).
- Tones mix: funny, gentle, adventurous. For 3–5, gentle and funny only.
- Diverse heroes and places across the list. No stereotypes.
- Nothing that needs a scary villain, a lost child, or a real danger to work.

End with one line asking the user to pick a number, or mix two.
