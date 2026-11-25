# Step 2: Outline

version: 2

Write `content/stories/<id>/outline.json`: the whole graph, with a one-line beat per page, every fork's question and options, every ending, and the visual state of every page. The structure of the story's text (`story/<lang>.json`, in every language) will have to match it exactly.

## Input
- `bible.json` (approved), especially its `craft` block.
- `craft/structures.md`: the arc on every path, pacing, the branching notes of the bible's shape and arc, and the endings. `craft/values.md`: the fork pairs for the bible's value. `craft/ages.md`: the band.
- The age band's budget, in `docs/story-format.md` ("Budgets per age band").
- Example: `content/stories/fox-and-the-lantern/outline.json`.

## Output
`outline.json` matching `schema/outline.schema.json`.

**Node ids:** `pNN-slug` for pages and forks, numbered by depth, with a letter for the branch (`p04a-hedge`, `p04b-garden`), and `eN-slug` for endings. Ids become permanent once published, so make them meaningful.

## Shape: branch and bottleneck
- **Never a pure binary tree.** Split, then **merge back** at a key moment, split again, and only split for good at the last one or two forks.
- The first fork comes by page 3 or 4.
- Keep each play-through inside the budget (pages and forks, counting the ending as a page). Endings: one `best`, the others `happy`, `cozy` or `silly`.
- **Stories are stateless.** A merge page can't know which way the child came. So the choices that lead to the best ending **must come after the last merge**, or they do nothing. The best ending takes two "right" choices (kind, brave, curious), for example asking for help and then leading.
- **Follow the bible's shape** (`craft.structure`). Its branching notes in `craft/structures.md` say what goes on the trunk, what goes on the branches, and where they merge.
- **The whole arc on every path:** the problem by page 2, the **low point on a trunk page** (usually the last merge), and the climax after the last merge. For a growth arc, the hero learns on the trunk, so they change on every path.
- **The hero solves the problem**, with help from friends. Nobody rescues them from nowhere.

## Choices
- About **character, not luck**: be brave or be careful, help or explore, ask or try, together or alone.
- **Both options sound fun.** No obviously wrong option, and none is punished. A "silly" ending is a funny mishap, never a failure.
- **The value, two ways.** At the forks that carry the story's value, both options are good forms of it (the fork pairs in `craft/values.md`), never its opposite. If someone needs help, both options help.
- The consequence shows up **on the next page or the one after**.
- `question`: one sentence, read aloud, ending in two options joined by "or" ("Should Mia hop across the stepping stones, or walk to the little bridge?").
- `label`: 2–4 words, the two starting differently, each understandable without the question ("Hop the stones" / "Walk to the bridge").
- `why`: the trait the option stands for, so the editor pass can check the choice means something.

## Merges and visual state
- `visual.time`: time of day and light. Plan a **light arc** across the story (sunset → twilight → night), so the pictures progress.
- `visual.present`: every character, extra and prop in the picture, by bible id, with their state in brackets: `"mia (dry boots)"`.
- **Branch characters leave before the merge.** A character met on one branch exits on that branch's last page, in its beat ("flutters off to bed").
- **On every merge page**, write `visual.mustNot`: what can't be shown because some path doesn't have it (a side character, wet clothes, a prop picked up on one branch, the moon before it rises).
- One beat per page. Every beat moves the story.
- Give each ending a technique from `craft/structures.md` (full circle, hope forward, quiet resolution, celebration) and say it in the beat.

## Before you save
- [ ] Walk every path in your head from the start: does each one make sense, especially through each merge?
- [ ] Does every path have the whole arc: a problem, a low point, a climax and a warm ending? Is the resolution unhurried?
- [ ] Does every ending close warmly and come back to safety (home, a grown-up, bed)?
- [ ] Does the running joke or the refrain come back at least twice on most paths?

Then run `uv run story check <id>`. It checks the graph (no dangling targets or cycles, everything reachable, two different targets per fork) and the budgets. Fix every error and run it again, up to three rounds. If it still fails, show the user the remaining errors.

## Showing it to the user
Run `uv run story paths <id>` (it reads the outline until `story/` exists), and give the user the shape in a few lines: how many pages, endings and play-throughs, where it splits and merges, and the choices in order. Then wait for approval or notes.
