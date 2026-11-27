# Page turn

How the reader moves from one page to the next: a corner that folds up when a page is read, a page that lifts like
paper when the child pulls it, and a book that flips by itself after a choice. Agreed on 2026-11-26 from an
interactive sketch ([private sketch](https://claude.ai/artifact/9P7qBuYQTZUfB47JPp1E13), placeholder pictures, no
narration). The numbers below are the sketch's, in dp; tune them on devices, not in this file first.

## What changes

| Today | With this change |
|---|---|
| A round "next" arrow pulses at the bottom right once the page is read | The page's bottom-right corner folds up and shows the next page beneath it, with a white arrow on the fold |
| Tapping "next" cross-fades to the next page | Tapping the corner, or dragging it, lifts the whole page to the left and uncovers the next one |
| Tapping a card cross-fades to the next page | The chosen card glows, sparkles, and the book flips through a few glowing pages by itself to the new page |
| A "back" arrow at the bottom left | No back arrow, no going back a page |
| A sunny 60 dp home button | A small, see-through 44 dp home button |

Everything stays in the shared Compose code, the same on Android and iOS. No new library, no image or sound files:
the fold, its shadows and the sparkles are drawn in code as UI effects, not story art.

## 1. The folded corner

- **When.** The corner folds up at the moment the arrows come today: once the page is read (its last word has faded
  in and the narrator has said it, `ReaderPage.readMillis`). Only on a plain page (a `Page` node), never on a fork or
  an ending (§5).
- **Shape.** A dog-ear: the crease runs from a point on the bottom edge to a point on the right edge, and the corner
  folds over it. The crease meets the bottom edge 118 dp from the corner and the right edge 96 dp up from it on a
  phone; 150 dp and 122 dp on a tablet (shortest side ≥ 600 dp).
- **Coming in.** It grows from nothing to its size in 520 ms with a small overshoot (ease-out-back, overshoot 1.5).
- **Breathing.** While it waits, it grows and shrinks by 5% every 2.6 s, so it invites a tap without pulsing like a
  button.
- **What shows.** The back of the page on the flap (§3), and the next page in the uncovered corner, in shade (§3). The
  next page is really there, laid out, but without its picture: a page's picture comes in only once the page is in
  front and lying flat, then fades in (500 ms). So a turn never draws a picture beneath it, on every frame (§10).
- **The arrow.** White, the `TaleIcons.Next` arrow, 40 dp, stroke 3.2 on its 24-unit grid, with two soft dark shadows
  so it reads on paper and on any picture: (0, 1 dp, blur 1.5 dp, `#281C14` at 85%) and (blur 6 dp, `#281C14` at
  50%). It sits at the incenter of the visible flap triangle and points the way to pull: to the left and a little up
  (the direction (−1, −0.45), about 204°). It nudges 4 dp back and forth along that direction every 1.4 s. It fades in
  once the fold is 60% grown, and fades out (300 ms) as soon as a finger touches the corner.
- **Room.** The bottom of the text panel keeps the room the arrows row has today (68 dp on a phone, 80 dp on a
  tablet, plus the 8 dp gap above it), empty, so no word ever sits under the fold.

## 2. Turning the page

- **Where to touch.** A square at the bottom-right corner of the screen, 132 dp on a phone and 160 dp on a tablet,
  only while the corner is folded up. It covers the flap and the uncovered corner.
- **Tap.** A touch that moves less than 10 dp and lasts less than 450 ms turns the page.
- **Drag.** The corner follows the finger, keeping the offset between the finger and the corner from the moment it
  touched (so the fold never jumps). It eases toward the finger (45% of the gap per frame). On release:
  - it turns if the corner is in the left 55% of the screen, or the finger was flicking left faster than
    0.35 dp/ms (smoothed over the last moves);
  - otherwise it settles back into the folded corner in 280 ms (ease-out-cubic), and the arrow comes back.
- **The page stays bound at its left edge**, like a book's spine, so it never tears off: the corner is kept within
  one page width of the bottom-left corner, and within one page diagonal of the top-left corner. It never goes below
  the bottom edge or right of the right edge.
- **The turn.** The corner travels along a quadratic curve from where it is to (−width, height), where the whole page
  has folded over to the left, off screen. With `span` = (corner x + width) / (2 × width), the curve's control point
  is halfway along in x and `0.3 × min(width, height) × span` above the higher end, so the page lifts in an arc. It
  takes `300 + 450 × span` ms: ease-in-out-sine after a tap, ease-out-cubic after a flick (the page keeps the
  finger's speed). About 0.7 s from the folded corner.
- **The whole screen is the page.** In portrait and in landscape alike the picture and the text turn together. The
  home button stays put above everything.
- While a page turns, nothing else takes a touch except the home button.

## 3. Drawing the fold

A flat fold, with a straight crease. A rounded curl would need a GPU shader, which Android only has from 13 (the app
supports 8): maybe later.

The corner C (bottom right) has moved to P. The crease is the line halfway between C and P, at right angles to C→P.
Let `n` = (C − P) / |C − P|, `M` = (C + P) / 2, `len` = |C − P|.

- **The page** keeps the part on the far side of the crease from C: `(X − M) · n ≤ 0`. The rest is clipped away.
- **The flap** is the cut-off part, `(X − M) · n ≥ 0`, mirrored across the crease. With `d` = (−n.y, n.x),
  `a = d.x² − d.y²`, `b = 2 d.x d.y`, the reflection is `X' = R X + t` with `R = [[a, b], [b, −a]]` and `t = M − R M`.
  It's painted as the back of the paper:
  - a new token `TaleColors.PageBack` = `#F9F3E7`, slightly lighter than the paper;
  - the page itself at 7% opacity, so it shows through mirrored, like ink through paper;
  - a shading gradient from the crease toward the flap's tip (over `len / 2`): `#3A2C24` at 30% in the crease, warm
    white `#FFFCF5` at 55% about 9 dp in (at most 20% of the way), `#FFFCF5` at 12% from 45% of the way, `#3A2C24` at
    10% at the tip;
  - a soft shadow around the flap: blur 7 dp, `#281C14` at 40%.
- **The next page**, under the cut-away part, is shaded by the lifted flap:
  - a shadow along the crease, from `#281C14` at 58% in the crease to `tint` at `w = clamp(0.35 × len, 16, 90)` dp
    from it, then `tint` over the rest of the uncovered part, where `tint = 0.2 × clamp(1 − len / (1.5 × width), 0, 1)`.
    So the folded corner is clearly in shade, and the shade lightens as the page lifts higher;
  - all of it fades out over the last 35% of the way: multiplied by `clamp((P.x + width) / (0.35 × width), 0, 1)`.

The page is recorded once per frame into a `GraphicsLayer` (`rememberGraphicsLayer()`), then drawn twice: clipped as
the page, and mirrored into the flap. The fold math lives in a pure file (§9) and is unit-tested.

## 4. Choosing a card: the book flips by itself

A choice is the moment the child builds their own story, so it's the one page change that's magical. One tap on a
card picks it, as today. Then:

1. **The chosen card lights up** (550 ms): it grows to 114%, then settles at 110%, with a gold glow
   (`LanternGold` ring 6 dp at 60%, and a 40 dp glow at 80%, settling to 4 dp at 50% and 30 dp at 65%).
2. **The rest steps back** (350 ms): the other card and the question fade out and shrink to 90%.
3. **Sparkles burst from the card:** 36 at once from its center, then 2 per frame around its edge for 700 ms.
4. **At 700 ms the book riffles by itself:** the fork page turns from its corner, then three blank leaves, faster
   each time, and the last turn uncovers the new page. Turn lengths: 620, 420, 330 and 380 ms, each from the
   bottom-right corner along the curve of §2 (ease-in-out-sine). The leaves are paper with a soft gold glow in the
   middle (a radial gradient, `LanternGold` at 30% at the center to 0 at 62% of the way out), and their backs are
   painted like any page's back. Gold dust flies off the crease all along: 3 sparkles per frame from random points
   on the crease, moving the way the flap goes (−n, give or take 0.7 rad).
5. **The new page lands:** its picture fades in (it isn't drawn under the leaves) while 28 sparkles drift slowly down
   over it (top 45% of the screen) and fade. Then its words and its narration start (§6).

About 2.5 s from tap to the first word. No gleam or shimmer across the new picture (tried and dropped).

**Sparkles.** Drawn on one `Canvas` above the pages and below the home button. 60% are four-pointed twinkles (four
quadratic curves through the center), the rest small dots. Colors: `LanternGold`, amber `#ECA030`, warm white
`#FFFAEB`, each with an amber glow (blur 8 dp, `#ECA030` at 90%). Each lives 0.5 to 1.5 s, fades in over 80 ms and
out over its life, twinkles (its opacity waves ±35%), shrinks by 40%, slows down (×0.985 per 16 ms) and falls a
little (0.0003 dp/ms²). Sizes 3 to 15 dp.

## 5. Forks and endings

No folded corner on a fork or an ending: what comes next is on the same page.

- **Fork.** Once its text is read, after a 900 ms pause, the question and the cards rise on the same page (today's
  slide-in), and the narrator reads the question and the labels as today. The screen sends `ReaderEvent.Next` by
  itself to open them.
- **Ending.** Once its text is read, after a longer pause (1.6 s, so the last line lands), the closing rises on the
  same page, one part after the other as the narrator says it (`Closing`, 2026-11-27): "The End" with 40 sparkles
  drifting down over the page; every ending's badge in one row (as big as the row allows, this one a third bigger
  with a soft gold glow; a new one turns from "?" into its picture with a pop, a burst of sparkles and a "New!"
  ribbon); the closing line, while the "?" badges wiggle now and then; and last the buttons. The ending's name isn't
  shown or said. Without a clip, each part waits a moment (`THE_END_MILLIS`, `BADGES_MILLIS`, `OUTRO_MILLIS`).
- **Back to a fork** (a "?" badge, or "Try another path"): the ending is held as it was while blank leaves come back
  over it from the left, the way a turn took them (`PageTurn.returnNow`: a turn's arc run backward), one for each page
  read since the fork, up to three (330, 380, 430 ms), then the fork page itself (640 ms, ease-out-cubic), with gold
  dust off the crease going right. Only the leaves and pages read on this path ever show. The fork page is then read
  again (`landing` until `Landed`, as after a choice), then its cards rise. After a "?", the card that leads toward
  that ending has a gold arrow pointing at it (`TaleIcons.Next` at 64 dp with a soft dark shadow, nudging 6 dp toward
  the card every 650 ms): above it, pointing down, when the cards are side by side; beside it when they're stacked. At
  every fork until an ending is reached. With reduced motion it's a
  cross-fade.
- **Other page changes** keep today's cross-fade: opening the reader, switching the story's
  language.

## 6. No back arrow

The back arrow goes, on pages and at forks. A child can't go back a page: forward, and back to a fork (a "?" badge
or "Try another path", §5) at an
ending.

- Remove `ReaderEvent.Previous`, `ReaderUiState.canGoBack`, `PageArrows` and `Strings.previousPage` (both languages).
  `Reading.previous()` goes too if nothing else uses it. `Reading.back()` stays ("Try another path").
- Once a fork's cards are out, the text isn't shown again.

## 7. The home button

In the reader it sits on the picture, so it gets quieter: a 44 dp circle (the smallest comfortable touch target on
both platforms), `Cream` at 78% (no backdrop blur: Compose can't blur what's behind it on both platforms), the house
icon in `InkSoft` at 22 dp, a light shadow (1 dp). Placed at the safe-drawing insets plus 14 dp. It works at any
moment, even during a turn or the magic.

## 8. Narration and the reading clock

- **A page's words and narration start once it's in front and lying flat**, never while it's beneath a turning
  page. The reading clock (`rememberReadingClock`) starts then, not when the page is composed beneath.
- **Corner turn.** The screen sends `ReaderEvent.Next` when the turn is over, not when it starts, so a page dropped
  back into the fold changes nothing. The page that was beneath becomes the current page without being composed again
  (keyed by node id); its picture loads then and fades in. Then the narration starts as today (the 400 ms breath, then
  the clip).
- **Choice.** `TapChoice` commits at once, as today: the path is saved, so if the app is killed during the magic it
  reopens on the new page. But the view model holds the new page's narration until the screen says it has landed:
  - new `ReaderEvent.Landed`, sent when the last leaf has turned;
  - new `ReaderUiState.landing: Boolean`, true from `TapChoice` to `Landed`. While it's true the clock waits at 0 (as
    with `Voice.WAITING`), and nothing is read aloud;
  - the screen keeps the fork page it was showing and draws it on top until the riffle is over;
  - if the screen is recreated meanwhile (the device turns), it shows the new page flat and sends `Landed` at once.
- **The next page beneath the fold** comes from a new `ReaderUiState.upcoming: ReaderPage?`: what `reading.next()`
  would show, set when the current node is a `Page`, null otherwise. `Reading` is immutable, so this doesn't move
  anything.
- **Auto mode** (later, plan.md §3.2) turns the page by itself with the same tap turn.
- **No sound.** A page-turn sound would be an audio file the user delivers; out of scope here.

## 9. Accessibility, reduced motion, system gestures

- **Screen readers.** While the corner is up, it's a button labeled `Strings.nextPage` ("Next", "Siguiente"); a
  double tap turns the page (the tap turn). The cards stay buttons with their labels.
- **Reduced motion.** New `platform` expect/actual `reduceMotion(): Boolean`: Android, the animator duration scale is
  0 (`ValueAnimator.areAnimatorsEnabled()` is false); iOS, `UIAccessibility.isReduceMotionEnabled`. When it's on:
  the corner folds without growing in or breathing, the arrow doesn't nudge, a turn is today's 450 ms cross-fade, and
  a choice is the card's glow (no sparkles, no riffle) then the cross-fade.
- **System gestures.** On Android with gesture navigation, a drag from the right edge is the system's back gesture
  and from the bottom edge goes home. Exclude the corner's square from system gestures while it's up
  (`Modifier.systemGestureExclusion()`, Android only, through a `platform` expect/actual modifier; Android allows up
  to 200 dp of exclusion per edge). On iOS, check on a device that the home indicator's swipe doesn't fight the drag;
  if it does, defer the bottom-edge system gestures in the iOS host
  (`preferredScreenEdgesDeferringSystemGestures`).

## 10. Implementation

In `shared/src/commonMain/kotlin/com/nicolasguillen/taletime/`:

- `feature/reader/PageFold.kt` (new, pure: floats and `Offset`): the fold of §3 (`keep` and `flap` polygons, the
  reflection, `len`, `n`, `M`), the dog-ear's corner for a crease (§1), the spine constraint (§2), the turn's curve.
  Unit-tested.
- `feature/reader/PageTurn.kt` (new): the page stack (the page in front, the page beneath, the leaves during the
  riffle), the corner's touch handling and its arrow, and the drawing of §3.
- `feature/reader/Sparkles.kt` (new): the sparkles of §4 on one `Canvas`, driven by `withFrameMillis`.
- `feature/reader/ReaderScreen.kt`: the page `AnimatedContent` gives way to the page stack; `PageArrows` goes; forks
  and endings open by themselves (§5); the choice card's glow and step-back.
- `feature/reader/ReaderViewModel.kt`: `upcoming`, `landing`, `Landed`; `Previous` and `canGoBack` go (§6, §8).
- `designsystem/TaleColors.kt`: `PageBack` (`#F9F3E7`), `FoldShadow` (`#281C14`), `Amber` (`#ECA030`),
  `Sparkle` (`#FFFAEB`).
- `designsystem/Components.kt`: the quiet home button of §7 (see open questions for the other screens).
- `i18n/Strings.kt`: `previousPage` goes.
- `platform/`: `reduceMotion()` and the gesture-exclusion modifier (§9).

### Drawing it cheaply (profiled on an iPad 7th generation, 2026-11-26)

On older iPads, Skia draws a stroked outline, a wide stroke or a letter's outline in software: it rasterizes a mask on
the CPU and uploads it as a texture, every frame. So the fold and the reader draw only what the GPU draws by itself:

- the page beneath the fold has no picture (§1): its picture is loaded, drawn and slowly zoomed only once it's in
  front and lying flat;
- the flap's soft shadow is the flap filled a few times, bigger and fainter, not its outline stroked;
- the arrow on the fold and the sparkles are images drawn once (with their shadows and glows, a twinkle in each of
  five turns), then placed each frame by their rectangle alone: one image call per sparkle, no transform;
- the chosen card's glow is its rounded shape filled behind it, bigger and fainter, not wide strokes;
- the words not read yet are hidden with rectangles over their lines, not with their letters' outlines; and a text
  layer is offscreen only while some words have come and some haven't;
- each page's content has its own layer, so a frame of the fold only draws it again, and the fold's geometry is
  worked out once a frame.

Measure on physical devices only, and without a UI test: XCUITest turns on Compose's accessibility tree, which then
costs the app about a fifth of its main thread and a frame or two per query. Launch the app with the agent switches
(`platform/AgentFlags`, §11) so it turns its pages by itself; it then prints, every 10 s, the frames it drew and missed
while a page turned and the rest of the time (`xcrun devicectl device process launch --console …` shows them). Record it
with Instruments too ("Time Profiler", `xcrun xctrace record --device <id> --all-processes`; "Animation Hitches" keeps
only a few seconds on an older iPad). `PageTurnPerformanceTests` (iosAppUITests) still drives the corner with a finger
(drags let go, flicks); it marks "Corner up" in its log when to attach.

**Judge smoothness on a Release build.** A debug build's Kotlin is unoptimized: on the iPad 7 most of its main thread
goes to debug-only checks and wrappers. Measured there on 2026-11-27, 65 s of pages turning by themselves (the fox
story, tap turns and riffles):

| Build | Frames missed while a page turns | Worst frame |
|---|---|---|
| Debug, the page beneath drawn with its picture | 18 of 1,076 (1.7%) | 83 ms |
| Debug, the page beneath without its picture | 5–8 of ~1,060 (0.5–0.8%) | 40–47 ms |
| Release | 0 of 833 | 16 ms |

The agent switches and the draft channel are a debug build's only, so to measure a Release build, build Xcode's
Release configuration with `isDebugBinary()` in `PlatformModule.ios.kt` returning `true` for that build, then put it
back. Never commit that.

## 11. Tests and checks

- `PageFoldTest` (commonTest), on a 360 × 760 dp screen:
  - the dog-ear with its crease 118 dp along the bottom and 96 dp up the right edge puts the corner at
    (266.0, 644.5); the page keeps (0, 0) (360, 0) (360, 664) (242, 760) (0, 760); the visible flap is
    (360, 664) (266, 644.5) (242, 760);
  - with the corner at (−360, 760) the page keeps nothing and the flap lies entirely at x ≤ 0;
  - with the corner where it starts (360, 760) there's no fold;
  - a corner dragged anywhere stays within 360 dp of (0, 760) and within the diagonal of (0, 0);
  - the shade's `tint` is 0.2 × (1 − len / 540) for a small fold and 0 once `len` ≥ 540.
- `ReaderViewModelTest`: `upcoming` is the next page on a page, null on a fork and an ending; `TapChoice` saves the
  path at once but reads nothing aloud until `Landed`; `landing` is true until `Landed`; the tests of `Previous` and
  `canGoBack` go.
- `WalkthroughTests.swift` (iOS): it waits for "Back a page" today to spot a fork. Instead, give the cards test tags
  `choice-0` and `choice-1` (Compose passes them on as accessibility identifiers) and wait for "Next" or `choice-0`;
  tap the card by its identifier instead of a screen coordinate.
- `./gradlew ktlintCheck`, `:shared:testAndroidHostTest`, `:shared:allTests`, both apps build.
- **Agent switches** for trying the turn again and again, in a debug build only (`platform/AgentFlags`):
  `-skipReading` (every page's words at once, nothing read aloud, so its corner folds up 0.7 s after it lands),
  `-openStory <id>` (straight to that story's reader, from its first page) and `-autoTurn` (pages turn by themselves,
  a fork picks each card in turn, an ending tries another path). iOS: launch arguments (`xcrun devicectl device
  process launch --device <id> --terminate-existing com.nicolasguillen.taletime -- -skipReading -autoTurn -openStory
  fox-and-the-lantern`, or `xcrun simctl launch booted com.nicolasguillen.taletime -skipReading …`); Android: intent
  extras (`adb shell am start -S -n com.nicolasguillen.taletime/.MainActivity --ez skipReading true --ez autoTurn
  true --es openStory fox-and-the-lantern`). With `-autoTurn` the app prints its frame counts every 10 s ("TaleTime
  frames: …"), on iOS to the console of `devicectl … launch --console`, on Android to logcat.
- On a phone and a tablet emulator, both orientations on the tablet: the corner, a tap turn, a drag dropped back, a
  flick, a choice, reduced motion on, TalkBack/VoiceOver on. Look at the screenshots. Steady 60 fps through a turn
  and the riffle on the phone emulator (and the physical phone if it's plugged in).

## 12. Docs to update with the change

- `plan.md` §3.2: items 3 and 5 and the "Back and next arrows" paragraph (the folded corner, no back arrow, the quiet
  home button, the choice's magic); Phase 5: tick the page-turn animation, keep the sounds open.
- `AGENTS.md`, "App": `reader/ (pages, forks, endings, back)` loses "back"; a line on the page turn pointing here.

## 13. Open questions

1. **Forks:** the cards rise on the same page by themselves once the text is read (§5, the sketch's default), or the
   corner folds and turns to a page with the cards? *Proposed: they rise.*
2. **Endings:** "The End" rises on the same page by itself (§5), or the corner turns to it? *Proposed: it rises, like
   the cards.*
3. **The quiet home button** on the cover and in the parents area too, or only in the reader? *Proposed: only the
   reader, where it sits on the picture.*
