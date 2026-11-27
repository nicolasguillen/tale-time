"""`story publish <channel>`: uploads a channel (story package) to the content host, Firebase Storage in the project
tale-time-b537d, into stories/<channel>/. Run it only when the user asks.

- draft: every story as it is, for debug builds (stories/draft/).
- production: the finished stories, for release builds (stories/production/).

The app reads single files under stories/ by plain HTTPS (backend/storage.rules: anyone can get, nobody can list or
write through the API). This uploads with the owner's own gcloud login, which those rules don't apply to:

1. every story file not on the host yet (they're named after their contents, so an existing name is the same file),
   marked cacheable forever;
2. the manifests, the same way;
3. the lines at the end of every story (app/), the same way;
4. the catalog last, marked never cached, so the app never sees a manifest or a file before it's there.

Production never deletes anything: an app that's halfway through an update still finds the files of the version it
started with. Draft then deletes the files on the host that no story on it uses any more, so old drafts don't pile up
in the bucket; a debug build caught halfway through an update tries again at its next launch.
"""
import shutil
import subprocess

from .package import APP_FOLDER, CATALOG_FILE, Result, describe, host_folder, package

BUCKET = "tale-time-b537d.firebasestorage.app"
FOREVER = "public, max-age=31536000, immutable"
FRESH = "no-cache"
TYPES = {".json": "application/json", ".webp": "image/webp", ".m4a": "audio/mp4"}


def url(path: str) -> str:
    """The public download URL of a path in the bucket, as the app builds it (ContentSource in
    data/HostedStoryLibrary.kt)."""
    return f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o/{path.replace('/', '%2F')}?alt=media"


def _target(channel: str) -> str:
    return f"gs://{BUCKET}/{host_folder(channel)}/"


def _gcloud(*args: str, dry_run: bool) -> None:
    command = ["gcloud", "storage", *args]
    if dry_run:
        shown = [a if len(a) < 80 else "…" + a[-60:] for a in command]
        print("  would run:", " ".join(shown[:8]), f"… ({len(command) - 8} more)" if len(command) > 8 else "")
        return
    subprocess.run(command, check=True)


def _upload_forever(files: list, target: str, dry_run: bool) -> None:
    """Files named after their contents, by type, never replacing one already there."""
    for ext, kind in TYPES.items():
        batch = [str(p) for p in files if p.suffix == ext]
        if batch:
            _gcloud("cp", "--no-clobber", f"--cache-control={FOREVER}", f"--content-type={kind}", *batch, target,
                    dry_run=dry_run)


def upload(result: Result, dry_run: bool = False) -> None:
    for story in result.stories:
        folder = result.root / story.id
        target = f"{_target(result.channel)}{story.id}/"
        files = sorted(p for p in folder.iterdir() if p.is_file() and not p.name.startswith("manifest-"))
        manifests = sorted(folder.glob("manifest-*.json"))
        for group in (files, manifests):
            _upload_forever(group, target, dry_run)
    app = result.root / APP_FOLDER
    if app.is_dir():
        _upload_forever(sorted(p for p in app.iterdir() if p.is_file()), f"{_target(result.channel)}{APP_FOLDER}/",
                        dry_run)
    _gcloud("cp", f"--cache-control={FRESH}", "--content-type=application/json", str(result.catalog),
            f"{_target(result.channel)}{CATALOG_FILE}", dry_run=dry_run)


def stale(remote: list[str], result: Result) -> list[str]:
    """The objects on the host (gs:// URLs) in the channel's folder that this package doesn't have."""
    base = _target(result.channel)
    local = {p.relative_to(result.root).as_posix() for p in result.root.rglob("*") if p.is_file()}
    return [u for u in remote if u.startswith(base) and u[len(base):] and u[len(base):] not in local]


def _remote(channel: str) -> list[str]:
    """Every object in the channel's folder on the host."""
    done = subprocess.run(["gcloud", "storage", "ls", f"{_target(channel)}**"], capture_output=True, text=True)
    if done.returncode != 0:
        if "matched no objects" in done.stderr:
            return []
        raise SystemExit(f"Couldn't list {_target(channel)}: {done.stderr.strip()}")
    return [line.strip() for line in done.stdout.splitlines() if line.strip() and not line.strip().endswith("/")]


def prune(result: Result, dry_run: bool = False) -> int:
    """Deletes the draft files on the host that this package doesn't use. Never touches production."""
    if result.channel != "draft":
        raise ValueError("only the draft channel is ever pruned")
    if dry_run:
        print(f"  would delete the files in {_target(result.channel)} that this package doesn't use")
        return 0
    old = stale(_remote(result.channel), result)
    if old:
        _gcloud("rm", *old, dry_run=False)
    return len(old)


def publish(channel: str, dry_run: bool = False) -> int:
    if not dry_run and not shutil.which("gcloud"):
        raise SystemExit("`gcloud` isn't installed or isn't on the PATH (and needs `gcloud auth login`).")
    result = package(channel)
    for line in describe(result):
        print(line)
    if not result.stories:
        print("Nothing to publish: " + ("no story passes the production checks yet." if channel == "production"
                                        else "no story has its text yet."))
        return 1
    upload(result, dry_run=dry_run)
    if channel == "draft":
        deleted = prune(result, dry_run=dry_run)
        if deleted:
            print(f"Deleted {deleted} old draft {'file' if deleted == 1 else 'files'} from the host.")
    print(("Would publish" if dry_run else "Published") +
          f" the {channel} catalog at {url(f'{host_folder(channel)}/{CATALOG_FILE}')}")
    return 0
