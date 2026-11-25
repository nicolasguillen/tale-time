"""`story package`: builds what the app downloads (docs/story-format.md, "Published stories"; plan.md §7.3).

The app shows every story on the shelf, and downloads one when a reader opens it, in the language they pick: its
text, its pictures and that language's narration. It keeps the story on the device to read offline, and at every
launch it checks whether the story changed. Everything it downloads is built here from content/, one folder per
channel, laid out like the content host (stories/<channel>/ in the bucket):

    catalog.json                the shelf: each story's title in every language, its cover, its languages (with their
                                download size and whether they're narrated) and its current manifest
    <id>/manifest-<hash>.json   every file of the story: its pictures, and per language its text, its clips and when
                                each word of a narrated page is said (its timings)
    <id>/<name>-<hash>.<ext>    the files themselves, named after their contents

A changed picture, clip or text gets a new name, so the manifest listing it changes too, and so does the manifest's name
in the catalog. That's how the app sees that a story changed: it compares that name with the one it has, then downloads
only the files it doesn't have yet. Nothing on the host is ever overwritten, so every file but the catalog can be
cached forever.

Two channels, each in content/.build/stories/<channel>/ and uploaded by `story publish <channel>` to
stories/<channel>/ on the host:
- draft: content/ as it is, for debug builds: draft translations, and whatever pictures and clips are delivered so far.
  A missing picture shows the app's placeholder, and a page without its clip reads without narration.
- production: for release builds. Only the stories that pass `story check --strict` (every picture delivered), in
  their first language and each translation a native speaker approved. A language's narration ships once every one of
  its clips is delivered; until then that language reads without narration.

Packaging only converts: pictures to WebP, scaled down to at most the sizes below, and clips to mono AAC with their
loudness evened out. It never makes or changes a picture or a sound. It also listens to each page's clip for when its
words are said (storytool/timings.py), so the app can show them as the narrator reads. Converted files and timings are
cached in content/.build/cache/ by their source's contents, so a file is converted again only when it changes.
"""
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

from .timings import VERSION as TIMINGS_VERSION, listen, timings as word_timings
from .check import ERROR, audio_clips, check_folder, image_ids
from .repo import CATALOG, CONTENT, find_audio, find_image, load_json, load_story, translation_file

FORMAT = 1  # the catalog's and the manifest's format; the app ignores what it doesn't know
BUILD = CONTENT / ".build"
CHANNELS = ("draft", "production")
CATALOG_FILE = "catalog.json"  # in the channel's folder

# The largest size of each kind of picture, in pixels. A picture is scaled down to fit, never up.
COVER_BOX = (900, 1200)  # 3:4, sharp on the shelf and on a tablet's cover screen
PAGE_BOX = (1600, 1200)  # 4:3, pages and endings
CARD_BOX = (768, 768)  # 1:1, choice cards
WEBP_QUALITY = 82
AAC_BITRATE = 64_000  # mono speech
LOUDNESS = -16.0  # LUFS, integrated, the same for every clip
TRUE_PEAK = -1.5  # dBTP
HASH_CHARS = 10


class Encoder:
    """Converts a delivered file into the one the app downloads."""
    version = "2"  # part of every cache key: bump it when the conversion changes

    def image(self, source: Path, box: tuple[int, int], dest: Path) -> None:
        """WebP, scaled down to fit inside `box`."""
        from PIL import Image, ImageOps

        with Image.open(source) as opened:
            picture = ImageOps.exif_transpose(opened)
            picture = picture.convert("RGBA" if "A" in picture.getbands() else "RGB")
            picture.thumbnail(box, Image.Resampling.LANCZOS)
            picture.save(dest, "WEBP", quality=WEBP_QUALITY, method=6)

    def audio(self, source: Path, dest: Path) -> None:
        """Mono AAC (.m4a) at the same loudness as every other clip: ffmpeg measures it and evens it out (EBU R128, two
        passes, linear), and macOS's afconvert encodes it (ffmpeg's own AAC encoder if afconvert isn't there)."""
        measured = _loudness(source)
        normalize = (f"loudnorm=I={LOUDNESS}:TP={TRUE_PEAK}:LRA=11:linear=true:"
                     f"measured_I={measured['input_i']}:measured_TP={measured['input_tp']}:"
                     f"measured_LRA={measured['input_lra']}:measured_thresh={measured['input_thresh']}:"
                     f"offset={measured['target_offset']}")
        with tempfile.TemporaryDirectory() as tmp:
            wav, aac = Path(tmp) / "even.wav", Path(tmp) / "encoded.m4a"
            _run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source), "-af", normalize,
                 "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", str(wav))
            if shutil.which("afconvert"):
                _run("afconvert", "-f", "m4af", "-d", "aac", "-b", str(AAC_BITRATE), str(wav), str(aac))
            else:
                _run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "aac",
                     "-b:a", str(AAC_BITRATE), "-f", "ipod", str(aac))
            # The same clip must always give the same file, or its name (its hash) changes and every device downloads
            # it again: afconvert stamps the time into the container, so rewrap the AAC without it.
            _run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(aac), "-c", "copy", "-map_metadata",
                 "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", "-f", "ipod", str(dest))

    def timings(self, source: Path, text: str) -> list[list[int]]:
        """When each word of `text` (each run of non-space characters) starts and ends in the clip, in milliseconds.
        Packaging neither moves nor trims the sound, so they hold for the converted clip too."""
        return [[round(start * 1000), round(end * 1000)] for start, end in word_timings(text, listen(source))]


def _run(*args: str) -> subprocess.CompletedProcess:
    if not shutil.which(args[0]):
        raise SystemExit(f"`{args[0]}` isn't installed: packaging needs ffmpeg (brew install ffmpeg)")
    return subprocess.run(args, check=True, capture_output=True, text=True)


def _loudness(source: Path) -> dict:
    """ffmpeg's loudnorm measurement of a clip (its first pass)."""
    out = _run("ffmpeg", "-hide_banner", "-nostats", "-i", str(source), "-af",
               f"loudnorm=I={LOUDNESS}:TP={TRUE_PEAK}:LRA=11:print_format=json", "-f", "null", "-").stderr
    return json.loads(out[out.rindex("{"):out.rindex("}") + 1])


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _json(doc, pretty: bool = True) -> bytes:
    if pretty:
        return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode()
    return json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode()


@dataclass
class Language:
    """One language of a packaged story, for the summary."""
    lang: str
    clips: int
    of: int
    bytes: int


@dataclass
class Packaged:
    id: str
    languages: list[Language]
    images: int
    of: int


@dataclass
class Result:
    channel: str
    root: Path  # the folder laid out like the host
    stories: list[Packaged] = field(default_factory=list)
    skipped: dict[str, list[str]] = field(default_factory=dict)  # production only: story id -> why it isn't published

    @property
    def catalog(self) -> Path:
        return self.root / CATALOG_FILE


class Packager:
    def __init__(self, channel: str, content: Path = CONTENT, out: Path | None = None, cache: Path | None = None,
                 encoder: Encoder | None = None, check=check_folder):
        if channel not in CHANNELS:
            raise ValueError(f"unknown channel '{channel}'")
        self.channel = channel
        self.content = content
        self.root = out or BUILD / host_folder(channel)
        self.cache = cache or BUILD / "cache"
        self.encoder = encoder or Encoder()
        self.check = check

    def build(self) -> Result:
        """Packages every story on the shelf (content/catalog.json) and writes the catalog last, so it never names a
        file that isn't there yet. The folder is emptied first, so it holds this package and nothing else (`story
        publish draft` deletes from the host what isn't in it)."""
        if self.root.exists():
            shutil.rmtree(self.root)
        self.root.mkdir(parents=True, exist_ok=True)
        result = Result(self.channel, self.root)
        shelf = load_json(self.content / CATALOG.relative_to(CONTENT))
        entries = []
        for entry in shelf["stories"]:
            folder = self.content / "stories" / entry["id"]
            why = self._unpublishable(folder)
            if why:
                result.skipped[entry["id"]] = why
                continue
            catalog_entry, packaged = self._story(entry, folder)
            entries.append(catalog_entry)
            result.stories.append(packaged)
        _write(result.catalog, _json({"format": FORMAT, "stories": entries}))
        return result

    def _unpublishable(self, folder: Path) -> list[str]:
        """Why a story can't go out on this channel, or nothing."""
        if load_story(folder) is None:
            return ["it has no text yet (story/<lang>.json)"]
        if self.channel == "draft":
            return []
        errors = [str(i) for i in self.check(folder, strict=True) if i.level == ERROR and i.code != "image-missing"]
        first = next(iter(load_story(folder).values()))
        wanted = list(dict.fromkeys(image_ids(first)))
        missing = [i for i in wanted if find_image(folder / "images", i) is None]
        if missing:
            errors.insert(0, f"{len(missing)} of {len(wanted)} pictures not delivered yet")
        return errors

    def languages(self, folder: Path) -> list[str]:
        """The story's languages on this channel, its first language first: every one on draft, and on production
        the first and each translation approved by a native speaker."""
        story = load_story(folder)
        langs = list(story)
        if self.channel == "draft":
            return langs
        approved = []
        for lang in langs[1:]:
            sheet = translation_file(folder, lang)
            if sheet.is_file() and load_json(sheet).get("status") == "approved":
                approved.append(lang)
        return [langs[0], *approved]

    def _story(self, entry: dict, folder: Path) -> tuple[dict, Packaged]:
        sid = entry["id"]
        story = load_story(folder)
        langs = self.languages(folder)
        first = story[langs[0]]
        out = self.root / sid
        out.mkdir(parents=True, exist_ok=True)
        files: dict[str, dict] = {}

        # Pictures, the same in every language.
        cards = {o["image"] for node in first["nodes"].values() for o in node.get("options", [])}
        wanted = list(dict.fromkeys(image_ids(first)))
        images: dict[str, str] = {}
        for image_id in wanted:
            source = find_image(folder / "images", image_id)
            if source is None:
                continue
            box = COVER_BOX if image_id == first["cover"] else CARD_BOX if image_id in cards else PAGE_BOX
            images[image_id] = self._converted(out, files, image_id, ".webp", source, f"image {box[0]}x{box[1]}",
                                               lambda dest, s=source, b=box: self.encoder.image(s, b, dest))
        image_bytes = sum(files[name]["bytes"] for name in images.values())

        # Each language: its text, its narration, and when each page's words are said.
        clips = audio_clips(first)
        languages: dict[str, dict] = {}
        offered: dict[str, dict] = {}
        summary = []
        for lang in langs:
            text = _json(story[lang], pretty=False)
            text_name = self._place(out, files, f"story-{lang}", ".json", text)
            delivered = {c: find_audio(folder / "audio" / lang, c) for c in clips}
            delivered = {c: path for c, path in delivered.items() if path is not None}
            if self.channel == "production" and len(delivered) < len(clips):
                delivered = {}  # all of a language's clips, or none: it reads without narration until then
            audio = {clip: self._converted(out, files, f"{lang}-{clip}", ".m4a", path, "audio",
                                           lambda dest, s=path: self.encoder.audio(s, dest))
                     for clip, path in delivered.items()}
            nodes = story[lang]["nodes"]
            heard = {clip: self._timed(path, nodes[clip]["text"]) for clip, path in delivered.items() if clip in nodes}
            languages[lang] = {"story": text_name, "audio": audio}
            if heard:
                languages[lang]["timings"] = self._place(out, files, f"timings-{lang}", ".json",
                                                         _json({"format": FORMAT, "clips": heard}, pretty=False))
            names = [text_name, *audio.values(), *([languages[lang]["timings"]] if heard else [])]
            size = image_bytes + sum(files[n]["bytes"] for n in names)
            offered[lang] = {"bytes": size, "narration": bool(audio)}
            summary.append(Language(lang, len(audio), len(clips), size))

        manifest = {"format": FORMAT, "id": sid, "images": images, "languages": languages, "files": files}
        manifest_name = self._place(out, {}, "manifest", ".json", _json(manifest))
        data = (out / manifest_name).read_bytes()
        catalog_entry = {
            "id": sid,
            "access": entry["access"],
            **({"product": entry["product"]} if entry.get("product") else {}),
            "ageBand": first["ageBand"],
            "title": {lang: story[lang]["title"] for lang in langs},
            "languages": offered,
            **({"cover": {"file": images[first["cover"]], **files[images[first["cover"]]]}}
               if first["cover"] in images else {}),
            "manifest": {"file": manifest_name, "bytes": len(data), "sha256": sha256(data)},
        }
        return catalog_entry, Packaged(sid, summary, len(images), len(wanted))

    def _place(self, out: Path, files: dict, stem: str, ext: str, data: bytes) -> str:
        """Writes `data` into the story's folder under a name made from its contents, and lists it in `files`."""
        digest = sha256(data)
        name = f"{stem}-{digest[:HASH_CHARS]}{ext}"
        target = out / name
        if not target.is_file():
            _write(target, data)
        files[name] = {"bytes": len(data), "sha256": digest}
        return name

    def _timed(self, source: Path, text: str) -> list[list[int]]:
        """When each word of a page is said in its clip, from the cache if this clip and text were measured before."""
        key = sha256(f"{TIMINGS_VERSION}|timings|{sha256(source.read_bytes())}|{text}".encode())
        cached = self.cache / f"{key}.json"
        if not cached.is_file():
            self.cache.mkdir(parents=True, exist_ok=True)
            _write(cached, _json(self.encoder.timings(source, text), pretty=False))
        return json.loads(cached.read_text())

    def _converted(self, out: Path, files: dict, stem: str, ext: str, source: Path, how: str, convert) -> str:
        """The converted file, from the cache if this very source was converted the same way before."""
        key = sha256(f"{self.encoder.version}|{how}|{sha256(source.read_bytes())}".encode())
        cached = self.cache / f"{key}{ext}"
        if not cached.is_file():
            self.cache.mkdir(parents=True, exist_ok=True)
            partial = cached.with_name(cached.name + ".part")
            convert(partial)
            os.replace(partial, cached)
        return self._place(out, files, stem, ext, cached.read_bytes())


def _write(path: Path, data: bytes) -> None:
    """Writes a file whole, so a reader never sees half of it."""
    path.parent.mkdir(parents=True, exist_ok=True)
    partial = path.with_name(path.name + ".part")
    partial.write_bytes(data)
    os.replace(partial, path)


def host_folder(channel: str) -> str:
    """The channel's folder on the content host, and in content/.build/."""
    return f"stories/{channel}"


def package(channel: str, **kw) -> Result:
    return Packager(channel, **kw).build()


def describe(result: Result) -> list[str]:
    """What was packaged, in a few lines."""
    lines = [f"{result.channel}: {len(result.stories)} {'story' if len(result.stories) == 1 else 'stories'} in "
             f"{result.root}"]
    for s in result.stories:
        langs = ", ".join(f"{lang.lang} ({mb(lang.bytes)}, " +
                          (f"{lang.clips} of {lang.of} clips" if lang.clips else "no narration") + ")"
                          for lang in s.languages)
        lines.append(f"  {s.id}: {s.images} of {s.of} pictures; {langs}")
    for sid, why in result.skipped.items():
        lines.append(f"  {sid}: not packaged: {why[0]}" + (f" (and {len(why) - 1} more)" if len(why) > 1 else ""))
    return lines


def mb(size: int) -> str:
    return f"{size / 1_000_000:.1f} MB"
