# Review: Noelia and the Baby Squirrel

**Status:** v1 draft, English. The text is complete and passes `story check`. It's **waiting for your read-through**, the one gate that can't be skipped. Images are requested in [REQUESTED_ASSETS.md](REQUESTED_ASSETS.md), next to this file, and placeholders are in use until they arrive. [REQUESTED_AUDIO.md](REQUESTED_AUDIO.md), next to this file, already lists the narration clips, because it's generated from `story.json`. Don't record anything until you've approved the text: the clips regenerate whenever the words change.

| | |
|---|---|
| Age band | 4–7 |
| Pages | 20 (16 pages and forks, 4 endings), 54–78 words each, 1,352 words in all |
| Words | 408 different words. Short sentences, the longest 17 words. New words: *drey* and *hush*, each made clear by context and used twice. |
| Play-throughs | 16, each 12 pages with 4 forks (the top of the 4–7 budget) |
| Endings | best (The Whole Wood Says Hello), happy (Up, Up, Up!), cozy (Chitter at the Window), silly (Chitter-Chatter) |
| Images (after step 5) | 31 story images (cover, 20 pages, 10 choice cards) plus 4 reference sheets (Noelia, Pipa, Abuelo, Mama Squirrel) |
| To read every page and choice | 4 paths: `cd tools && uv run story paths noelia-and-the-baby-squirrel` |

## Editor pass

### Merge pages

| Merge page | Reached from | What was checked |
|---|---|---|
| `p06-path` | `p05a-tock-home`, `p05b-nibs` | Both side characters leave on the page before: Tock goes back to his tapping, and Nibs pops back into his hole. "That round hole wasn't Pipa's home" fits both (Tock's hole in the oak, Nibs's hole under the root). Pipa's tail droops here and on neither branch, so it isn't said twice. Abuelo catches up on both paths. |
| `p09-clearing` | `p08a-fluffballs`, `p08b-not-us` | The fluffball birds and the wood birds both fly off on the page before. "That didn't work either" and "she had zoomed and zoomed and zoomed" fit both tries. "In the hush" picks up p08b's hush but needs nothing from it. Mama Squirrel isn't seen until p11, and the drey is only in the picture (a clue for sharp eyes), not in the text. |

### Fixed in this pass
- **p02:** after Pipa jumps back, Noelia now says sorry and *tucks her hands behind her back*, so the page shows the right habit with a wild animal, not just the reach.
- **p04b:** split an 18-word sentence.
- **p05a:** Tock *squawks* first and laughs at the end, instead of laughing twice, so he's grumpy first and then softens, as the bible says.
- **p06:** dropped "slow" before "very slowly".
- **p11b, e4:** cut repeats of "Noelia".
- **e3 (best):** on the "still as a stone" paths, the word *drey* had never been explained, so it's now "their drey, a round, leafy nest high in the pine". The lasting gift now says Noelia comes "with Abuelo", so the ending is clearly with her grown-up.
- `outline.json` beats updated to match (p02, p04a, p04b, p05a, e2, e3). The structure is unchanged.

### Read-aloud qualities
- One beat per page, with page-turn reveals on every side branch: "And inside was…" → "…wood chips." "Out of its little round door came…" → "…one, two, three, four, five fluffball birds!" The low point ends "And in the hush, she heard…", and the next page opens "…chitter-chitter."
- Sound words: *squeak, tap-tap-tap, crunch, mmf, wibble-wobble, tsee-tsee-tsee, whirr, flutter, shh, chitter-chitter.*
- **Noelia's refrain**, "Ready, set, ZOOM!", comes 3–4 times on every path. It's cut off on p10 ("Ready, set, ZO…") and turns into a whisper on the tiptoe paths ("Ready, set… tiptoe").
- **Pipa's refrain**, "Home is up! Up, up, up!", comes on p02, p04b and in three of the four endings. e3 echoes it as "up, up, up".
- **The running joke** is that Abuelo stops for everything: a beetle (p01), a ladybug (p06), and sitting still on the stump (p09, and p11b, where he's "very good at it"). It turns around in the best ending, where Noelia says his line to him: "Mira, Abuelo. Look at that!"
- Something to count in a picture: the five fluffball birds (p08a).

### Choices
- None of the 5 forks has a wrong option, and none is punished. Both early tries on every path fail in a funny way, as the shape requires, and the consequence always lands on the next page.
- The forks before the last merge are about *how* she searches: asking or tracking, looking or calling. The ones after it are two kinds of patience: tiptoe or keep still, then whisper or chitter, watch or say goodbye.
- The **best ending** takes two choices after the last merge: *Still as a stone*, then *Keep watching*.

## Heart and shape
- **Value:** patience, with caring for nature and curiosity along. It's shown, never stated. Nobody tells Noelia to slow down. She hears the chitter only when she's finally still (p09), and on p10 she works it out herself: "Zooming feet were loud, and the chitter was so soft." No ending's last line is a moral.
- **Shape:** rule of three. Try one is on the first pair of branches (Tock's hole, or Nibs's hole), with a merge at `p06-path`. Try two is on the second pair (the fluffball nest, or the "Not us!" birds), with a merge at `p09-clearing`. The patient third try comes after the last merge, where the child's choices shape how it succeeds.
- **Low point on the trunk:** `p09-clearing`, the last merge, where Noelia sighs "I can't find it." Every path reaches it.
- **Growth arc on every path:** the change happens on `p10-chitter`, which every path shares: "Ready, set, ZO…", then she stops and sits back down. After that she's patient on every path.
- **Every ending shows it:** Pipa is home with Mama in all four. The best ending shows the most: the whole wood comes close, and Noelia now stops to look, like Abuelo. "Noelia did everything fast. Well… almost everything."
- **The hero solves it:** Abuelo only sits on the stump with an orange slice. He never points or explains. Noelia hears the sound and finds the drey herself.

## Safety rubric
It passes, and the bible's "never" list holds: nobody touches, feeds or carries Pipa, nobody climbs except animals, there are no hunters, nobody eats anything found in the wood, nobody scolds, Mama is never frantic, and Pipa is home on every path. I'd like your call on these points:

1. **Noelia reaches toward Pipa (p02).** She doesn't touch her: Pipa jumps back, and Noelia says sorry and tucks her hands behind her back. It shows the mistake and the fix in one beat. The alternative is no reach at all, so she just crouches and looks.
2. **Noelia runs ahead of Abuelo.** She stays on the loop path, and he's always in sight. The text says so on p03, p06, p08a and p09. On the branch pages she's a few steps ahead. Fine, or should he keep up more often in the text? *Pictures, decided 2026-11-24:* he used to be in every picture. Now he's out of frame on the side-branch pages whose text doesn't mention him (p04a, p05a, p04b, p05b, p07a), and he walks into view on p08a. He stays in p08b, which is an edit of p07b, where he's holding his cap on.
3. **Shouting in the wood (p07a, p07b).** The birds flutter off "to a quieter tree", and Pipa covers her ears. Nobody is hurt or scared for long, and it's played for laughs. It's also the natural consequence that makes the story work.
4. **Wild animals coming close.** In p11b, Mama Squirrel crosses the grass to Pipa a few steps from Noelia. In e3, Pipa scampers down to say hello on later visits. Nobody touches or feeds, but in real life you'd watch from further away. Keep it, or let e3's lasting gift happen from a distance ("a little tail waved from the tallest pine")?
5. **A Spanish-speaking family.** Abuelo and his "Mira, mira!" are a light, everyday touch, not the subject of the story, and "mira" is made clear by "Look at that!" right after it. If you'd like more of it (Noelia calling Pipa *ardillita*, say) or less of it, tell me.

## What this story taught us
- **Respell a name from another language the way that language says it** (Abuelo is ah-BWEH-loh, not ah-BWAY-loh). You corrected it by hand. Added to `tools/prompts/bible.md` (version 5).
- **Make a new word clear on every path that meets it.** *Drey* was explained only on the tiptoe branch, then came up unexplained in the best ending. Added to `tools/prompts/text.md` (version 3).
- **Low-point merge pages are the hardest to keep inside the budget**, because they carry the recap, the feeling, the grown-up and the turn. p09 came out at 82 words in the first draft. It isn't a rule yet, just worth watching.

## Your read-through
- [ ] Read the 4 covering paths (`uv run story paths noelia-and-the-baby-squirrel`, from `tools/`)
- [ ] Read two or three pages aloud for rhythm, p04a's one-breath question especially
- [ ] Decide the five safety points above
- [ ] Approve, or give notes in plain words ("make Tock funnier", "Pipa should talk more")

## Translation: Latin American Spanish (es)

**Status:** draft, translated from English on 2026-11-24 (`translate@1`). Waiting for a native speaker to read every path in Spanish. The Spanish narration waits for that too, and `audio/es/REQUESTED_AUDIO.md` says so at the top.

**The numbers:** 56–80 words per page (English: 54–78), 1,441 words in all (English: 1,352). Every page is inside the 4–7 budget. The best ending, e3, is right at the top (80).

**Names:**
- Kept: **Noelia**, **Pipa**, **Nibs** (said as written), and **Abuelo**: "el abuelo" in the narration, "Abuelo" when Noelia talks to him.
- Adapted: Tock → **Toc**, the Spanish spelling of the same sound (his tapping is *toc-toc*). Mama Squirrel → **Mamá Ardilla**. The fluffball birds → **los pajaritos pompón**.
- Places: Pinecone Wood → **el Bosque de los Pinos** (*piña* alone means pineapple in Mexico).
- Title: ***Noelia y la ardillita***.

**Refrains and sound words:**
- Noelia: "¡En sus marcas, listos, ZUUM!", which turns into "En sus marcas, listos... de puntitas".
- Pipa: "¡Mi casa está arriba! ¡Arriba, arriba, arriba!"
- Abuelo: "¡Mira, mira! ¡Mira nada más!" In English his "Mira, mira!" was the family's Spanish showing through. In Spanish it's simply how he talks, and "¡Mira nada más!" is a warm, very Latin American "look at that!".
- Sound words: tap-tap → *toc-toc*; chitter-chitter → ***chiqui-chiqui*** (Spanish has no word for a squirrel's chatter, and this one is easy to say and copy, which e2 needs); squeak → *¡iiic!*; crunch → *cronch*; tsee-tsee → *tsi-tsi*; whirr → *frrr*.
- Ending titles: "¡Arriba, arriba, arriba!", "Chiqui-chiqui-chá", "Todo el bosque dice hola", "Chiqui-chiqui en la ventana".

**Judgment calls:**
1. **"Drey"**: Spanish has no word for a squirrel's nest, so it's "un nido de ardilla", a round ball of twigs and leaves (p11a), and "su nido de hojas" (e3). The English new-word moment is lost; the clue in the picture stays.
2. **The ladybug** (p06) is "un bichito rojo con puntitos negros". *Mariquita* is an insult in parts of Latin America, and *catarina* and *vaquita de San Antonio* are regional.
3. **"¡Arriba, arriba, arriba!"** is plain Spanish for "up, up, up", but some grown-ups will hear a cartoon mouse's catchphrase in it. If that bothers you: "¡Para arriba, para arriba!".
4. **Noelia's sneakers** are "zapatos de rayos": *tenis*, *zapatillas* and *championes* each belong to different countries.
5. **e3 is at 80 words**, the top of the budget, after shortening ("los pájaros" instead of "los pájaros del bosque").
6. **Dialogue in quotes**, as in English and in the fox story, instead of Spanish dashes.

**Your read-through (Spanish):**
- [ ] Read the 4 covering paths in Spanish (`uv run story paths noelia-and-the-baby-squirrel --lang es`, from `tools/`)
- [ ] Read two or three pages aloud for rhythm, p04a's one-breath question especially
- [ ] Decide the six points above
- [ ] Approve: set `"status": "approved"` in `translations/es.json` and run `uv run story assets`. Or give notes in plain words.
