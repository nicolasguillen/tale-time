# Story structures

version: 1

The shape of a story: how the problem rises and is resolved, and where the branches go. Used by the seeds, bible, outline, text and editor steps. The bible names the shape in `craft.structure` and the hero's arc in `craft.arc`.

Adapted from the kids-book-writer skill ([jamesrochabrun/skills](https://github.com/jamesrochabrun/skills/tree/main/skills/kids-book-writer), MIT). Its shapes are linear. Here each one says how it branches and merges (outline.md: branch and bottleneck, stateless merges).

## The arc on every path

Every play-through, from the first page to any ending, is a whole story:

1. **Opening:** meet the hero in their world. Page 1 has a hook.
2. **Inciting incident:** the problem or want appears, by page 2.
3. **Rising action:** tries and obstacles. The first fork comes by page 3 or 4.
4. **Low point:** the biggest obstacle, or the hero's feelings at their peak.
5. **Turning point and climax:** a new idea, or help arriving, and the hero acts.
6. **Ending:** the problem is solved, there's a new normal, and the last picture is warm.

**Branching rules:**
- **The low point is on a trunk page,** usually the last merge, so every path reaches it. In the spike, `p09-willow` (Glim is too dim to fly) is both the last merge and the low point.
- **The climax comes after the last merge,** where the child's last choices shape it.
- **No path skips a part.** A short path still has a problem, a low point (even a tiny "uh-oh") and a climax.

### Pacing a play-through

A 32-page picture book, scaled to one play-through (counting the ending as a page):

| Part | Share of the play-through | In a 10-page 4–7 path |
|---|---|---|
| Opening and inciting incident | the first 15–20% | pages 1–2 |
| Rising action: tries, obstacles, the first merges | up to about 65% | pages 3–6 |
| Low point, on a trunk page | about two thirds of the way | page 7 |
| Turning point and climax, after the last merge | the last pages but one | pages 8–9 |
| Ending: resolution, new normal, last picture | the last page | page 10 |

**Don't rush the resolution.** Solve the problem by the page before the ending, or early on the ending page, so the ending still has room for the new normal and a warm last line. In the app that last line is the last thing the story says ("The End." follows it, and no title): it must sound like an ending on its own. If an ending needs three beats, give it a page before it.

For 3–5, the low point is a wobble, not a fall: "Uh-oh!"

### Page turns

The page turn is a surprise machine. End a page on an unfinished moment and reveal it on the next one: "She opened the little door, and inside was…" → "a sleepy baby star!" A fork is the biggest page turn of all, because the child makes it happen.

## Shapes by age band

- **3–5: want → try → get.** Simple cause and effect, repetition, a quick happy resolution, no complicated feelings. One try per page.
- **4–7: problem and growth.** The hero has a personality trait, and the problem challenges it. Two or three tries, the hero grows a little, and the growth solves the problem. A simple inner conflict (shyness) next to an outer one (making a friend).
- **7–10: layered.** An outer obstacle shows an inner flaw. Failures raise the stakes, and the hero has to change to succeed. The resolution answers both the outer and the inner problem. A small subplot can live on one branch, but it ends before the merge, and its side characters leave there.

## The shapes

These are the ids `craft.structure` takes.

| Shape | Best for | In one line |
|---|---|---|
| `want-try-get` | 3–5 | Wants something, looks for it two or three times, gets it. |
| `rule-of-three` | 3–5, 4–7, 7–10 with growth | Try, try, succeed. |
| `quest` | 3–5, 4–7 | A journey with obstacles. The journey is the treasure. |
| `circle` | 3–5, 4–7 | Starts and ends in the same place, changed. |
| `cumulative` | 3–5 | A chain that grows page by page, then breaks. |
| `day-in-the-life` | 4–7, 7–10 | A small problem that grows from morning to bedtime. |
| `problem-solver` | 4–7, 7–10 | Creative solutions with funnier and funnier results. |
| `surprise-twist` | the top of 4–7, 7–10 | An expectation, clues, and a reveal that changes everything. |

### `want-try-get`
- **Pattern:** the hero wants something → looks or tries two or three times, with the same sentence pattern each time → gets it, or learns it's fine without it.
- **Branching:** each fork picks where to try next. Both tries miss in a funny way, and the merge page repeats the pattern line ("No honey here! Where can it be?"). The last forks pick how it's found.
- **Example:** Baby Bear wants honey. Not in the cupboard, not under the bed, not in the garden… and then a surprise.

### `rule-of-three`
- **Pattern:** a problem → the first try fails → the second try fails → the third try works → a celebration.
- **Why three:** young children spot the pattern, it builds expectation, and it's neither too short nor too long.
- **Branching:** it fits branch and bottleneck naturally. Tries one and two each happen on a branch (the two options are two different tries), with a merge after each. Try three comes after the last merge, so the child's last choices shape how it succeeds. A merge page can't name the try that just happened: "That didn't work either."

### `quest`
- **Pattern:** the hero has to go somewhere or find something → a journey with obstacles, each one teaching something → they arrive → the journey itself mattered.
- **Branching:** the stations are merge pages. Each leg between them is two routes, each with its own obstacle and one-scene character. The arrival is the last merge. The spike, *The Fox and the Lantern*, is a quest.

### `circle`
- **Pattern:** start and end in the same place, with the hero or the place changed.
- **Branching:** every ending comes back to the opening place. That gives you the "come back to safety" rule for free. Each ending echoes the first line, with a change: "Every morning, Sophie looked out of her window at the birds." → "Every morning, Sophie looked out of her window. But now, she flew with them."

### `cumulative`
- **Pattern:** one thing, then two, then three, with the whole chain repeated on every page → a climax breaks the pattern → it all resolves at once, or in reverse.
- **Branching:** a stateless merge can't remember which link a branch added. So build the chain on trunk pages, and let forks choose *how* the next link joins (with a hop, or with a roll), not *which* link, so every merge page can repeat the whole chain. The last forks, after the last merge, can add a different final link for each ending.
- **Endings:** the pattern breaks differently in each one: the sneeze, the "POP!", the wake-up.

### `day-in-the-life`
- **Pattern:** the morning starts normally → a small problem → it grows through the day, getting funnier → in the evening it's resolved, often naturally or with help → bedtime and a thought about the day.
- **Branching:** the times of day are merge pages, and the light arc comes built in. The problem grows on the trunk, so every path sees it grow. The branches are the day's episodes.
- **Endings:** mostly cozy and happy, at bedtime.

### `problem-solver`
- **Pattern:** a problem → creative solution one, with a funny result → solution two, more creative, with a funnier result → solution three, over the top, with the funniest result → the simple solution works, or accepting the "problem" is the answer.
- **Branching:** each fork offers two creative solutions, and each result lands on the next page. Silly endings are the natural home of the over-the-top solution. The best ending finds the simple one.

### `surprise-twist`
- **Pattern:** set up an expectation → build on it → slip in small clues → the twist → a new understanding, and a laugh.
- **Branching:** the twist lands on a trunk page after a merge, so every path gets it. Every path passes at least one clue: put clues on trunk pages, or one on each side of a fork. Different branches can hold different clues, so playing again finds new ones. That's what branching is for.
- **Watch:** the twist is funny or warm. It's never a scare.

## Character arcs

These are the ids `craft.arc` takes.

### `growth`: the hero changes
- **Pattern:** a small flaw or fear (impatient, shy, a show-off) → the problem makes the hero face it → they struggle and fail → they learn → they succeed because they changed. For example: selfish → generous, scared → brave, impatient → patient, closed → open.
- **Branching:** the learning happens on the trunk, at or before the last merge, so every path has it. After that, the forks choose how the hero uses what they learned. **No path leaves the hero unchanged.** A silly ending still shows the growth, just with a splash.

### `realization`: the hero finds out what they really need
- **Pattern:** the hero thinks they want X → goes after X → realizes they need Y → turns to Y → is happy. For example: wants to be popular, but finds a true friend matters more; wants to win, but finds trying their best matters more.
- **Branching:** the realization lands on the last merge page, or in every ending. The endings differ in how Y is found.

### `impact`: the hero changes the world around them
- **Pattern:** the hero is fine, but someone or something isn't → the hero acts → their action helps others → the world is a little better.
- **Branching:** every ending shows the impact, and the best ending shows the most of it. In the spike, Mia gets Glim home on most paths. In `e5-windowsill`, the trip home waits until tomorrow.

## Endings

Pick a technique for each ending. Every ending comes back to safety (outline.md).

| Technique | Fits | For example |
|---|---|---|
| **Full circle:** echo the opening, with a change | best, happy | "Every morning, Sophie looked out at the birds. But now, she flew with them." |
| **Hope forward** | happy, cozy | "Tomorrow there would be new adventures. But tonight? Tonight was perfect." |
| **Quiet resolution:** a feeling, said simply, not a moral | cozy | "And as the moon rose, Pip felt warm all the way down to her toes." |
| **Celebration** | best, silly | "And they all danced until the moon came up. Well, until the next adventure, anyway!" |
| **Open door** | happy, only in a series | "But that's a story for another day…" |

## Opening hooks

Page 1 starts with a quirk, a want or a surprise, never with a slow description.

- **Adventure:** "The map showed an X right where Tomas's house was. This was going to be the best treasure hunt ever!"
- **Friendship:** "Ada had exactly zero friends at the new pond. But that was about to change."
- **Problem:** "When Bo woke up that morning, everything was blue. Including Bo."
- **A feeling:** "Lulu's grumpy was so big it filled the whole room. Maybe the whole house."
- **Bedtime:** "As the stars came out over the sleepy hill, Pip settled down for the night…"

## Mistakes to catch

| Mistake | Fix |
|---|---|
| **No clear problem:** things happen, then it ends. | A goal or problem by page 2. |
| **Too many problems:** save the world, make a friend and clean the room. | One main problem, one main value. |
| **Rescue from nowhere:** a magic fairy fixes everything. | The hero solves it. Help from friends is fine, being rescued isn't. |
| **Rushed resolution:** nine pages of problem, one line of answer. | Balance the build-up and the payoff (pacing above). |
| **No stakes:** nothing matters to the hero. | Make the reader care what happens to them. |
| **A path that skips part of the arc** | Move the low point to a trunk page. |
| **A merge page that only works after one branch** | Rewrite it so it fits every way in (outline.md). |
| **A growth arc that only completes on some paths** | Move the learning to the trunk. |

> "Structure is the skeleton. Character is the heart. Your voice is the soul."
