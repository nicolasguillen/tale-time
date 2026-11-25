# Review: The Fox and the Lantern

**Status:** v1 draft. The English text is complete and passes `story check`. It's **waiting for your read-through** (the one gate that can't be skipped, plan.md §5.5). Images are requested in [REQUESTED_ASSETS.md](REQUESTED_ASSETS.md), next to this file, and placeholders are in use until they arrive. Narration hasn't started because the voice isn't chosen yet.

This is the Phase 0 **story spike**: written by hand with Claude (bible → outline → text → editor pass, then shots), with no pipeline scripts, to learn what the prompts need.

| | |
|---|---|
| Age band | 4–7 |
| Pages | 20 (15 pages and forks, 5 endings), 54–70 words each, 1,211 words in all |
| Play-throughs | 18, each 8–11 pages with 3–5 forks |
| Endings | best (The Sky Parade), happy (Goodnight, Glim), cozy (One More Night), silly ×2 (The Big Splash, The Croakiest Song) |
| Images | 33 story images (cover, 20 pages, 12 choice cards) plus 5 reference sheets |
| To read every page and choice | 5 paths: `cd tools && uv run story paths fox-and-the-lantern` |

## Editor pass

### Merge pages

| Merge page | Reached from | What was checked |
|---|---|---|
| `p06-brook` | `p05a-moths`, `p05b-bramble` | The moths and Bramble both leave on the page before ("fluttered off to bed", "trotted off to bed"), so neither is in p06's text or picture. "A little dimmer" fits Glim on both paths. Tuck is there on both. |
| `p09-willow` | `p08a-across`, `p08b-goose` | Mia is dry on both paths (the splash path ends before the pond). Gertie stays at the bridge. Both pages end "toward the willow". The moon "peeks out" here and nowhere earlier. |

### Read-aloud qualities
- Short sentences, one beat per page, many sound words: *rustle, wibble-wobble, crick-crack, hooonk, squelch, ribbit, aroo*.
- The refrain, *"Little light, little light, show me the way tonight!"*, comes back on p05a and p10b, and turns silly in e4.
- The running joke is that Tuck's shortcuts are never short (p03, p04a, p04b, p08a).

### Choices
- None of the 6 forks has an obviously wrong option, and no option is punished. The worst outcome is a funny splash.
- Consequences show up on the very next page.
- The **best ending** takes two choices after the last merge: *Ask for help* (asking is a strength), then *Lead the parade*. Stateless stories can only reward choices made after the last merge.

### Heart and shape
Checked afterwards against the craft guides (`edit@2`), which came after the spike. Nothing in `story.json` changed.
- **Value:** kindness, with asking for help and friendship along (`craft` in the bible). It's shown and never stated: Mia carries Glim home, frees Bramble's apple and asks Gertie politely. No ending's last line is a moral.
- **Shape:** a quest. The stations are the merges (`p06-brook`, `p09-willow`), and each leg has two routes with their own obstacle and one-scene character. The problem appears on page 2 (Glim fell from the sky), and the hook is on page 1 (a glowing lantern in the grass).
- **Low point on the trunk:** `p09-willow`, the last merge. Glim is too dim to fly, so every path reaches it.
- **The child picks the value in two good ways:** at `p09-willow` both options help Glim (ask the pond friends, or sing the star song), and at `p10a-friends` both send Glim home (lead the parade, or wave goodbye).
- **Age fit (4–7):** 419 different words and about 9 words per sentence. The few long sentences are cascades that read well aloud ("higher and higher, until it was a star again").
- **Arc:** impact. Glim gets home in 4 of the 5 endings. Judgment call for you: in **`e5-windowsill`** (cozy), the fireflies leave without Glim, and the moon promises "tomorrow night". It's a warm *hope forward* ending, but the impact is left for tomorrow. Keep it, or let Glim float up from the windowsill at the end?

### Safety rubric
It passes. I'd like your call on these three points:
1. **A child in the garden at dusk.** Everything happens inside Grandma's garden. Grandma is on the porch, its light is always visible, and every ending goes home to her.
2. **Stepping stones and a splash ending.** p06 says the brook is "only ankle-deep", and the picture shows the pebbles. The splash is played for laughs and ends with a warm towel.
3. **Following a fox.** Tuck isn't a stranger: he's "the little fox who lived under Grandma's shed", a known neighbor.

Also handled: Mia moves the hedgehog's apple with a cabbage leaf and never touches the spines. Glim is never trapped (the bible's "never" list).

## What the spike taught us, for the Phase 1 prompts
- **Branch characters must leave before a merge.** The outline prompt should require that any side character introduced in a branch exits on that branch's last page.
- **The outline needs a visual state for every page**, including what must *not* be shown (`mustNot`: wet clothes at p09, the moon before p09). The shot list relies on it.
- **The best ending's choices must come after the last merge.** The outline prompt has to say so, or the "right choices" do nothing.
- **Write the bible's "never" list before the story.** It caught three problems early: a child alone at night (moved into the garden, with Grandma there), a stranger animal (made a known neighbor), and touching a hedgehog (a leaf instead).
- **Choice cards:** leave the hero out, or show only part of them (boots, a sleeve). The two cards of a fork need a different main color and shape. The asset doc now shows each card's partner.
- **Covering paths, not every path.** 18 play-throughs, but 5 paths cover every page and every choice. `story paths` prints them. Update plan.md §5.2's "5–8 distinct paths" to match.
- **Word budget:** 40–80 words suits this voice. Pages landed at 54–70 without forcing it.

## Your read-through
- [ ] Read the 5 covering paths (`uv run story paths fox-and-the-lantern`, from `tools/`)
- [ ] Read two or three pages aloud for rhythm
- [ ] Decide the three safety points above, and the `e5-windowsill` point under "Heart and shape"
- [ ] Approve, or give notes in plain words ("make p07b funnier", "Gertie should be a swan")

## Translation: Latin American Spanish (es)

**Status:** draft, translated from English on 2026-11-24 (`translate@1`). Waiting for a native speaker to read every path in Spanish. The Spanish narration waits for that too, and `audio/es/REQUESTED_AUDIO.md` says so at the top.

**The numbers:** 54–76 words per page (English: 54–70), 1,305 words in all (English: 1,211). Every page is inside the 4–7 budget.

**Names:**
- Kept: **Mia**, **Tuck** (the narrator says TUK, as Spanish reads it).
- Adapted: Glim → **Chispita** (Mia names the star in the story, so the name should mean something: a little spark, as *Glim* comes from *glimmer*). Bramble → **Zarza** (a bramble bush, prickly like the hedgehog). Gertie → **Gertrudis** (the full, old-fashioned Spanish form: a good name for a grumpy goose). Grandma → **la abuela**.
- Places and things: Willow Pond → **el Estanque del Sauce**; the lantern → **el farolito** (a *linterna* is a flashlight in most of Latin America).
- Title: ***El zorro y el farolito***.

**Refrains and sound words:**
- Mia's song: "¡Lucecita, lucecita, alumbra mi caminito!" (a loose rhyme, like the English one). The frogs' version: "¡LUCECITA, LUCECITA, CROAC-CROAC, CROAQUITO!"
- Tuck's running joke: "¡Y yo conozco un atajo!" ... "¡Ya casi llegamos!"
- Sound words: rustle → *¡cris, cras!*; whoosh → *¡fiuuu!*; hmm-mmm → *mmm-mmm*; crick-crack → *cric-crac*; ribbit → *croac*; Hooonk → *¡Cuaaac!*; Aroo → *¡Auuu!*; squelch → *chuic, chuic*. SPLASH, POP and Hop stay.
- The wobbly stone's "wibble, wobble" became "Se movía para acá. Se movía para allá." Spanish has no pair as funny. A better one is welcome.

**Judgment calls:**
1. **Tuck keeps his English name.** Spanish-speaking children read it TUK. If you'd rather adapt it: *Tito* or *Toto*.
2. **Bramble is "la vecina erizo"** (the hedgehog neighbor). *Erizo* has no feminine form, so this keeps her a she without an odd word.
3. **"Su colita"** for Mia's bottom in the splash ending: the gentlest word that children understand everywhere (*pompis* is Mexican).
4. **"Palomita de maíz"** for popcorn: understood everywhere, though each country has its own word (*pochoclo*, *cotufa*, *canchita*).
5. **Dialogue in quotes** ("¡Hola!", dijo Mia), as in English, instead of Spanish dashes (—¡Hola! —dijo Mia.). The app then shows every language the same way, and the word highlight never lands on a dash. Many Latin American picture books use dashes, so tell me if you prefer them.

**Your read-through (Spanish):**
- [ ] Read the 5 covering paths in Spanish (`uv run story paths fox-and-the-lantern --lang es`, from `tools/`)
- [ ] Read two or three pages aloud for rhythm, and the song in p05a, p10b and e4
- [ ] Decide the five points above
- [ ] Approve: set `"status": "approved"` in `translations/es.json` and run `uv run story assets`. Or give notes in plain words.
