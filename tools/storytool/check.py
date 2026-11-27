"""`story check`: the rules every pipeline file must pass (plan.md §4.5). It's also the repair loop: after writing a
step's file, run the check and fix every error before showing the step to the user.

Issue levels:
- error:   broken. `check` fails.
- warning: worth a look, doesn't block.
- request: an asset that isn't delivered yet (a placeholder is used). Blocks only with --strict (publishing).
"""
import re
from dataclasses import dataclass
from pathlib import Path

from jsonschema import Draft202012Validator

from . import graph
from .budgets import BANDS, Budget
from .repo import (APP_CLIPS, ROOT, VOICE, audio_dir, find_audio, find_image, first_language, load_json, load_story,
                   style_dir, style_home, translation_file)

# The published app narration's folder on the content host (stories/<channel>/app/): no story can be called that.
RESERVED_IDS = ("app",)

ERROR, WARNING, REQUEST = "error", "warning", "request"
WORD = re.compile(r"[^\W_]+(?:['’-][^\W_]+)*")


@dataclass(frozen=True)
class Issue:
    level: str
    code: str
    message: str
    node: str | None = None

    def __str__(self):
        where = f" [{self.node}]" if self.node else ""
        return f"{self.level:7} {self.code}{where}: {self.message}"


def word_count(text: str) -> int:
    return len(WORD.findall(text))


def validate(doc: dict, schema_file: str, name: str | None = None) -> list[Issue]:
    """JSON Schema errors, one issue each. `schema_file` is a file name in schema/; `name` names the checked file in the
    messages (the schema's file name by default)."""
    validator = Draft202012Validator(load_json(ROOT / "schema" / schema_file))
    issues = []
    for e in sorted(validator.iter_errors(doc), key=lambda e: list(e.absolute_path)):
        where = "/".join(str(p) for p in e.absolute_path) or "(root)"
        issues.append(Issue(ERROR, "schema", f"{name or schema_file}: {where}: {e.message[:300]}"))
    return issues


def story_name(doc: dict) -> str:
    """Where one language of a story lives in its folder, for messages."""
    return f"story/{doc['language']}.json"


def image_ids(story: dict) -> list[str]:
    """Every image the story references, in reading order: cover, then each node's page and its cards. The same in
    every language, so any language's file will do."""
    ids = [story["cover"]]
    for node in story["nodes"].values():
        ids.append(node["image"])
        ids.extend(o["image"] for o in node.get("options", []))
    return ids


def audio_clips(story: dict) -> list[str]:
    """Narration clip ids, the same for every language (so any language's file will do): audio/<lang>/<clip>.m4a. An
    ending's title has none: the ending screen closes with the lines every story shares (content/app/narration.json),
    so an `<ending>-end` clip delivered earlier is simply left out (not checked, requested or packaged)."""
    clips = ["title"]
    for nid, node in story["nodes"].items():
        clips.append(nid)
        if graph.kind(node) == graph.FORK:
            clips.append(f"{nid}-q")
            clips.extend(f"{nid}-{'ab'[i]}" for i in range(len(node["options"])))
    return clips


def texts(story: dict):
    """(node id or None, field name, text) for every text in one language of the story."""
    yield None, "title", story["title"]
    for nid, node in story["nodes"].items():
        yield nid, "text", node["text"]
        if "question" in node:
            yield nid, "question", node["question"]
        for i, o in enumerate(node.get("options", [])):
            yield nid, f"option {'AB'[i]} label", o["label"]
        if "ending" in node:
            yield nid, "ending title", node["ending"]["title"]


# Bible --------------------------------------------------------------------------------------------------------------

def check_bible(bible: dict) -> list[Issue]:
    issues = validate(bible, "bible.schema.json")
    if issues:
        return issues
    ids: dict[str, int] = {}
    for entry in bible["cast"] + bible.get("extras", []) + bible.get("props", []) + bible["places"]:
        ids[entry["id"]] = ids.get(entry["id"], 0) + 1
    for cid, n in sorted(ids.items()):
        if n > 1:
            issues.append(Issue(ERROR, "bible-duplicate-id", f"'{cid}' is used {n} times in bible.json"))
    heroes = [c["id"] for c in bible["cast"] if c["role"] == "hero"]
    if len(heroes) != 1:
        issues.append(Issue(WARNING, "bible-hero", f"expected exactly one cast member with role 'hero', found {heroes}"))
    if not any(c.get("sheet") for c in bible["cast"]):
        issues.append(Issue(WARNING, "bible-sheets", "no cast member has \"sheet\": true, so no reference sheets "
                                                    "will be requested"))
    return issues


def check_style(doc: dict, anchor: bool = True) -> list[Issue]:
    """An art style file: a story's own style/style.json (with its anchor), or the app's (without one)."""
    issues = validate(doc, "style.schema.json")
    if not issues and anchor and "anchor" not in doc:
        issues.append(Issue(ERROR, "style-anchor", "the style has no anchor: every story's style needs one, made first"))
    return issues


def check_style_and_sharing(folder: Path, bible: dict, shots: bool) -> list[Issue]:
    """The story's art style is its own (style/style.json) unless the bible declares `styleFrom`, and a character or
    prop it shares (`from`) is word for word the one in its home story. Other stories are sibling folders."""
    issues: list[Issue] = []
    own = style_dir(folder) / "style.json"
    source = bible.get("styleFrom")
    if source:
        home = style_home(folder, bible)
        if source == folder.name:
            issues.append(Issue(ERROR, "style-from", "styleFrom names this story itself"))
        elif not (style_dir(home) / "style.json").is_file():
            issues.append(Issue(ERROR, "style-from", f"styleFrom is '{source}', but content/stories/{source}/style/"
                                                     "style.json doesn't exist"))
        if own.is_file():
            issues.append(Issue(WARNING, "style-both", f"the bible shares the style of '{source}' (styleFrom), so this "
                                                       "story's own style/style.json is ignored: delete one of the two"))
    elif own.is_file():
        issues += check_style(load_json(own))
    else:
        level = ERROR if shots else WARNING
        issues.append(Issue(level, "style-missing", "no style/style.json: every story has its own art style, written at "
                                                    "the bible step (or the bible declares styleFrom to share one)"))
    for entry in bible.get("cast", []) + bible.get("props", []):
        source = entry.get("from")
        if not source:
            continue
        other = folder.parent / source / "bible.json"
        if source == folder.name or not other.is_file():
            issues.append(Issue(ERROR, "shared-from", f"'{entry['id']}' is shared from '{source}', which has no "
                                                      "bible.json"))
            continue
        other_bible = load_json(other)
        match = next((c for c in other_bible.get("cast", []) + other_bible.get("props", []) if c["id"] == entry["id"]),
                     None)
        if match is None:
            issues.append(Issue(ERROR, "shared-from", f"'{entry['id']}' is shared from '{source}', whose bible has no "
                                                      f"'{entry['id']}'"))
        elif match["look"] != entry["look"]:
            issues.append(Issue(ERROR, "shared-look", f"'{entry['id']}' is shared from '{source}', so its look must be "
                                                      "word for word the same as there (or drop `from` to make it this "
                                                      "story's own character)"))
    return issues


# Graph rules, shared by the outline and the story -------------------------------------------------------------------

def _graph_issues(doc: dict) -> tuple[list[Issue], bool]:
    """Start, dangling targets, cycles, reachability, fork targets. Returns (issues, safe to walk the graph)."""
    nodes = doc["nodes"]
    if doc["start"] not in nodes:
        return [Issue(ERROR, "start", f"start node '{doc['start']}' doesn't exist")], False
    issues, walkable = [], True
    for nid, t in graph.edges(doc):
        if t not in nodes:
            walkable = False
            issues.append(Issue(ERROR, "dangling", f"points to '{t}', which doesn't exist", nid))
    cycle = graph.find_cycle(doc)
    if cycle:
        walkable = False
        issues.append(Issue(ERROR, "cycle", " -> ".join(cycle)))
    for nid in sorted(set(nodes) - graph.reachable(doc)):
        issues.append(Issue(ERROR, "unreachable", "no path from the start reaches this node", nid))
    for nid, node in nodes.items():
        if graph.kind(node) == graph.FORK:
            targets = [o["target"] for o in node["options"]]
            if len(set(targets)) != len(targets):
                issues.append(Issue(ERROR, "same-targets", "both options lead to the same node", nid))
    return issues, walkable


def _structure_budget(doc: dict, b: Budget, walkable: bool) -> list[Issue]:
    nodes = doc["nodes"]
    issues = _range("pages-total", "pages in total", len(nodes), b.pages_total)
    endings = [nid for nid, n in nodes.items() if graph.kind(n) == graph.ENDING]
    issues += _range("endings", "endings", len(endings), b.endings)
    if walkable:
        s = graph.stats(doc)
        issues += _span("pages-per-playthrough", "pages", s.min_pages, s.max_pages, b.pages_per_playthrough)
        issues += _span("forks-per-playthrough", "forks", s.min_forks, s.max_forks, b.forks_per_playthrough)
    kinds = [nodes[e]["ending"]["kind"] for e in endings]
    if kinds.count("best") > 1:
        issues.append(Issue(WARNING, "endings-best", f"{kinds.count('best')} endings are 'best'; one is the goal"))
    return issues


# Outline ------------------------------------------------------------------------------------------------------------

def check_outline(outline: dict, age_band: str, budget: Budget | None = None) -> list[Issue]:
    issues = validate(outline, "outline.schema.json")
    if issues:
        return issues
    graph_issues, walkable = _graph_issues(outline)
    issues += graph_issues
    if graph_issues and not walkable:
        return issues
    return issues + _structure_budget(outline, budget or BANDS[age_band], walkable)


def check_consistency(outline: dict, story: dict) -> list[Issue]:
    """The story's first language (its story/<lang>.json) must have exactly the outline's nodes, kinds and links: the
    outline is the structure. The other languages must match the first (check_same_structure)."""
    issues = []
    o_nodes, s_nodes = outline["nodes"], story["nodes"]
    name = story_name(story)
    hint = "change the structure in outline.json first, then in every story/<lang>.json"
    if outline["start"] != story["start"]:
        issues.append(Issue(ERROR, "outline-mismatch", f"start is '{story['start']}' in {name}, the outline says "
                                                       f"'{outline['start']}' ({hint})"))
    for nid in sorted(set(o_nodes) - set(s_nodes)):
        issues.append(Issue(ERROR, "outline-mismatch", f"in the outline but not in {name} ({hint})", nid))
    for nid in sorted(set(s_nodes) - set(o_nodes)):
        issues.append(Issue(ERROR, "outline-mismatch", f"in {name} but not in the outline ({hint})", nid))
    for nid in sorted(set(o_nodes) & set(s_nodes)):
        o, s = o_nodes[nid], s_nodes[nid]
        if graph.kind(o) != graph.kind(s):
            issues.append(Issue(ERROR, "outline-mismatch", f"a {graph.kind(s)} in {name} but a "
                                                           f"{graph.kind(o)} in the outline", nid))
        elif graph.successors(o) != graph.successors(s):
            issues.append(Issue(ERROR, "outline-mismatch", f"leads to {graph.successors(s)} in {name} but to "
                                                           f"{graph.successors(o)} in the outline", nid))
        elif graph.kind(o) == graph.ENDING and o["ending"]["kind"] != s["ending"]["kind"]:
            issues.append(Issue(ERROR, "outline-mismatch", f"ending kind '{s['ending']['kind']}' in {name}, "
                                                           f"'{o['ending']['kind']}' in the outline", nid))
    return issues


def _shape(node: dict) -> tuple:
    """Everything about a node but its words: what every language shares."""
    return (graph.kind(node), node["image"], graph.successors(node), [o["image"] for o in node.get("options", [])],
            node.get("ending", {}).get("kind"))


def check_same_structure(first: dict, doc: dict) -> list[Issue]:
    """A translation is the same story in other words: the same id, version, age band, cover and start as the first
    language, and the same nodes in the same order, each with the same kind, image, links, cards and ending kind."""
    name, base = story_name(doc), story_name(first)
    hint = f"a translation changes only the words, the rest is the same as in {base}"
    issues = []
    for key in ("id", "version", "ageBand", "cover", "start"):
        if doc[key] != first[key]:
            issues.append(Issue(ERROR, "translation-structure", f"{name}: {key} is {doc[key]!r}, {base} says "
                                                                f"{first[key]!r} ({hint})"))
    a, b = first["nodes"], doc["nodes"]
    for nid in [n for n in a if n not in b]:
        issues.append(Issue(ERROR, "translation-structure", f"in {base} but not in {name} ({hint})", nid))
    for nid in [n for n in b if n not in a]:
        issues.append(Issue(ERROR, "translation-structure", f"in {name} but not in {base} ({hint})", nid))
    shared = [n for n in a if n in b]
    if shared != [n for n in b if n in a]:
        issues.append(Issue(ERROR, "translation-structure", f"{name} has its pages in another order than {base} "
                                                            "(keep the reading order of the first language)"))
    for nid in shared:
        if _shape(a[nid]) != _shape(b[nid]):
            issues.append(Issue(ERROR, "translation-structure", f"{name} gives this node another kind, image, link, "
                                                                f"card or ending kind than {base} ({hint})", nid))
    return issues


# Story --------------------------------------------------------------------------------------------------------------

def check_story(story: dict[str, dict], folder: Path | None = None, budget: Budget | None = None,
                strict: bool = False, shots: dict | None = None) -> list[Issue]:
    """All issues with a story in every language it has: language -> its story/<lang>.json, as load_story() gives it.
    The first language carries the structure (graph and budgets); every other language must match it, and the words
    fit the budgets in each. `folder` enables the asset checks; `budget` overrides the age band's budget."""
    issues = []
    for lang, doc in story.items():
        issues += validate(doc, "story.schema.json", f"story/{lang}.json")
    if issues:
        return issues  # The graph checks assume the shapes are right.

    issues += _language_files(story)
    first = story[first_language(story)]
    graph_issues, walkable = _graph_issues(first)
    issues += graph_issues
    if any(i.code == "start" for i in graph_issues):
        return issues
    for doc in story.values():
        if doc is not first:
            issues += check_same_structure(first, doc)

    b = budget or BANDS[first["ageBand"]]
    issues += _structure_budget(first, b, walkable)
    lo, hi = b.words_per_page
    for lang, doc in story.items():
        for nid, field, text in texts(doc):
            if not text.strip():
                issues.append(Issue(ERROR, "text-empty", f"{field} is empty in story/{lang}.json", nid))
        for nid, node in doc["nodes"].items():
            words = word_count(node["text"])
            if not lo <= words <= hi:
                issues.append(Issue(ERROR, "words", f"{words} words in story/{lang}.json, the band allows {lo}–{hi}",
                                    nid))
    unique_images = set(image_ids(first))
    if len(unique_images) > b.max_images:
        issues.append(Issue(ERROR, "images-budget", f"{len(unique_images)} images, the band allows {b.max_images}"))

    if folder is not None:
        issues += _check_assets(first, list(story), folder, strict, unique_images)
    if shots is not None:
        issues += check_shots(first, shots)
    return issues


def _language_files(story: dict[str, dict]) -> list[Issue]:
    """Each file says its own language, exactly one is the first language, and the others are translated from it."""
    issues = []
    for lang, doc in story.items():
        if doc["language"] != lang:
            issues.append(Issue(ERROR, "story-language", f"story/{lang}.json says its language is "
                                                         f"'{doc['language']}'"))
    originals = [lang for lang, doc in story.items() if not doc.get("translatedFrom")]
    if len(originals) != 1:
        found = ", ".join(f"story/{lang}.json" for lang in originals) or "none"
        issues.append(Issue(ERROR, "story-first", "exactly one language is the one the story was written in, the file "
                                                  f"without translatedFrom; found {found}"))
        return issues
    for lang, doc in story.items():
        if doc.get("translatedFrom") and doc["translatedFrom"] != originals[0]:
            issues.append(Issue(ERROR, "translated-from", f"story/{lang}.json is translated from "
                                                          f"'{doc['translatedFrom']}', but the story's first language "
                                                          f"is '{originals[0]}'"))
    return issues


# Shots --------------------------------------------------------------------------------------------------------------

def check_shots(story: dict, shots: dict) -> list[Issue]:
    """Every image the story references has exactly one shot, with the fields its kind needs."""
    issues = validate(shots, "shots.schema.json")
    if issues:
        return issues
    wanted = set(image_ids(story))
    cards = {o["image"] for n in story["nodes"].values() for o in n.get("options", [])}
    have: dict[str, int] = {}
    for shot in shots["images"]:
        have[shot["id"]] = have.get(shot["id"], 0) + 1
        needs = ("subject", "background") if shot["id"] in cards else ("scene",)
        missing = [f for f in needs if not shot.get(f)]
        if shot["id"] in wanted and missing:
            kind = "a choice card" if shot["id"] in cards else "a page, ending or cover"
            issues.append(Issue(ERROR, "shot-fields", f"'{shot['id']}' is {kind} and needs {', '.join(missing)}"))
    for image_id in sorted(wanted - set(have)):
        issues.append(Issue(ERROR, "shot-missing", f"image '{image_id}' has no entry in shots.json"))
    for image_id in sorted(set(have) - wanted):
        issues.append(Issue(WARNING, "shot-unused", f"shots.json describes '{image_id}', which the story doesn't use"))
    for image_id, n in sorted(have.items()):
        if n > 1:
            issues.append(Issue(ERROR, "shot-duplicate", f"'{image_id}' is described {n} times in shots.json"))
    return issues


def check_shot_links(story: dict, shots: dict, bible: dict) -> list[Issue]:
    """Who and where each shot names exist in the bible, and each `editFrom` is an earlier image of the same shape."""
    if validate(shots, "shots.schema.json"):
        return []  # check_shots reports it
    who = {c["id"] for key in ("cast", "extras", "props") for c in bible.get(key, [])}
    places = {p["id"] for p in bible.get("places", [])}
    order: dict[str, int] = {}
    for i, image_id in enumerate(image_ids(story)):
        order.setdefault(image_id, i)
    cards = {o["image"] for n in story["nodes"].values() for o in n.get("options", [])}

    def shape(image_id):
        return "cover" if image_id == story["cover"] else "card" if image_id in cards else "page"

    issues = []
    for shot in shots["images"]:
        sid = shot["id"]
        for c in shot.get("cast", []):
            if c["id"] not in who:
                issues.append(Issue(ERROR, "shot-cast", f"'{sid}' shows '{c['id']}', who isn't in the bible's cast, "
                                                        "extras or props"))
        if shot.get("place") and shot["place"] not in places:
            issues.append(Issue(ERROR, "shot-place", f"'{sid}' is set in '{shot['place']}', which isn't one of the "
                                                     "bible's places"))
        source = shot.get("editFrom")
        if not source or sid not in order:
            continue
        if source not in order:
            problem = "which the story doesn't use"
        elif shape(source) != shape(sid) or shape(sid) == "cover":
            problem = "a different kind of image (a page or ending edits a page or ending, a card edits a card)"
        elif order[source] >= order[sid]:
            problem = "which doesn't come before it in reading order, so it isn't made yet"
        else:
            continue
        issues.append(Issue(ERROR, "shot-edit", f"'{sid}' is edited from '{source}', {problem}"))
    return issues


def _check_assets(story: dict, languages: list[str], folder: Path, strict: bool, wanted: set[str]) -> list[Issue]:
    issues = []
    level = ERROR if strict else REQUEST
    for image_id in sorted(wanted):
        if find_image(folder / "images", image_id) is None:
            issues.append(Issue(level, "image-missing", f"images/{image_id} not delivered yet (placeholder in use)"))
    clips = audio_clips(story)
    for lang in languages:
        missing = [c for c in clips if find_audio(folder / "audio" / lang, c) is None]
        if not missing:
            continue
        if strict:
            # A language is published with all its clips or none (story package): it reads without narration until
            # every clip is delivered.
            issues.append(Issue(WARNING, "audio-missing",
                                f"narration '{lang}': {len(clips) - len(missing)} of {len(clips)} clips delivered, so "
                                f"'{lang}' is published without narration until all are"))
        else:
            issues.append(Issue(REQUEST, "audio-missing",
                                f"narration '{lang}': {len(clips) - len(missing)} of {len(clips)} clips delivered "
                                f"(see audio/{lang}/REQUESTED_AUDIO.md)"))
        # Timings are checked once narration exists (plan.md §5.2, step 8).
    return issues


# Languages: translations and narrators ------------------------------------------------------------------------------

def check_translation(sheet: dict, lang: str, story: dict[str, dict], bible: dict | None,
                      shots: dict | None) -> list[Issue]:
    """translations/<lang>.json: the names, refrains and moods of one language after the story's first, complete for
    the bible's cast and every page's picture, so the text and its narrator say each of them one way. `story` is every
    language of the story, as load_story() gives it."""
    issues = validate(sheet, "translation.schema.json")
    if issues:
        return issues
    where = f"translations/{lang}.json"
    first = first_language(story)
    if sheet["language"] != lang:
        issues.append(Issue(ERROR, "translation-language", f"{where} says it's '{sheet['language']}'"))
    if sheet["from"] != first:
        issues.append(Issue(ERROR, "translation-from", f"{where} is translated from '{sheet['from']}', but the story's "
                                                       f"first language is '{first}'"))
    if bible is not None:
        entries = bible["cast"] + bible.get("extras", []) + bible.get("props", [])
        for entry in entries:
            name = sheet["cast"].get(entry["id"])
            if name is None:
                issues.append(Issue(ERROR, "translation-name", f"{where} has no name for '{entry['id']}'"))
                continue
            for field in ("refrain", "personality"):
                if entry.get(field) and not name.get(field):
                    issues.append(Issue(ERROR, "translation-cast", f"{where}: '{entry['id']}' has a {field} in the "
                                                                   f"bible but not in '{lang}'"))
        for cid in sorted(set(sheet["cast"]) - {e["id"] for e in entries}):
            issues.append(Issue(WARNING, "translation-unknown", f"{where} names '{cid}', who isn't in the bible"))
    if shots is not None:
        moods = {s["id"] for s in shots.get("images", []) if s.get("mood")}
        pages = {n["image"] for n in story[first]["nodes"].values()} & moods
        for image_id in sorted(pages - set(sheet["moods"])):
            issues.append(Issue(ERROR, "translation-mood", f"{where} has no mood for '{image_id}' (shots.json has one)"))
        for image_id in sorted(set(sheet["moods"]) - pages):
            issues.append(Issue(WARNING, "translation-mood-unused", f"{where} has a mood for '{image_id}', which isn't "
                                                                    "a page or ending with a mood"))
    return issues


def check_voice(voice: dict) -> list[Issue]:
    """content/style/voice.json: one narrator per language."""
    return validate(voice, "voice.schema.json")


def check_languages(folder: Path, story: dict[str, dict], bible: dict | None, shots: dict | None) -> list[Issue]:
    """Every language after the first has its translation sheet, and every language has a narrator. `story` is every
    language of the story, as load_story() gives it."""
    issues: list[Issue] = []
    first = first_language(story)
    others = [lang for lang in story if lang != first]
    for lang in others:
        path = translation_file(folder, lang)
        if not path.is_file():
            issues.append(Issue(ERROR, "translation-missing", f"story/{lang}.json exists, but translations/{lang}.json "
                                                              "is missing (step 6, tools/prompts/translate.md)"))
        else:
            issues += check_translation(load_json(path), lang, story, bible, shots)
    sheets = sorted(p.stem for p in (folder / "translations").glob("*.json")) if (folder / "translations").is_dir() else []
    for lang in sheets:
        if lang == first:
            issues.append(Issue(WARNING, "translation-first", f"translations/{lang}.json: '{lang}' is the story's first "
                                                              "language, whose names are the bible's"))
        elif lang not in others:
            issues.append(Issue(WARNING, "translation-unused", f"translations/{lang}.json: the story has no "
                                                               f"story/{lang}.json (yet)"))
    narrators = load_json(VOICE).get("narrators", {}) if VOICE.is_file() else {}
    for lang in story:
        if lang not in narrators:
            issues.append(Issue(ERROR, "narrator-missing", f"no '{lang}' narrator in content/style/voice.json, so its "
                                                           "narration can't be requested"))
    if bible is not None:
        for lang in story:
            if lang not in bible["title"]:
                issues.append(Issue(WARNING, "bible-title", f"the bible's title has no '{lang}'"))
    return issues


# Catalog and app images (repository-wide) ---------------------------------------------------------------------------

def check_catalog(catalog: dict, stories: dict[str, dict | None],
                  translations: dict[str, dict[str, str]] | None = None) -> list[Issue]:
    """content/catalog.json. `stories` maps every story folder id to its text in every language, as load_story()
    gives it (None if it has none yet); `translations`, each story's translations to their status (draft or
    approved)."""
    issues = validate(catalog, "catalog.schema.json")
    if issues:
        return issues
    seen: set[str] = set()
    products: dict[str, str] = {}
    for entry in catalog["stories"]:
        sid = entry["id"]
        if sid in seen:
            issues.append(Issue(ERROR, "catalog-duplicate", f"'{sid}' is on the shelf twice"))
        seen.add(sid)
        if sid in RESERVED_IDS:
            issues.append(Issue(ERROR, "catalog-reserved", f"'{sid}' is reserved: the published narration at the end "
                                                           f"of every story is in stories/<channel>/{sid}/, so no "
                                                           "story can be called that"))
        if sid not in stories:
            issues.append(Issue(ERROR, "catalog-unknown", f"'{sid}' has no folder in content/stories/"))
        elif stories[sid] is None:
            issues.append(Issue(ERROR, "catalog-unwritten", f"'{sid}' has no text yet (story/<lang>.json), so it "
                                                            "can't be on the shelf"))
        written = list(stories.get(sid) or {})
        for lang in entry.get("approved", []):
            if stories.get(sid) is None:
                break
            if lang not in written:
                issues.append(Issue(ERROR, "catalog-approved", f"'{sid}' is approved in '{lang}', a language it isn't "
                                                               "written in"))
            elif lang != written[0] and (translations or {}).get(sid, {}).get(lang) != "approved":
                issues.append(Issue(ERROR, "catalog-approved", f"'{sid}' is approved in '{lang}', but a native "
                                                               f"speaker hasn't approved that translation yet (its "
                                                               f"status in translations/{lang}.json)"))
        if entry.get("product"):
            if entry["product"] in products:
                issues.append(Issue(ERROR, "catalog-product", f"'{sid}' and '{products[entry['product']]}' share the "
                                                              f"product '{entry['product']}'"))
            products[entry["product"]] = sid
    if catalog["stories"] and catalog["stories"][0]["access"] != "free":
        issues.append(Issue(WARNING, "catalog-first-free", "the first story on the shelf is paid: new families "
                                                           "should be able to read one right away"))
    return issues


def check_app_assets(doc: dict) -> list[Issue]:
    """content/app/assets.json: the app icon and its Android layers. Their style, content/app/style.json, is checked
    with check_style(style, anchor=False)."""
    issues = validate(doc, "app-assets.schema.json")
    if issues:
        return issues
    ids = [i["id"] for i in doc["images"]]
    for image_id in sorted({i for i in ids if ids.count(i) > 1}):
        issues.append(Issue(ERROR, "app-duplicate", f"'{image_id}' is described {ids.count(image_id)} times"))
    for i, image in enumerate(doc["images"]):
        source = image.get("editFrom")
        if source and source not in ids[:i]:
            issues.append(Issue(ERROR, "app-edit", f"'{image['id']}' is edited from '{source}', which isn't an "
                                                   "earlier image in this file"))
    return issues


def check_app_narration(doc: dict, narrators: dict, folder: Path | None = None, strict: bool = False) -> list[Issue]:
    """content/app/narration.json: the lines the narrator says at the end of every story, one entry per language, for
    every narrator in voice.json (`narrators`) and no other. `folder` (content/app/) enables the delivery checks: clips
    not delivered yet are requests, and in `strict` mode warnings, like a story's narration."""
    issues = validate(doc, "app-narration.schema.json")
    if issues:
        return issues
    for lang in narrators:
        if lang not in doc:
            issues.append(Issue(ERROR, "app-narration-missing", f"the '{lang}' narrator has no lines in "
                                                                "content/app/narration.json, the end of every story"))
    for lang in doc:
        if lang not in narrators:
            issues.append(Issue(ERROR, "narrator-missing", f"no '{lang}' narrator in content/style/voice.json, so the "
                                                           f"'{lang}' lines at the end of every story can't be "
                                                           "requested"))
    if folder is None:
        return issues
    for lang in doc:
        missing = [c for c in APP_CLIPS if find_audio(audio_dir(folder, lang), c) is None]
        if not missing:
            continue
        done = len(APP_CLIPS) - len(missing)
        if strict:
            issues.append(Issue(WARNING, "app-audio-missing",
                                f"app narration '{lang}': {done} of {len(APP_CLIPS)} clips delivered, so '{lang}' is "
                                "published without them until all are"))
        else:
            issues.append(Issue(REQUEST, "app-audio-missing",
                                f"app narration '{lang}': {done} of {len(APP_CLIPS)} clips delivered "
                                f"(see app/audio/{lang}/REQUESTED_AUDIO.md)"))
    return issues


# Whole story folder -------------------------------------------------------------------------------------------------

# The pipeline's steps 1 to 5, by the file (or folder) each one writes. story/ holds the text, one file per language.
STAGES = ["bible.json", "outline.json", "story/", "review.md", "shots.json"]


def check_folder(folder: Path, strict: bool = False) -> list[Issue]:
    """Checks every pipeline file present in a story folder, and how they fit together."""
    def load(name):
        return load_json(folder / name) if (folder / name).is_file() else None

    bible, outline, shots = load("bible.json"), load("outline.json"), load("shots.json")
    story = load_story(folder)
    first = next(iter(story.values())) if story else None
    issues: list[Issue] = []
    bible_ok = False
    if bible is None:
        issues.append(Issue(ERROR, "bible-missing", "bible.json is missing (pipeline step 1)"))
    else:
        bible_issues = check_bible(bible)
        issues += bible_issues
        bible_ok = not any(i.code == "schema" for i in bible_issues)
        if bible_ok:
            issues += check_style_and_sharing(folder, bible, shots=shots is not None)
    band = (first or {}).get("ageBand") or (bible or {}).get("ageBand")
    outline_ok = False
    if outline is not None and band in BANDS:
        outline_issues = check_outline(outline, band)
        issues += outline_issues
        outline_ok = not any(i.code == "schema" for i in outline_issues)
    if story is not None:
        issues += check_story(story, folder=folder, strict=strict, shots=shots)
        story_ok = not any(validate(doc, "story.schema.json") for doc in story.values())
        shots_ok = shots is not None and not validate(shots, "shots.schema.json")
        if outline is None:
            issues.append(Issue(ERROR, "outline-missing", "the story has its text (story/) but no outline.json"))
        elif outline_ok and story_ok:
            issues += check_consistency(outline, first)
        if story_ok:
            if bible is not None and bible["ageBand"] != first["ageBand"]:
                issues.append(Issue(ERROR, "age-band", f"bible says {bible['ageBand']}, {story_name(first)} says "
                                                       f"{first['ageBand']}"))
            if bible is not None and shots_ok:
                issues += check_shot_links(first, shots, bible)
            issues += check_languages(folder, story, bible if bible_ok else None, shots if shots_ok else None)
    elif shots is not None:
        issues.append(Issue(WARNING, "shots-early", "shots.json exists but the story has no text (story/) yet"))
    return issues


def _range(code: str, what: str, value: int, allowed: tuple[int, int]) -> list[Issue]:
    lo, hi = allowed
    if lo <= value <= hi:
        return []
    return [Issue(ERROR, code, f"{value} {what}, the band allows {lo}–{hi}")]


def _span(code: str, what: str, low: int, high: int, allowed: tuple[int, int]) -> list[Issue]:
    lo, hi = allowed
    issues = []
    if low < lo:
        issues.append(Issue(ERROR, f"{code}-low", f"the shortest play-through has {low} {what}, "
                                                  f"the band allows {lo}–{hi}"))
    if high > hi:
        issues.append(Issue(ERROR, f"{code}-high", f"the longest play-through has {high} {what}, "
                                                   f"the band allows {lo}–{hi}"))
    return issues
