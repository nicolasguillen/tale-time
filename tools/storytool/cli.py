"""The `story` command. Run it from tools/: `uv run story <command> ...`.

It never calls a model and needs no API key. The writing steps (bible, outline, text, editor pass, shots) are done
in Claude Code by the /new-story skill, following tools/prompts/. This CLI does the deterministic parts.
"""
import argparse
import sys

from . import assets, audio, graph, package, publish, serve
from .check import (ERROR, REQUEST, STAGES, WARNING, Issue, check_app_assets, check_catalog, check_folder,
                    check_style, check_voice)
from .repo import (APP_ASSETS, APP_STYLE, CATALOG, ROOT, STORIES, VOICE, characters_dir, find_image, has_story,
                   load_json, load_story, story_dir, story_file, story_ids, style_dir, translation_file)


def _repo_issues():
    """The catalog (the shelf, free and paid), the narrators and the app's own images."""
    found = []
    if VOICE.is_file():
        found += [("style/voice.json", i) for i in check_voice(load_json(VOICE))]
    else:
        found.append(("style/voice.json", Issue(ERROR, "voice-missing", "content/style/voice.json is missing: the "
                                                                        "narrators")))
    if CATALOG.is_file():
        stories = {p.name: load_story(p) for p in STORIES.iterdir() if p.is_dir()}
        found += [("catalog.json", i) for i in check_catalog(load_json(CATALOG), stories)]
    else:
        found.append(("catalog.json", Issue(ERROR, "catalog-missing", "content/catalog.json is missing")))
    if APP_ASSETS.is_file():
        found += [("app/assets.json", i) for i in check_app_assets(load_json(APP_ASSETS))]
        if APP_STYLE.is_file():
            found += [("app/style.json", i) for i in check_style(load_json(APP_STYLE), anchor=False)]
        else:
            found.append(("app/style.json", Issue(ERROR, "app-style", "content/app/style.json is missing: the app icon "
                                                                      "has its own style")))
    return found


def cmd_check(args) -> int:
    failed = False
    if not args.ids:
        repo = _repo_issues()
        errors = [i for _, i in repo if i.level == ERROR]
        print(f"catalog, narrators and app images: {len(errors)} errors, {len(repo) - len(errors)} warnings")
        for name, issue in repo:
            print(f"  {name}: {issue}")
        failed |= bool(errors)
    for sid in args.ids or story_ids():
        folder = story_dir(sid)
        issues = check_folder(folder, strict=args.strict)
        errors = [i for i in issues if i.level == ERROR]
        warnings = [i for i in issues if i.level == WARNING]
        requests = [i for i in issues if i.level == REQUEST]
        print(f"{sid}: {len(errors)} errors, {len(warnings)} warnings, {len(requests)} assets requested")
        for issue in errors + warnings:
            print(f"  {issue}")
        if requests and args.verbose:
            for issue in requests:
                print(f"  {issue}")
        elif requests:
            print(f"  (images and narration not delivered yet are requested in content/stories/{sid}/REQUESTED_ASSETS.md "
                  "and audio/<lang>/REQUESTED_AUDIO.md; -v lists them here)")
        doc = _graph_doc(folder)
        if doc and not errors:
            s = graph.stats(doc)
            print(f"  {len(doc['nodes'])} pages, {s.paths} play-throughs of {s.min_pages}–{s.max_pages} pages "
                  f"with {s.min_forks}–{s.max_forks} forks, {len(graph.covering_paths(doc))} paths cover every "
                  "page and choice")
        failed |= bool(errors)
    return 1 if failed else 0


def _graph_doc(folder):
    """The story in its first language if it has its text, else outline.json, else None."""
    story = load_story(folder)
    if story:
        return next(iter(story.values()))
    return load_json(folder / "outline.json") if (folder / "outline.json").is_file() else None


def cmd_paths(args) -> int:
    folder = story_dir(args.id)
    story = load_story(folder)
    if story:
        lang = args.lang or next(iter(story))
        if lang not in story:
            raise SystemExit(f"{args.id} has no story/{lang}.json. Its languages: {', '.join(story)}")
        doc = story[lang]
        title = doc["title"]
    elif (folder / "outline.json").is_file():
        doc = load_json(folder / "outline.json")
        title = load_json(folder / "bible.json")["title"]
        title = title.get(args.lang) or next(iter(title.values()))
    else:
        raise SystemExit(f"{args.id} has no outline.json or story/ yet.")
    paths = graph.all_paths(doc) if args.all else graph.covering_paths(doc)
    nodes = doc["nodes"]
    what = "every play-through" if args.all else "paths that together cover every page and every choice"
    source = "" if story else " (from the outline: beats, not text yet)"
    print(f"# {title}: {len(paths)} {what}{source}\n")
    for n, path in enumerate(paths, 1):
        end = nodes[path[-1][0]]["ending"]
        print(f"## Path {n}: {end['title']} ({end['kind']})\n")
        print(" → ".join(nid for nid, _ in path) + "\n")
        for nid, choice in path:
            node = nodes[nid]
            body = node["text"] if story else node["beat"]
            print(f"**{nid}**  \n{body}\n")
            if choice is not None:
                labels = [o["label"] for o in node["options"]]
                print(f"> *{node['question']}*  \n> **→ {labels[choice]}** (not: {labels[1 - choice]})\n")
        print()
    return 0


NEXT_STEP = {
    "bible.json": "step 1, bible (tools/prompts/bible.md)",
    "outline.json": "step 2, outline (tools/prompts/outline.md)",
    "story/": "step 3, text (tools/prompts/text.md)",
    "review.md": "step 4, editor pass (tools/prompts/edit.md)",
    "shots.json": "step 5, shots (tools/prompts/shots.md)",
}


def cmd_status(args) -> int:
    for sid in args.ids or story_ids():
        folder = story_dir(sid)
        story = load_story(folder)
        print(f"{sid}:")
        missing = []
        for name in STAGES:
            if name == "story/":  # the text in the story's first language; the others are step 6, below
                present = has_story(folder)
                label = story_file(folder, next(iter(story))).relative_to(folder).as_posix() if story else name
            else:
                present, label = (folder / name).is_file(), name
            print(f"  {'✓' if present else '·'} {label}")
            if not present:
                missing.append(name)
            if name == "bible.json" and present:
                bible = load_json(folder / "bible.json")
                own = (style_dir(folder) / "style.json").is_file()
                if bible.get("styleFrom"):
                    print(f"    art style: shared from {bible['styleFrom']} (styleFrom)")
                else:
                    print(f"  {'✓' if own else '·'} style/style.json (this story's own art style)")
        if (folder / "shots.json").is_file() and story:
            bible = load_json(folder / "bible.json")
            images = sorted(set(assets.image_ids(next(iter(story.values())))))
            sheets = [c["id"] for c in bible.get("cast", []) + bible.get("props", []) if c.get("sheet") and not c.get("from")]
            done = sum(find_image(folder / "images", i) is not None for i in images)
            done += sum(find_image(characters_dir(folder), c) is not None for c in sheets)
            total = len(images) + len(sheets)
            own_style = style_dir(folder) / "style.json"
            if not bible.get("styleFrom") and own_style.is_file():
                total += 1
                done += find_image(style_dir(folder), load_json(own_style).get("anchor", {}).get("id", "")) is not None
            print(f"  images delivered: {done} of {total} (see content/stories/{sid}/REQUESTED_ASSETS.md)")
        if story:
            for lang in list(story)[1:]:
                sheet = translation_file(folder, lang)
                state = load_json(sheet).get("status", "draft") if sheet.is_file() else None
                print(f"  {'✓' if state == 'approved' else '·'} story/{lang}.json and translations/{lang}.json "
                      f"({state + ', ' if state else 'no sheet yet, '}step 6, tools/prompts/translate.md)")
            narration = audio.Story(folder)
            for lang, doc in story.items():
                clips = audio.clips(doc)
                have = sum(narration.delivered(lang, c.id) is not None for c in clips)
                print(f"  narration delivered ({lang}): {have} of {len(clips)} clips "
                      f"(see content/stories/{sid}/audio/{lang}/REQUESTED_AUDIO.md)")
        issues = check_folder(folder)
        errors = [i for i in issues if i.level == ERROR]
        if errors:
            print(f"  {len(errors)} errors: run `uv run story check {sid}`")
        if missing:
            print(f"  next: {NEXT_STEP[missing[0]]}")
        else:
            print("  next: your read-through and review gates (review.md), then the images and narration requested in "
                  f"content/stories/{sid}/REQUESTED_ASSETS.md and audio/<lang>/REQUESTED_AUDIO.md")
    return 0


def cmd_assets(args) -> int:
    stale = False
    for label, module in (("Image requests (REQUESTED_ASSETS.md)", assets), ("Narration requests (REQUESTED_AUDIO.md)", audio)):
        changed = module.sync(check_only=args.check)
        stale |= bool(changed)
        names = ", ".join(p.relative_to(ROOT).as_posix() for p in changed)
        if not changed:
            print(f"{label}: up to date." if args.check else f"{label}: already up to date.")
        elif args.check:
            print(f"{label}: out of date, run `story assets`: {names}")
        else:
            print(f"{label}: wrote {names}")
    return 1 if args.check and stale else 0


def cmd_package(args) -> int:
    for channel in args.channels or ["dev"]:
        for line in package.describe(package.package(channel)):
            print(line)
    return 0


def cmd_serve(args) -> int:
    serve.serve(port=args.port, lan=args.lan)
    return 0


def cmd_publish(args) -> int:
    return publish.publish(dry_run=args.dry_run)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="story", description="The Tale Time content pipeline (no model calls).")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("check", help="validate every pipeline file of the stories (bible, outline, story, shots)")
    p.add_argument("ids", nargs="*", help="story ids (default: all)")
    p.add_argument("--strict", action="store_true", help="publishing mode: every image and clip must be delivered")
    p.add_argument("-v", "--verbose", action="store_true", help="also list each asset that isn't delivered yet")
    p.set_defaults(func=cmd_check)

    p = sub.add_parser("status", help="which pipeline steps are done, and what's next")
    p.add_argument("ids", nargs="*", help="story ids (default: all)")
    p.set_defaults(func=cmd_status)

    p = sub.add_parser("paths", help="print play-throughs as Markdown (from story/<lang>.json, or the outline before "
                       "that)")
    p.add_argument("id")
    p.add_argument("--all", action="store_true", help="every play-through, not just a covering set")
    p.add_argument("--lang", help="language (default: the story's first)")
    p.set_defaults(func=cmd_paths)

    p = sub.add_parser("assets", help="write the REQUESTED_ASSETS.md and REQUESTED_AUDIO.md files, at the root, in each story folder and in its audio/<lang>/ folders (every image and narration clip to make, "
                       "with its spec and description)")
    p.add_argument("--check", action="store_true", help="only report whether the file is up to date")
    p.set_defaults(func=cmd_assets)

    p = sub.add_parser("package", help="build what the app downloads (the catalog, and per story its manifest, pictures "
                       "as WebP, texts and clips as AAC) into content/.build/<channel>/")
    p.add_argument("channels", nargs="*", choices=package.CHANNELS,
                   help="dev (content as it is, for debug builds; the default) or release (what `publish` uploads)")
    p.set_defaults(func=cmd_package)

    p = sub.add_parser("serve", help="serve the dev channel to debug builds of the app (http://localhost:8765)")
    p.add_argument("--port", type=int, default=serve.PORT)
    p.add_argument("--lan", action="store_true", help="also answer other devices on the network, not just this Mac")
    p.set_defaults(func=cmd_serve)

    p = sub.add_parser("publish", help="upload the release channel to Firebase Storage (only when the user asks)")
    p.add_argument("--dry-run", action="store_true", help="package and show what would be uploaded, upload nothing")
    p.set_defaults(func=cmd_publish)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
