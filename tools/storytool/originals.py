"""`story originals`: the delivered pictures and narration, kept in Firebase Storage instead of git.

Every picture and clip delivered into content/ (story pictures, reference sheets, style anchors, narration clips, the
app icon) is an original: made outside the repo, read-only, and too heavy for git. .gitignore keeps them out of it.
content/originals.json, which is committed, lists each one by its path under content/ with its size and SHA-256, and
the bucket keeps the files themselves, privately, under originals/, named after their contents:

    gs://tale-time-b537d.firebasestorage.app/originals/<path under content/, with -<hash> before the extension>
    e.g. originals/stories/fox-and-the-lantern/images/p01-meadow-3fa2b1c4d5.png

A redelivered picture gets a new name, so the one it replaced stays in the bucket: nothing there is ever overwritten
or deleted, and every commit's originals.json says exactly which originals that version of the content used.

- `story originals` (the status, no network): what's new or changed here and not pushed yet, and what's listed but
  missing here.
- `story originals push`: uploads every original here that the bucket doesn't have yet, then rewrites originals.json.
  Run it after every delivery: until then the delivered file is only on this computer.
- `story originals pull`: downloads every listed original that's missing here (after a clone, or a delivery pushed
  from another computer). It never overwrites a file that's different here.

`story package` and `story publish` pull what's missing first, so a fresh clone packages every delivered picture and
clip, and `story publish` pushes what isn't kept yet, so nothing published exists only on this computer.

backend/storage.rules let nobody read or write originals/ through the API. These upload and download with the owner's
gcloud login, like `story publish`.
"""
import hashlib
import json
import os
import shutil
import subprocess
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

from .package import HASH_CHARS
from .publish import BUCKET
from .repo import AUDIO_EXTENSIONS, CONTENT, IMAGE_EXTENSIONS

EXTENSIONS = IMAGE_EXTENSIONS + AUDIO_EXTENSIONS
LOCK = CONTENT / "originals.json"
PREFIX = "originals"
SKIPPED = (".build", ".work")  # under content/: what packaging builds, and work in progress
ABOUT = ("The delivered pictures and narration under content/, which aren't in git. The files are in Firebase "
         "Storage, gs://" + BUCKET + "/" + PREFIX + "/, named after their contents. Written by `story originals push`; "
         "`story originals pull` downloads them.")


@dataclass(frozen=True)
class Entry:
    bytes: int
    sha256: str


def object_name(path: str, entry: Entry) -> str:
    """Where an original is kept in the bucket, under originals/: its path under content/ with the start of its hash
    before the extension."""
    p = PurePosixPath(path)
    return (p.parent / f"{p.stem}-{entry.sha256[:HASH_CHARS]}{p.suffix}").as_posix()


def _digest(path: Path) -> Entry:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return Entry(path.stat().st_size, h.hexdigest())


def files_here(content: Path = CONTENT) -> dict[str, Path]:
    """Every original in content/ on this computer: its path under content/ -> the file."""
    found = {}
    for folder, dirs, files in os.walk(content):
        here = Path(folder)
        if here == content:
            dirs[:] = [d for d in dirs if d not in SKIPPED]
        dirs.sort()
        for name in sorted(files):
            path = here / name
            if path.suffix.lower() in EXTENSIONS:
                found[path.relative_to(content).as_posix()] = path
    return found


def read_lock(lock: Path = LOCK) -> dict[str, Entry]:
    if not lock.is_file():
        return {}
    with open(lock, encoding="utf-8") as f:
        return {path: Entry(e["bytes"], e["sha256"]) for path, e in json.load(f).get("files", {}).items()}


def write_lock(entries: dict[str, Entry], lock: Path = LOCK) -> None:
    doc = {"about": ABOUT,
           "files": {path: {"bytes": e.bytes, "sha256": e.sha256} for path, e in sorted(entries.items())}}
    lock.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


@dataclass
class State:
    listed: dict[str, Entry]  # in originals.json
    here: dict[str, Entry]  # on this computer
    paths: dict[str, Path] = field(default_factory=dict)

    @property
    def new(self) -> list[str]:
        return sorted(set(self.here) - set(self.listed))

    @property
    def changed(self) -> list[str]:
        return sorted(p for p in set(self.here) & set(self.listed) if self.here[p] != self.listed[p])

    @property
    def missing(self) -> list[str]:
        return sorted(set(self.listed) - set(self.here))


def state(content: Path = CONTENT, lock: Path = LOCK) -> State:
    paths = files_here(content)
    return State(read_lock(lock), {rel: _digest(path) for rel, path in paths.items()}, paths)


def quick_check(content: Path = CONTENT, lock: Path = LOCK) -> tuple[list[str], list[str]]:
    """Without reading the files: (listed but not here, here but not listed or with another size). For `story check`;
    `story originals` compares the hashes."""
    listed = read_lock(lock)
    here = files_here(content)
    missing = sorted(set(listed) - set(here))
    unpushed = sorted(p for p, f in here.items() if p not in listed or listed[p].bytes != f.stat().st_size)
    return missing, unpushed


class Gcloud:
    """originals/ in the bucket, through the owner's gcloud login (storage.rules don't apply to it)."""

    base = f"gs://{BUCKET}/{PREFIX}/"

    def names(self) -> set[str]:
        """Every object under originals/, as its name under originals/."""
        done = subprocess.run(["gcloud", "storage", "ls", f"{self.base}**"], capture_output=True, text=True)
        if done.returncode != 0:
            if "matched no objects" in done.stderr:
                return set()
            raise SystemExit(f"Couldn't list {self.base}: {done.stderr.strip()}")
        lines = (line.strip() for line in done.stdout.splitlines())
        return {line[len(self.base):] for line in lines if line.startswith(self.base) and not line.endswith("/")}

    def upload(self, stage: Path) -> None:
        """Uploads a folder laid out like originals/, never replacing an object that's already there."""
        sources = [str(p) for p in sorted(stage.iterdir())]
        subprocess.run(["gcloud", "storage", "cp", "--recursive", "--no-clobber", *sources, self.base], check=True)

    def download(self, names: list[str], stage: Path) -> None:
        """Downloads objects into a folder laid out like originals/ (one call per folder: gcloud puts every source of
        a call in the same place)."""
        folders = defaultdict(list)
        for name in names:
            folders[PurePosixPath(name).parent.as_posix()].append(self.base + name)
        for folder, urls in sorted(folders.items()):
            target = stage / folder
            target.mkdir(parents=True, exist_ok=True)
            subprocess.run(["gcloud", "storage", "cp", *urls, f"{target}/"], check=True)


def _require_gcloud(bucket) -> None:
    if isinstance(bucket, Gcloud) and not shutil.which("gcloud"):
        raise SystemExit("`gcloud` isn't installed or isn't on the PATH (and needs `gcloud auth login`).")


def _mb(n: int) -> str:
    return f"{n / 1_000_000:.1f} MB"


def _by_folder(paths: list[str], verbose: bool) -> list[str]:
    if verbose:
        return [f"    {p}" for p in paths]
    counts = defaultdict(int)
    for p in paths:
        counts[PurePosixPath(p).parent.as_posix()] += 1
    return [f"    {folder}/: {n}" for folder, n in sorted(counts.items())]


def status(content: Path = CONTENT, lock: Path = LOCK, verbose: bool = False) -> int:
    s = state(content, lock)
    size = sum(e.bytes for e in s.listed.values())
    print(f"{lock.name} lists {len(s.listed)} originals ({_mb(size)}), kept in gs://{BUCKET}/{PREFIX}/. "
          f"Here: {len(s.here)}.")
    for label, paths in (("New here, not pushed yet (`story originals push`)", s.new),
                         ("Different here from the pushed original (`story originals push`)", s.changed),
                         ("Listed but not here (`story originals pull`)", s.missing)):
        if paths:
            print(f"  {label}: {len(paths)}")
            for line in _by_folder(paths, verbose):
                print(line)
    if not (s.new or s.changed or s.missing):
        print("  Everything here is pushed, and everything pushed is here.")
    return 0


def push(content: Path = CONTENT, lock: Path = LOCK, bucket=None, dry_run: bool = False, forget: bool = False) -> int:
    bucket = bucket or Gcloud()
    _require_gcloud(bucket)
    s = state(content, lock)
    if s.missing and not forget:
        print(f"{len(s.missing)} originals in {lock.name} aren't here:")
        for line in _by_folder(s.missing, verbose=False):
            print(line)
        print("Run `story originals pull` first. If you deleted them on purpose, `story originals push --forget` drops "
              "them from the list (they stay in the bucket).")
        return 1
    have = bucket.names()
    todo = {path: object_name(path, entry) for path, entry in s.here.items() if object_name(path, entry) not in have}
    size = sum(s.here[p].bytes for p in todo)
    if dry_run:
        print(f"Would upload {len(todo)} originals ({_mb(size)}) to {Gcloud.base}"
              + (f" and drop {len(s.missing)} from {lock.name}" if s.missing else "") + ".")
        for line in _by_folder(sorted(todo), verbose=False):
            print(line)
        return 0
    if todo:
        stage = content / ".build" / "originals-upload"
        shutil.rmtree(stage, ignore_errors=True)
        try:
            for path, name in todo.items():
                (stage / name).parent.mkdir(parents=True, exist_ok=True)
                try:
                    os.link(s.paths[path], stage / name)
                except OSError:
                    shutil.copy2(s.paths[path], stage / name)
            bucket.upload(stage)
        finally:
            shutil.rmtree(stage, ignore_errors=True)
    write_lock(s.here, lock)
    if not todo and not s.missing:
        print(f"Every original here ({len(s.here)}) is kept in {Gcloud.base} already.")
        return 0
    print(f"Uploaded {len(todo)} originals ({_mb(size)}) to {Gcloud.base}. {lock.name} lists {len(s.here)}."
          + (f" Dropped {len(s.missing)} that aren't here any more (still in the bucket)." if s.missing else ""))
    return 0


def pull(content: Path = CONTENT, lock: Path = LOCK, bucket=None, dry_run: bool = False) -> int:
    bucket = bucket or Gcloud()
    _require_gcloud(bucket)
    s = state(content, lock)
    if s.changed:
        print(f"Kept {len(s.changed)} files that are different here from their pushed original "
              "(push them, or delete them and pull again):")
        for path in s.changed:
            print(f"    {path}")
    if not s.missing:
        print(f"Nothing to download: every original in {lock.name} is here.")
        return 0
    wanted = {path: object_name(path, s.listed[path]) for path in s.missing}
    have = bucket.names()
    absent = sorted(path for path, name in wanted.items() if name not in have)
    fetch = {path: name for path, name in wanted.items() if path not in absent}
    size = sum(s.listed[p].bytes for p in fetch)
    if dry_run:
        print(f"Would download {len(fetch)} originals ({_mb(size)}).")
        for line in _by_folder(sorted(fetch), verbose=False):
            print(line)
    elif fetch:
        stage = content / ".build" / "originals-download"
        shutil.rmtree(stage, ignore_errors=True)
        try:
            bucket.download(sorted(fetch.values()), stage)
            for path, name in fetch.items():
                got = _digest(stage / name)
                if got != s.listed[path]:
                    raise SystemExit(f"{name} in the bucket isn't the original {lock.name} lists for {path}.")
                (content / path).parent.mkdir(parents=True, exist_ok=True)
                shutil.move(stage / name, content / path)
        finally:
            shutil.rmtree(stage, ignore_errors=True)
        print(f"Downloaded {len(fetch)} originals ({_mb(size)}).")
    if absent:
        print(f"Not in the bucket, so not downloaded ({len(absent)}): were they pushed from another computer?")
        for path in absent:
            print(f"    {path}")
        return 1
    return 0


def ensure_here(content: Path = CONTENT, lock: Path = LOCK, bucket=None) -> None:
    """Before packaging: downloads the listed originals that aren't here (no network when they all are). Stops when one
    can't be downloaded, because a story packaged without it would lose that picture or clip."""
    missing, _ = quick_check(content, lock)
    if not missing:
        return
    print(f"{len(missing)} originals aren't here: downloading them first (`story originals pull`).")
    if pull(content, lock, bucket) != 0:
        raise SystemExit("Some originals couldn't be downloaded, so nothing was packaged.")
