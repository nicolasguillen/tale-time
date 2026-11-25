"""`story assets`: writes the image requests. Each story gets its own REQUESTED_ASSETS.md, next to its images/
folder, with every image it needs, including its own art style and style anchor (style/style.json) and its own
characters' reference sheets (characters/). Stories share a style or a character only when a bible declares it
(`styleFrom`, or `from` on a cast member or prop). The file at the repository root is an index: the status of each
story with a link to its file, how to make and deliver the images, and the app's own images.

The images are generated with AI by the user, outside the repo: each one is requested here with its size, the
images to attach and a prompt to paste into an AI image tool that takes reference images (ChatGPT or the GPT Image
API), then dropped into the folder named in its entry. Nothing in this repo calls an image model. Until then the app and the previewer show a static placeholder from content/placeholders/.

The prompts follow one method, so the cast and the style stay the same from picture to picture:
- anchors first: the story's style anchor picture, then a reference sheet for each recurring character and prop;
- every later prompt attaches those images and says what each one is for ("Image 2 is the character reference for
  Mia");
- the style, palette, character and place descriptions are pasted word for word from the sources, and only the scene
  at the end changes;
- the first picture of a place is the location reference for the later ones, and a picture with the same place,
  light and framing as an earlier one is an edit of it (`editFrom`);
- a crowded picture is built in layers: the place first, then one character at a time.
"""
import os
import re
from dataclasses import dataclass
from pathlib import Path

from . import graph
from .check import image_ids
from .repo import (APP, APP_ASSETS, APP_STYLE, PLACEHOLDERS, REQUESTED_ASSETS, ROOT, STORIES, characters_dir,
                   find_image, has_story, load_json, load_story, story_ids, style_dir, style_home)

# A picture with this many named characters (the bible's cast, not extras or props) is built in layers.
CROWDED = 4


@dataclass(frozen=True)
class Spec:
    label: str
    aspect: str
    minimum: tuple[int, int]
    ideal: tuple[int, int]
    placeholder: str
    rules: str


SPECS = {
    "sheet": Spec("Reference sheet", "3:2 landscape", (2400, 1600), (3000, 2000), "sheet.svg",
                  "Plain, light, even paper-colored background with flat, even light: no cast shadows, no scenery, no "
                  "labels. Every view at the same scale, side by side, none overlapping."),
    "cover": Spec("Cover", "3:4 portrait", (1200, 1600), (1500, 2000), "cover.svg",
                  "No title and no text of any kind: the app draws the title over the bottom quarter, so keep that "
                  "area calm (grass, water, sky). Main characters in the upper two thirds."),
    "page": Spec("Page illustration", "4:3 landscape", (1600, 1200), (2400, 1800), "page.svg",
                 "Full bleed, no border or vignette. Keep the main action inside the middle 80%: the edges get "
                 "cropped on some screens."),
    "ending": Spec("Ending illustration", "4:3 landscape", (1600, 1200), (2400, 1800), "page.svg",
                   "Full bleed, no border. Keep the main action inside the middle 80%, and the key moment in the "
                   "very center: a circle cropped from the middle becomes the ending's badge."),
    "card": Spec("Choice card", "1:1 square", (768, 768), (1024, 1024), "card.svg",
                 "One clear subject on a simple, calm background, readable as a small thumbnail (about 160 pt wide on "
                 "a phone)."),
}
ORDER = ["sheet", "cover", "page", "ending", "card"]
ANCHOR = Spec("Style anchor", "4:3 landscape", (1600, 1200), (2400, 1800), "none",
              "Full bleed, no border or vignette. Only the place and its light: no people, animals or characters.")
APP_SPECS = {
    "icon": Spec("App icon", "1:1 square", (1024, 1024), (1024, 1024), "none (the system's default icon)",
                 "PNG, opaque, square corners (the stores round them). The app's own style, simplified: big shapes that "
                 "still read at 29 pt on a home screen. No text."),
    "icon-foreground": Spec("Android icon foreground", "1:1 square", (1024, 1024), (1024, 1024),
                            "none (the system's default icon)",
                            "PNG with a transparent background. Android masks it into a circle, a squircle or a "
                            "rounded square and moves it with parallax, so everything that matters sits inside the "
                            "central circle, 61% of the width across."),
    "icon-background": Spec("Android icon background", "1:1 square", (1024, 1024), (1024, 1024),
                            "none (the system's default icon)",
                            "PNG, opaque, edge to edge. No subject: it sits behind the foreground layer."),
}
HEADER = ("<!-- Generated by `story assets`. Edit the sources (each story's bible.json, shots.json and "
          "style/style.json; content/app/assets.json and style.json), not this file. -->")
STORY_HEADER = ("<!-- Generated by `story assets`. Edit the sources (this story's bible.json, shots.json and "
                "style/style.json), not this file. -->")
# The name of the requests file, at the root and in every story folder.
FILE = REQUESTED_ASSETS.name


@dataclass(frozen=True)
class Style:
    """An art style: a story's own (style/style.json), another story's that it declares with `styleFrom`, or the
    app's. `folder` is where its style anchor is delivered, or None for a style without one (the app's)."""
    doc: dict
    folder: Path | None


@dataclass(frozen=True)
class Ref:
    """An image to attach to a prompt."""
    kind: str  # style, sheet, place or edit
    path: str  # where it is, or will be once delivered
    label: str  # for the person attaching it
    role: str  # what the prompt tells the image tool it's for


# Helpers --------------------------------------------------------------------------------------------------------------

def _rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else path.as_posix()


def _size(spec: Spec) -> str:
    (mw, mh), (iw, ih) = spec.minimum, spec.ideal
    return f"{spec.aspect}, at least {mw}×{mh} px, ideally {iw}×{ih} px"


def _file(folder: Path, asset_id: str) -> str:
    found = find_image(folder, asset_id)
    if found:
        return f"`{_rel(found)}`"
    return f"`{_rel(folder / asset_id)}.png` (or `.webp` / `.jpg`)"


def _path(folder: Path, asset_id: str) -> str:
    """The file to attach: the delivered one, or where it will be."""
    found = find_image(folder, asset_id)
    return _rel(found) if found else f"{_rel(folder / asset_id)}.png"


def _mark(done: bool) -> str:
    return "✅" if done else "⬜"


def _quote(text: str) -> str:
    return "\n".join(f"> {line}" if line else ">" for line in text.splitlines())


def _lower(text: str) -> str:
    return text[:1].lower() + text[1:]


def _sentence(text: str) -> str:
    text = text.strip()
    return text if text.endswith((".", "!", "?")) else text + "."


def _mid(name: str) -> str:
    """A name in the middle of a sentence: 'The lantern' -> 'the lantern', 'Mia' stays."""
    return _lower(name) if name.startswith("The ") else name


def _and(names: list[str]) -> str:
    return names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]


def _code(lines: list[str]) -> list[str]:
    return ["```text", *lines, "```", ""]


# Prompt blocks. Each one is built the same way every time, so the same character, place or style reads word for word
# the same in every prompt. Only the scene changes. -------------------------------------------------------------------

def _style_ref(style: Style) -> list[Ref]:
    """The style anchor to attach, as a list: empty for a style without one."""
    if style.folder is None or "anchor" not in style.doc:
        return []
    return [Ref("style", _path(style.folder, style.doc["anchor"]["id"]), "style anchor",
                "the art style reference: match its medium, brush texture, paper grain, palette and edges, not its "
                "subject")]


def _edit_ref(folder: Path, image_id: str) -> Ref:
    return Ref("edit", _path(folder, image_id), "the picture to edit",
               f"the picture to edit (`{image_id}`), and the style reference: keep its style, and everything the "
               "description below doesn't change, exactly as it is")


def _ref_lines(refs: list[Ref]) -> list[str]:
    return [f"Image {n} is {r.role}." for n, r in enumerate(refs, 1)]


def _style_lines(style: dict, accents: dict | None = None) -> list[str]:
    palette = ", ".join(f"{name} {hex_}" for name, hex_ in style["palette"].items())
    if accents:
        palette += "; story accents: " + ", ".join(f"{name} {hex_}" for name, hex_ in accents.items())
    return [f"Style: {style['prompt']}", f"Palette: {palette}."]


def _rules(style: dict, characters: bool, style_ref: bool) -> list[str]:
    keep = []
    if characters:
        keep.append("every character and prop exactly as its reference image and description show it: the same "
                    "face, proportions, age, clothes, colors and markings")
    if style_ref:
        keep.append("the brush texture, paper grain, edges and palette of the style reference")
    out = [f"Keep {'; and '.join(keep)}."] if keep else []
    return out + ["Never: " + "; ".join(_lower(a) for a in style["avoid"]) + "."]


def _format(spec: Spec) -> str:
    return f"Format: {spec.aspect}. {spec.rules}"


def _asks(item: dict) -> list[str]:
    out = []
    if item.get("mustShow"):
        out.append("Must show: " + "; ".join(item["mustShow"]) + ".")
    if item.get("avoid"):
        out.append("Avoid here: " + "; ".join(item["avoid"]) + ".")
    return out


def _prompt(*blocks: list[str]) -> list[str]:
    out: list[str] = []
    for block in blocks:
        if block:
            out += ([""] if out else []) + block
    return out


def _attach(refs: list[Ref]) -> list[str]:
    out = ["- **Attach, in this order:**"]
    return out + [f"  {n}. `{r.path}` ({r.label})" for n, r in enumerate(refs, 1)]


# Stories --------------------------------------------------------------------------------------------------------------

class Story:
    def __init__(self, story_id: str, stories: Path = STORIES):
        self.folder = stories / story_id
        # The pictures are the same in every language: they're requested with the first language's words.
        story = load_story(self.folder)
        self.languages = list(story)
        self.story = story[self.languages[0]]
        self.bible = load_json(self.folder / "bible.json")
        self.shots = load_json(self.folder / "shots.json")
        home = style_home(self.folder, self.bible)
        self.style = Style(load_json(style_dir(home) / "style.json"), style_dir(home))
        self.style_from = self.bible.get("styleFrom")  # another story's id, when the style is shared
        everyone = self.bible.get("cast", []) + self.bible.get("extras", []) + self.bible.get("props", [])
        self.who = {c["id"]: c for c in everyone}
        self.named = {c["id"] for c in self.bible.get("cast", [])}
        self.extras = {c["id"] for c in self.bible.get("extras", [])}
        self.props = {c["id"] for c in self.bible.get("props", [])}
        self.places = {p["id"]: p["look"] for p in self.bible.get("places", [])}
        self.shot = {s["id"]: s for s in self.shots["images"]}
        self.images = self.folder / "images"
        # image id -> (node id, option index or None)
        self.owner: dict[str, tuple[str, int | None]] = {}
        for nid, node in self.story["nodes"].items():
            self.owner[node["image"]] = (nid, None)
            for i, o in enumerate(node.get("options", [])):
                self.owner[o["image"]] = (nid, i)

    def kind(self, image_id: str) -> str:
        if image_id == self.story["cover"]:
            return "cover"
        nid, option = self.owner[image_id]
        if option is not None:
            return "card"
        return "ending" if graph.kind(self.story["nodes"][nid]) == graph.ENDING else "page"

    def sheets(self) -> list[dict]:
        """This story's own characters and props that get a reference sheet, which every other picture must match.
        A shared one (`from`) has its sheet in its own story and isn't requested again."""
        return [c for c in self.bible.get("cast", []) + self.bible.get("props", []) if c.get("sheet") and not c.get("from")]

    def shared_sheets(self) -> list[dict]:
        """Characters and props this story reuses from another one (`from` in the bible), with their sheet."""
        return [c for c in self.bible.get("cast", []) + self.bible.get("props", []) if c.get("sheet") and c.get("from")]

    def sheet_dir(self, cast_id: str) -> Path:
        """Where a character's or prop's reference sheet is: this story's characters/ folder, or its home story's."""
        home = self.who[cast_id].get("from")
        return characters_dir(self.folder.parent / home if home else self.folder)

    def anchor_done(self) -> bool:
        return find_image(self.style.folder, self.style.doc["anchor"]["id"]) is not None

    def ordered_images(self) -> list[str]:
        """The order to make them in: reading order, each fork's two cards right after its page, the cover last (it
        reuses the characters and places of the pages)."""
        seen, out = set(), []
        for image_id in image_ids(self.story):
            if image_id not in seen:
                seen.add(image_id)
                out.append(image_id)
        cover = self.story["cover"]
        return [i for i in out if i != cover] + [cover]

    def appears_in(self, cast_id: str) -> list[str]:
        return [s["id"] for s in self.shots["images"] if any(c["id"] == cast_id for c in s.get("cast", []))]

    def name(self, cast_id: str) -> str:
        return self.who.get(cast_id, {}).get("name", cast_id)

    def cast_ids(self, shot: dict) -> list[str]:
        """Who's in the picture, once each, in the shot's order, leaving out ids the bible doesn't know."""
        out: list[str] = []
        for c in shot.get("cast", []):
            if c["id"] in self.who and c["id"] not in out:
                out.append(c["id"])
        return out

    def look(self, cast_id: str) -> str:
        return f"{self.name(cast_id)}: {self.who[cast_id]['look']}"

    def sheet_ref(self, cast_id: str) -> Ref:
        name = self.name(cast_id)
        if cast_id in self.named:
            role = (f"the character reference for {name}: keep their face, proportions, colors, clothes and markings "
                    "exactly")
        else:
            role = f"the reference for {_mid(name)}: keep its shape, colors and details exactly"
        return Ref("sheet", _path(self.sheet_dir(cast_id), cast_id), _mid(name), role)

    def place_ref(self, image_id: str) -> str | None:
        """The first page or ending set in the same place, made before this one: the location reference."""
        place = self.shot[image_id].get("place")
        if not place:
            return None
        for other in self.ordered_images():
            if other == image_id:
                return None
            if self.kind(other) in ("page", "ending") and self.shot.get(other, {}).get("place") == place:
                return other
        return None

    def refs(self, image_id: str) -> list[Ref]:
        shot = self.shot[image_id]
        source = shot.get("editFrom")
        out = [_edit_ref(self.images, source)] if source else _style_ref(self.style)
        out += [self.sheet_ref(c) for c in self.cast_ids(shot) if self.who[c].get("sheet")]
        other = None if source or self.kind(image_id) == "card" else self.place_ref(image_id)
        if other:
            place = shot["place"].replace("-", " ")
            out.append(Ref("place", _path(self.images, other), f"location: {place}",
                           f"the location reference for the {place}: use it only for the place itself (layout, "
                           "landmarks, plants and colors), not for its characters, poses, light or framing"))
        return out

    def crowded(self, image_id: str) -> int:
        """How many named characters a picture made in one go would have to keep apart (0 if it's built otherwise)."""
        shot = self.shot[image_id]
        if shot.get("editFrom") or self.kind(image_id) == "card":
            return 0
        named = [c for c in self.cast_ids(shot) if c in self.named]
        return len(named) if len(named) >= CROWDED else 0

    def place_lines(self, shot: dict) -> list[str]:
        place, setting = shot.get("place"), shot.get("setting")
        if place in self.places:
            return [f"Place: {self.places[place]}"] + ([f"Where exactly: {_sentence(setting)}"] if setting else [])
        return [f"Place: {_sentence(setting)}"] if setting else []

    def scene_lines(self, image_id: str) -> list[str]:
        shot = self.shot[image_id]
        if self.kind(image_id) == "card":
            out = [f"Subject: {_sentence(shot['subject'])}", f"Background: {_sentence(shot['background'])}"]
        else:
            out = self.place_lines(shot) + [f"Scene: {_sentence(shot['scene'])}"]
        out += [f"- {self.name(c['id'])}: {c['state']}" for c in shot.get("cast", [])]
        how = [f"{label}: {_sentence(shot[key])}" for key, label in
               (("framing", "Framing"), ("light", "Light"), ("mood", "Mood")) if shot.get(key)]
        if how:
            out.append(" ".join(how))
        return out + _asks(shot)

    def prompt(self, image_id: str) -> list[str]:
        """The whole prompt for a story image, as lines."""
        shot = self.shot[image_id]
        refs = self.refs(image_id)
        ids = self.cast_ids(shot)
        return _prompt(_ref_lines(refs),
                       _style_lines(self.style.doc, self.bible.get("palette")),
                       [self.look(c) for c in ids],
                       _rules(self.style.doc, characters=bool(ids), style_ref=True) + [_format(SPECS[self.kind(image_id)])],
                       self.scene_lines(image_id))

    def sheet_prompt(self, entry: dict) -> list[str]:
        if entry.get("views"):
            views = "; ".join(entry["views"])
        else:
            expressions = ", ".join(entry.get("expressions", ["happy", "surprised", "sleepy"]))
            views = f"full body front, side and back; head and shoulders: {expressions}"
        sheet = [f"Reference sheet of {_mid(entry['name'])}, showing: {views}."]
        if entry.get("personality"):
            sheet.append(f"Personality to show: {entry['personality']}")
        return _prompt(_ref_lines(_style_ref(self.style)),
                       _style_lines(self.style.doc, self.bible.get("palette")),
                       [self.look(entry["id"])],
                       _rules(self.style.doc, characters=False, style_ref=True) + [_format(SPECS["sheet"])],
                       sheet)

    def layers(self, image_id: str) -> list[tuple[str, list[Ref], list[str]]]:
        """A crowded picture in steps: the place (with any extras), then each character on its own, together with the
        props their state names (Mia "holding the lantern up" brings the lantern). Each step is (title, the images to
        attach, the prompt lines)."""
        shot = self.shot[image_id]
        ids = self.cast_ids(shot)
        extras = [c for c in ids if c in self.extras]
        base = [r for r in self.refs(image_id) if r.kind in ("style", "place")]
        how = [f"{label}: {_sentence(shot[key])}" for key, label in
               (("framing", "Framing"), ("light", "Light"), ("mood", "Mood")) if shot.get(key)]
        if extras:
            only = (f"Paint only the place, its light and {_and([_mid(self.name(c)) for c in extras])} for now, with no "
                    "other characters")
        else:
            only = "Paint only the place and its light for now, with no characters"
        background = _prompt(
            _ref_lines(base),
            _style_lines(self.style.doc, self.bible.get("palette")),
            [self.look(c) for c in extras],
            _rules(self.style.doc, characters=bool(extras), style_ref=True) + [_format(SPECS[self.kind(image_id)])],
            self.place_lines(shot) + [
                f"{only}: they are added one at a time next. Leave room for them as this scene will need: "
                f"{_sentence(shot['scene'])}",
            ] + ([" ".join(how)] if how else []))
        steps = [("The place", base, background)]

        state = {c["id"]: c["state"] for c in shot.get("cast", [])}
        props = [c for c in ids if c in self.props]
        claimed: set[str] = set()
        groups = []
        for c in ids:
            if c in self.extras or c in claimed:
                continue
            group = [c]
            if c not in self.props:
                group += [p for p in props if p not in claimed and re.search(
                    rf"\b{re.escape(_mid(self.name(p)).removeprefix('the '))}\b", state[c], re.IGNORECASE)]
            claimed.update(group)
            groups.append(group)
        for group in groups:
            sheets = [self.sheet_ref(c) for c in group if self.who[c].get("sheet")]
            names = _and([_mid(self.name(c)) for c in group])
            matching = (f", matching the attached reference image{'s' if len(sheets) > 1 else ''} exactly"
                        if sheets else "")
            steps.append((f"Add {names}", sheets, [
                f"Add only {names} to this picture{matching}.",
                *[self.look(c) for c in group],
                *[f"{self.name(c)} in this picture: {_sentence(state[c])}" for c in group],
                f"The finished scene: {_sentence(shot['scene'])}",
                "Keep everything else exactly the same.",
            ]))
        return steps


def _ready(story_id: str) -> bool:
    """A story's images are requested once it has its shot list (step 5) and an art style (its own, or the one its
    bible declares with `styleFrom`). `story check` reports what's missing."""
    folder = STORIES / story_id
    if not all((folder / f).is_file() for f in ("bible.json", "shots.json")) or not has_story(folder):
        return False
    return (style_dir(style_home(folder, load_json(folder / "bible.json"))) / "style.json").is_file()


def _title(story_id: str) -> str:
    folder = STORIES / story_id
    story = load_story(folder)
    if story:
        return next(iter(story.values()))["title"]
    return next(iter(load_json(folder / "bible.json")["title"].values()))


def _link(target: Path, start: Path) -> str:
    return Path(os.path.relpath(target, start)).as_posix()


COLUMNS = ["anchor"] + ORDER
COLUMN_LABELS = "Style anchor | Sheets | Cover | Pages | Endings | Cards"


def _counts(s: Story) -> dict[str, list[int]]:
    """Delivered and requested, per kind of image. A shared style anchor or sheet counts in its own story."""
    counts = {k: [0, 0] for k in COLUMNS}
    if not s.style_from:
        counts["anchor"] = [int(s.anchor_done()), 1]
    for c in s.sheets():
        counts["sheet"][1] += 1
        counts["sheet"][0] += find_image(s.sheet_dir(c["id"]), c["id"]) is not None
    for image_id in s.ordered_images():
        k = s.kind(image_id)
        counts[k][1] += 1
        counts[k][0] += find_image(s.images, image_id) is not None
    return counts


def _status_cells(s: Story) -> str:
    counts = _counts(s)
    done, total = sum(v[0] for v in counts.values()), sum(v[1] for v in counts.values())
    return " | ".join(f"{counts[k][0]}/{counts[k][1]}" if counts[k][1] else "shared" if k == "anchor" else "0/0"
                      for k in COLUMNS) + f" | **{done}/{total}**"


def render(stories: list[Story] | None = None) -> str:
    """The index at the root: every story's status with a link to its own file, the method, the delivery rules and the
    app's own images. Nothing in it is a story's style or characters: those are in each story's file."""
    if stories is None:
        stories = [Story(sid) for sid in story_ids() if _ready(sid)]
    waiting = [sid for sid in story_ids() if not _ready(sid)]
    out: list[str] = [HEADER, "", "# Requested assets", ""]
    out += [
        "Every image the app needs, with its size, the images to attach and a prompt to paste. **The images are "
        "generated with AI, outside the repo:** you paste each prompt into an AI image tool that takes reference images "
        "(ChatGPT or the GPT Image API), attach the listed images, and drop the result into the folder in its entry, "
        "named exactly as shown. Nothing in this repo or its pipeline calls an image model. Until an image arrives, "
        f"the app and the previewer show a static placeholder from `{_rel(PLACEHOLDERS)}/`.",
        "",
        f"**Each story is requested in its own file,** next to its folders (`content/stories/<id>/{FILE}`, linked "
        "below), **with its own art style, style anchor and characters.** Stories don't share a style or a character "
        "unless a story's `bible.json` declares it: `styleFrom` to reuse another story's art style, `from` on a cast "
        "member or prop to reuse another story's character. This file is only the index, the method and the app's own "
        "images.",
        "",
        "This file is generated. To change a request, edit the story's `bible.json`, `shots.json` or "
        f"`style/style.json` (or `{_rel(APP_ASSETS)}` and `{_rel(APP_STYLE)}` for the app icon), then run "
        "`cd tools && uv run story assets`. Run it again after delivering files, and the ⬜ entries turn ✅. Narration "
        "is requested separately, per language: [REQUESTED_AUDIO.md](REQUESTED_AUDIO.md) is its index, and each story "
        "has one `audio/<lang>/REQUESTED_AUDIO.md` per language. The pictures are the same in every language.",
        "",
    ]

    # Status table, one row per story, linking to its file.
    out += ["## Status", "", f"| Story | Art style | {COLUMN_LABELS} | Total |", "|---|---|---|---|---|---|---|---|---|"]
    for s in stories:
        style = (f"shared from `{s.style_from}`" if s.style_from
                 else f"{s.style.doc['name']} ({s.style.doc['status']})")
        out.append(f"| [{s.story['title']}]({_link(s.folder / FILE, ROOT)}) | {style} | {_status_cells(s)} |")
    out.append("")
    if waiting:
        names = ", ".join(f"*{_title(sid)}*" for sid in waiting)
        out += [f"Not requested yet, waiting for a shot list and an art style: {names}.", ""]
    app = load_json(APP_ASSETS) if APP_ASSETS.is_file() else None
    if app:
        done = sum(find_image(APP, i["id"]) is not None for i in app["images"])
        out += [f"App images (icon): **{done}/{len(app['images'])}**", ""]

    out += [
        "## How to make them",
        "",
        "Image models make every picture from scratch and drift from page to page; there's no switch that locks a "
        "character. This method keeps a story's cast and style the same on every page:",
        "",
        "1. **Anchors first, per story.** In the story's file, make its style anchor, then its reference sheets (with "
        "the style anchor attached), and approve them before anything else: every later prompt of that story attaches "
        "them. Then the story's first batch, then the rest in the order listed.",
        "2. **Attach the listed images, in the listed order.** The prompt starts by saying what each one is for "
        "(\"Image 2 is the character reference for Mia\"). A one-scene character without a sheet is described in "
        "words only.",
        "3. **Paste the prompt as it is.** The style, palette, character and place descriptions are word for word the "
        "same in every prompt of a story; only the scene at the end changes. Small rewordings shift colors and "
        "proportions. To change a description, change its source and run `story assets`, so every prompt changes "
        "together.",
        "4. **Edit, don't start over,** where an entry says *Edit from*: it's the same place, light and framing as an "
        "earlier picture, so edit that one (reply to it in ChatGPT, or send it to the image edit endpoint) and keep "
        "its background and light. Other pictures in a place already painted attach its first picture as the "
        "location reference.",
        f"5. **Build crowded scenes in layers.** With {CROWDED} or more named characters in one picture, faces and "
        "clothes bleed into each other. Those entries give the steps: the place first, then one character at a time, "
        "each with its reference attached. Use the same steps for any picture whose characters come out mixed up.",
        "6. **Fix drift with a narrow edit,** not a new picture: attach the reference and say exactly what to change "
        "and what to keep (\"Redraw only Tuck's scarf to match the attached reference image. Keep everything else "
        "exactly the same.\").",
        "7. **Never name an artist.** A style is described by medium and technique only.",
        "",
    ]

    # Delivery rules and sizes.
    out += [
        "## How to deliver",
        "",
        "- **Name** each file after its asset id, as `.png`, `.webp` or `.jpg`. sRGB, no transparency, no watermark, "
        "no text.",
        "- **Where**: each entry names its folder. A story's style anchor goes in its `style/` folder, its reference "
        "sheets in its `characters/` folder, everything else in its `images/` folder.",
        "- **Size**: at least the minimum below, ideally the larger size. Packaging crops, resizes and converts to "
        "WebP, and strips metadata.",
        "- **Order**: a story's style anchor, then its reference sheets. Every other picture of a character or prop "
        "must match its sheet.",
        "- **Provenance**: note the tool and model that made each image in the story's `provenance.json`, and keep "
        "its terms (commercial use in an app) on file. The store listing says the pictures are made with AI.",
        "- **Keep**: delivered files aren't in git. Run `cd tools && uv run story originals push` after a delivery to "
        "keep the originals in Firebase Storage.",
        "",
        "| Kind | Aspect | Minimum | Ideal | Placeholder | Rules |",
        "|---|---|---|---|---|---|",
    ]
    for spec in [ANCHOR] + [SPECS[k] for k in ORDER]:
        out.append(f"| {spec.label} | {spec.aspect} | {spec.minimum[0]}×{spec.minimum[1]} | "
                   f"{spec.ideal[0]}×{spec.ideal[1]} | `{spec.placeholder}` | {spec.rules} |")
    out.append("")

    if app and APP_STYLE.is_file():
        out += _render_app(app, Style(load_json(APP_STYLE), None))
    return "\n".join(out).rstrip() + "\n"


def _audio_links(s: Story) -> str:
    """Links to the story's narration requests, one per language, from its folder."""
    links = [f"[audio/{lang}/REQUESTED_AUDIO.md](audio/{lang}/REQUESTED_AUDIO.md)" for lang in s.languages]
    return links[0] if len(links) == 1 else ", ".join(links[:-1]) + " and " + links[-1]


def render_story(s: Story) -> str:
    """One story's own requests file, next to its images/ folder: its art style and anchor, its characters' sheets,
    and every picture."""
    title = s.story["title"]
    index = _link(REQUESTED_ASSETS, s.folder)
    first = ("this story's **style anchor** (below)" if not s.style_from
             else f"the style anchor of `{s.style_from}`, whose art style this story shares (below)")
    out: list[str] = [STORY_HEADER, "", f"# Requested assets: {title}", ""]
    out += [
        f"Every image *{title}* needs, with its size, the images to attach and a prompt to paste into an AI image tool "
        "that takes reference images (ChatGPT or the GPT Image API). Nothing in this repo calls an image model. Until "
        f"an image arrives, the app shows a static placeholder from `{_rel(PLACEHOLDERS)}/`.",
        "",
        f"**Before you start:** make and approve {first}, then this story's reference sheets, then its images in the "
        "order listed. Drop each file into the folder named in its entry, named exactly as shown. How to make them "
        f"and the delivery rules are in the [index of requests]({index}).",
        "",
        "This file is generated. To change a request, edit this story's `bible.json`, `shots.json` or "
        "`style/style.json`, then run `cd tools && uv run story assets`. Run it again after delivering files, and the "
        "⬜ entries turn ✅. These pictures are the same in every language. The narration is requested per language, "
        f"in {_audio_links(s)}.",
        "",
        f"| {COLUMN_LABELS} | Total |",
        "|---|---|---|---|---|---|---|",
        f"| {_status_cells(s)} |",
        "",
    ]
    out += _render_story(s)
    return "\n".join(out).rstrip() + "\n"


def _render_style(s: Story) -> list[str]:
    """The story's art style, and its style anchor request (or where the shared one is)."""
    bible = s.bible
    if s.style_from:
        other = _link(s.folder.parent / s.style_from / FILE, s.folder)
        out = [f"## Art style: {s.style.doc['name']}, shared from `{s.style_from}`", "",
               f"This story uses the art style of `{s.style_from}`, as its `bible.json` declares (`styleFrom`). Its "
               f"style anchor and style block are requested in [that story's file]({other}); every prompt below "
               "attaches the same anchor.", ""]
    else:
        doc = s.style.doc
        out = [f"## Art style: {doc['name']} ({doc['status']})", "",
               f"This story's own style, from `{_rel(s.style.folder / 'style.json')}`. No other story uses it unless "
               "its bible declares `styleFrom`.", "", doc["summary"], ""]
        for key, label in [("medium", "Medium"), ("shapes", "Shapes"), ("line", "Line"), ("color", "Color"),
                           ("light", "Light"), ("composition", "Composition"), ("characters", "Characters")]:
            if key in doc:
                out.append(f"- **{label}:** {doc[key]}")
        out += ["", "**Base palette:**", ""]
        out += [f"- `{hex_}` {name}" for name, hex_ in doc["palette"].items()]
        out += ["", "**Never, in any image:**", ""]
        out += [f"- {rule}" for rule in doc["avoid"]]
        out += ["", "**Style block**, pasted word for word into every prompt of this story:", "", _quote(doc["prompt"]), ""]
    if bible.get("palette"):
        out += ["**Story palette accents** (the cast's colors and the story's light):", ""]
        out += [f"- `{hex_}` {name}" for name, hex_ in bible["palette"].items()]
        out.append("")
    if s.style_from:
        return out

    anchor = s.style.doc["anchor"]
    out += [
        f"### {_mark(s.anchor_done())} `{anchor['id']}`: {ANCHOR.label}",
        "",
        f"- **File:** {_file(s.style.folder, anchor['id'])}",
        f"- **Size:** {_size(ANCHOR)}",
        "- **Attach:** nothing. Make this first: every other prompt of this story attaches it as the style reference. "
        "Make it again until the medium, the textures and the light are exactly right, and approve it before going on.",
        "",
    ]
    out += _code(_prompt(_style_lines(s.style.doc), _rules(s.style.doc, characters=False, style_ref=False)
                         + [_format(ANCHOR)], [f"Scene: {_sentence(anchor['subject'])}"] + _asks(anchor)))
    return out


def _render_app(app: dict, style: Style) -> list[str]:
    out = [f"## App images ({app.get('status', 'requested')})", "",
           "The app's own pictures, outside any story, in the app's own style "
           f"({style.doc['name']}, {style.doc['status']}, from `{_rel(APP_STYLE)}`). Deliver them into "
           f"`{_rel(APP)}/`. The Android launcher icon is built from the two layers, iOS and the stores use "
           "`app-icon`. Once they're delivered, `story icons` puts them into both apps.", ""]
    for image in app["images"]:
        spec = APP_SPECS[image["kind"]]
        done = find_image(APP, image["id"]) is not None
        source = image.get("editFrom")
        refs = [_edit_ref(APP, source)] if source else _style_ref(style)
        out += [
            f"#### {_mark(done)} `{image['id']}`: {spec.label}",
            "",
            f"- **File:** {_file(APP, image['id'])}",
            f"- **Size:** {spec.aspect}, exactly {spec.ideal[0]}×{spec.ideal[1]} px",
        ]
        if source:
            out.append(f"- **Edit from:** `{source}`, so the two line up exactly.")
        out += (_attach(refs) if refs else ["- **Attach:** nothing. Make this one first: the others are edits of it."])
        out.append("")
        subject = [f"Subject: {_sentence(image['subject'])}"]
        if image.get("background"):
            subject.append(f"Background: {_sentence(image['background'])}")
        out += _code(_prompt(_ref_lines(refs), _style_lines(style.doc),
                             _rules(style.doc, characters=False, style_ref=bool(refs)) + [_format(spec)],
                             subject + _asks(image)))
    return out


def _render_story(s: Story) -> list[str]:
    bible = s.bible
    out = [bible["pitch"], ""]
    out += _render_style(s)
    if s.shots.get("lightArc"):
        out += [f"**Light across the story:** {s.shots['lightArc']}", ""]
    if s.shots.get("firstBatch"):
        batch = ", ".join(f"`{i}`" for i in s.shots["firstBatch"])
        out += [f"**Make these first** (after the style anchor): {batch}. They set the look for the rest, and they're "
                "the set to make in each candidate style if you want to compare styles before committing.", ""]

    out += ["## Locked descriptions", "",
            "Pasted word for word into every prompt the character, prop or place is in. To change one, edit "
            "`bible.json` and run `story assets`, never the prompt.", ""]
    for entry in bible.get("cast", []) + bible.get("extras", []) + bible.get("props", []):
        sheet = f" Reference sheet: `{entry['id']}`." if entry.get("sheet") else ""
        shared = f" Shared from `{entry['from']}`." if entry.get("from") else ""
        out.append(f"- **{entry.get('name', entry['id'])}** (`{entry['id']}`): {entry['look']}{sheet}{shared}")
    for place in bible.get("places", []):
        out.append(f"- **Place** `{place['id']}`: {place['look']}")
    out.append("")

    sheets, shared = s.sheets(), s.shared_sheets()
    if sheets or shared:
        spec = SPECS["sheet"]
        out += ["## Reference sheets (characters and props)", "",
                "This story's own characters and props: their sheets go in this story's "
                f"`{_rel(characters_dir(s.folder))}/` folder.", ""]
        for c in shared:
            other = _link(s.folder.parent / c["from"] / FILE, s.folder)
            out += [f"- **{c['name']}** (`{c['id']}`) is shared from `{c['from']}`, as `bible.json` declares (`from`): "
                    f"its sheet is made there ([that story's file]({other})), at "
                    f"`{_rel(s.sheet_dir(c['id']) / c['id'])}.png`, and attached here.", ""]
        for c in sheets:
            done = find_image(s.sheet_dir(c["id"]), c["id"]) is not None
            used = ", ".join(f"`{i}`" for i in s.appears_in(c["id"]))
            out += [
                f"### {_mark(done)} `{c['id']}`: {spec.label}, {c['name']}",
                "",
                f"- **File:** {_file(s.sheet_dir(c['id']), c['id'])}",
                f"- **Size:** {_size(spec)}",
                f"- **Appears in:** {used or 'no shots yet'}",
            ]
            out += _attach(_style_ref(s.style)) + [""]
            out += _code(s.sheet_prompt(c))

    out += ["## Story images", "",
            "In the order to make them: reading order, a fork's two choice cards right after its page, and the cover "
            "last (it reuses the characters and places of the pages).", ""]
    for image_id in s.ordered_images():
        out += _render_image(s, image_id)
    return out


def _render_image(s: Story, image_id: str) -> list[str]:
    story = s.story
    k = s.kind(image_id)
    spec = SPECS[k]
    shot = s.shot.get(image_id)
    done = find_image(s.images, image_id) is not None
    out = [f"### {_mark(done)} `{image_id}`: {spec.label}", ""]
    out.append(f"- **File:** {_file(s.images, image_id)}")
    out.append(f"- **Size:** {_size(spec)}")
    if shot is None:
        return out + ["- **Missing from shots.json.** Add a shot, then run `story assets` again.", ""]

    if shot.get("editFrom"):
        out.append(f"- **Edit from:** `{shot['editFrom']}`. Same place, light and framing, so edit that picture "
                   "rather than starting a new one.")
    out += _attach(s.refs(image_id)) + [""]
    out += _code(s.prompt(image_id))

    crowded = s.crowded(image_id)
    if crowded:
        out += [f"**Crowded scene ({crowded} named characters): build it in layers** rather than in one go, so their "
                "looks don't blend. Each step edits the picture from the step before.", ""]
        for n, (title, refs, lines) in enumerate(s.layers(image_id), 1):
            attach = ", ".join(f"`{r.path}`" for r in refs)
            if n == 1:
                how = f"A new picture, attaching {attach}:"
            elif attach:
                how = f"Edit the last picture, attaching {attach}:"
            else:
                how = "Edit the last picture (no reference sheet, so the words carry the look):"
            out += [f"{n}. **{title}.** {how}", ""]
            out += _code(lines)

    if k == "cover":
        out += [f"- **Story** (for you, not part of the prompt): *{story['title']}*. {s.bible['pitch']}", ""]
        return out
    nid, option = s.owner[image_id]
    node = story["nodes"][nid]
    if option is None:
        if "ending" in node:
            out.append(f"- **Ending:** \"{node['ending']['title']}\" ({node['ending']['kind']})")
        out += ["- **Story moment** (the page text, for you, not part of the prompt):", "", _quote(node["text"])]
        if "question" in node:
            out += [">", _quote(f"*{node['question']}*")]
    else:
        other = node["options"][1 - option]
        other_shot = s.shot.get(other["image"], {})
        out.append(f"- **Story moment:** option {'AB'[option]}, \"{node['options'][option]['label']}\", "
                   f"for the question *{node['question']}*")
        if other_shot.get("subject"):
            out.append(f"- **The other card** (`{other['image']}`, \"{other['label']}\"): "
                       f"{_sentence(other_shot['subject'])} The two must look clearly different in shape and main "
                       "color, so a child who can't read tells them apart.")
    out.append("")
    return out


def outputs() -> dict[Path, str | None]:
    """Every requests file and its text. None means a generated file to remove (a story that lost its shot list)."""
    stories = [Story(sid) for sid in story_ids() if _ready(sid)]
    out: dict[Path, str | None] = {REQUESTED_ASSETS: render(stories)}
    for s in stories:
        out[s.folder / FILE] = render_story(s)
    for sid in story_ids():
        stale = STORIES / sid / FILE
        if not _ready(sid) and stale.is_file() and stale.read_text(encoding="utf-8").startswith(STORY_HEADER):
            out[stale] = None
    return out


def sync(check_only: bool = False) -> list[Path]:
    """Writes every requests file that changed (unless check_only) and returns the ones that were out of date."""
    changed = []
    for path, text in outputs().items():
        current = path.read_text(encoding="utf-8") if path.is_file() else None
        if current == text:
            continue
        changed.append(path)
        if not check_only:
            if text is None:
                path.unlink()
            else:
                path.write_text(text, encoding="utf-8")
    return changed


def write(check_only: bool = False) -> bool:
    """Writes the requests files. Returns True if they were all already up to date."""
    return not sync(check_only)
