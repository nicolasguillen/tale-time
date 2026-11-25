# Age guidelines

version: 1

What children in each age band can follow, feel and enjoy. Used by every writing step. The size budgets themselves (words per page, pages, forks, endings) live in `tools/storytool/budgets.py` and docs/story-format.md, and `story check` enforces them.

Adapted from the kids-book-writer skill ([jamesrochabrun/skills](https://github.com/jamesrochabrun/skills/tree/main/skills/kids-book-writer), MIT), with three changes:
- Its finer ages (2 to 9) are mapped onto the app's three bands.
- Its reading levels became **listening** levels. The app narrates every page, and the child may read along with the word highlight. Phonics and decodable text are left out.
- Where it allows more than this project does, **the project's rules win**: plan.md §5.3 and §5.5, and the bible's "never" list.

## Quick reference

| | 3–5 | 4–7 | 7–10 |
|---|---|---|---|
| Listens like (the skill's ages) | 3–4 and 4–5 | 4–5, 5–6 and 6–7 | 7–8, 8–9 and up |
| Words per page (budget) | 15–40 | 40–80 | 80–150 |
| Words per play-through (from the budget) | about 90–320 | about 320–960 | about 960–2,700 |
| Different words in the whole story | 100–250 | 300–600 | 600–1,200 |
| Words per sentence | 3–8, one idea each | 5–12, about 9 on average, a few longer | 8–18, with short ones for punch |
| Characters talking on one page | 1–2 | 2–3 | 3–4 |
| Feelings to name | happy, sad, cross, a bit scared, excited, sleepy | adds proud, worried, shy, surprised, lonely, and briefly frustrated or disappointed | adds embarrassed, jealous, nervous, determined, sorry, torn |
| Attention span for a sitting | 10–20 minutes | 15–30 minutes | 30–60 minutes |

These numbers fit together. The skill's word counts per book match the budgets per play-through, and the spike (4–7) has 1,218 words, 419 different words and about 9 words per sentence. A play-through should take well under the attention span, so there's time to play again and find another ending.

## 3–5

**Who's listening.** They know familiar things and people, follow simple cause and effect, and follow a story with a beginning, middle and end. They love repetition and knowing what comes next. They ask "why?" a lot and are starting to play pretend. They can't follow complicated plots, jumps in time ("yesterday", "meanwhile"), or many characters at once.

**Words.** Concrete nouns (ball, mama, boots), action verbs (hop, splash, hug), simple describing words (big, red, soft) and simple places (in, on, under). Familiar things first, and one new thing at a time.

**Sentences.** "The frog jumps." "Where is the ball? There it is!" One to three sentences per page.

**Patterns.** Lines that repeat, call and response ("'Hello!' said the cat. 'Hello!' said the dog."), sound words, counting to five, colors and shapes, things to find in the picture.

**Themes.** Daily routines (meals, bath time, bedtime), family and pets, sharing toys, being kind to a friend, trying something new, first experiences (a haircut, a new friend), simple feelings, animals and nature.

**Shapes.** `want-try-get`, `rule-of-three`, `cumulative`, `circle`, a short `quest`.

**Never.** Monsters that aren't silly, a parent leaving, any real danger, darkness without comfort, complicated feelings (jealousy, embarrassment), small picture details the story depends on, more than two or three characters to keep track of.

## 4–7 (the default band)

**Who's listening.** They follow stories with several steps, and understand consequences ("if… then"). They love humor and silly situations, mostly know make-believe from real, make predictions, and care about rules and fairness. Their empathy is growing, and they may have fears (the dark, monsters, being apart). At the top of the band they understand motives, make simple inferences, and start reading simple words.

**Words.** Everyday words, plus one or two delicious new ones per story, made clear by context and used twice ("The elephant was enormous, so big she couldn't fit through the door"). Feeling words and sound words. Keep the hero's refrain in short, common words, so a child who's starting to read can read it along with the highlight.

**Sentences.** "Max loved to build towers. Every day after school, he stacked his blocks higher and higher." Two to five sentences per page.

**Dialogue.** Natural, and driven by character. It shows feelings, reveals personality and moves the story on:
> "I'm scared of the dark," whispered Maya. "Me too," said her teddy bear, Buttons.

**Themes.** Making friends, first days, small fears, being helpful, learning a skill, being yourself, honesty, trying again, simple fairness, growing independence, brothers and sisters.

**It can include** mild conflict, resolved within a page or two; mild suspense with a happy ending; silly monsters; a short, safe separation (school, a grown-up in the next room); more complicated feelings; a hero who grows.

**Interaction.** Guessing what's behind the page turn, saying the refrain along, finding and counting things in the picture.

**Shapes.** `rule-of-three`, `quest`, `circle`, `problem-solver` and `day-in-the-life`; `surprise-twist` at the top of the band; `cumulative` only if it's short and funny.

**Never.** Death, realistic violence, a lost child, real danger.

## 7–10

**Who's listening.** They follow complicated plots and several threads, understand motives, inference and simple metaphors, hold two points of view, and think logically and critically. They enjoy mysteries, humor that comes from character, and being trusted with nuance. Many read along on their own.

**Words.** A richer vocabulary, figurative language ("her stomach did a flip-flop"), words with more than one meaning, and new words made clear by context.

**Sentences.** Mix longer descriptive sentences with short, punchy ones, and show the hero's thoughts:
> Maya's stomach did a flip-flop as she walked into the lunchroom. Yesterday, she'd spilled juice all over herself. In front of everyone.

**Voice.** A first-person hero can work, because kid logic is funny ("I'm seven and three-quarters, which means I'm practically a teenager"). Each character gets a voice of their own: careful Chloe, eye-rolling Jake, Mia with the plan. Dialogue can carry subtext and group dynamics.

**Themes.** Complicated friendships, identity and belonging, standing up for what's right, small ethical dilemmas, mistakes and making amends, teamwork, brothers and sisters, simple mysteries, science and nature, change (moving house, a new baby, a friend moving away).

**It can include** moral complexity where both sides have a point, several points of view, inner conflict, longer arcs, a subplot on a branch, and suspense with a safe resolution.

**Shapes.** `problem-solver`, `surprise-twist`, `quest`, `day-in-the-life`, and `rule-of-three` with a growth arc.

**The guide allows more than this project.** It suggests loss handled gently, historical hardship and social issues for 8–9. This project keeps peril mild (plan.md §5.3): no death, injury or abandonment. Grief, illness, divorce and real-world politics only appear if the user asks for that story, and then they're a judgment call in `review.md`.

## Feelings

- Name only the feelings the band knows (quick reference). A younger band's feelings are always fine in an older band.
- **Show a feeling first, then name it:** shoulders droop, a voice wobbles, a tail stops wagging. The picture can show it too, so give it to the shot (`state` in shots.json).
- Every big feeling is resolved before the ending, or on the ending page.

## Interaction that works in the app

- Refrains and sound words to say along (every band).
- Guessing what's behind the page turn (structures.md).
- Finding and counting things in the picture (3–5 and 4–7). Name them in the text and put them in the shot's `mustShow`.
- The forks: the child is the one who decides. Lift-the-flap and touch-and-feel don't apply.

## Before writing

- [ ] Do the vocabulary and sentence length suit the band?
- [ ] Are the ideas concrete enough (younger bands) or rich enough (older ones)?
- [ ] Are the feelings ones the band knows?
- [ ] Does a play-through fit well inside the attention span?
- [ ] Are the themes part of this age's own life?

> "Meet children where they are, then invite them one step forward."
