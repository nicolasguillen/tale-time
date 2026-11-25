"""`story publish`: uploads the release channel (story package) to the content host, Firebase Storage in the project
tale-time-b537d. Run it only when the user asks to publish.

The app reads single files under stories/ by plain HTTPS (backend/storage.rules: anyone can get, nobody can list or
write through the API). This uploads with the owner's own gcloud login, which those rules don't apply to:

1. every story file not on the host yet (they're named after their contents, so an existing name is the same file),
   marked cacheable forever;
2. the manifests, the same way;
3. the catalog last, marked never cached, so the app never sees a manifest or a file before it's there.

Nothing is deleted: an app that's halfway through an update still finds the files of the version it started with.
"""
import shutil
import subprocess

from .package import CATALOG_PATH, Result, describe, package

BUCKET = "tale-time-b537d.firebasestorage.app"
FOREVER = "public, max-age=31536000, immutable"
FRESH = "no-cache"
TYPES = {".json": "application/json", ".webp": "image/webp", ".m4a": "audio/mp4"}


def url(path: str) -> str:
    """The public download URL of a path in the bucket, as the app builds it (data/ContentSource.kt)."""
    return f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o/{path.replace('/', '%2F')}?alt=media"


def _gcloud(*args: str, dry_run: bool) -> None:
    command = ["gcloud", "storage", *args]
    if dry_run:
        shown = [a if len(a) < 80 else "…" + a[-60:] for a in command]
        print("  would run:", " ".join(shown[:8]), f"… ({len(command) - 8} more)" if len(command) > 8 else "")
        return
    subprocess.run(command, check=True)


def upload(result: Result, dry_run: bool = False) -> None:
    for story in result.stories:
        folder = result.root / "stories" / story.id
        target = f"gs://{BUCKET}/stories/{story.id}/"
        files = sorted(p for p in folder.iterdir() if p.is_file() and not p.name.startswith("manifest-"))
        manifests = sorted(folder.glob("manifest-*.json"))
        for group in (files, manifests):
            for ext, kind in TYPES.items():
                batch = [str(p) for p in group if p.suffix == ext]
                if batch:
                    _gcloud("cp", "--no-clobber", f"--cache-control={FOREVER}", f"--content-type={kind}", *batch,
                            target, dry_run=dry_run)
    _gcloud("cp", f"--cache-control={FRESH}", "--content-type=application/json", str(result.catalog),
            f"gs://{BUCKET}/{CATALOG_PATH}", dry_run=dry_run)


def publish(dry_run: bool = False) -> int:
    if not dry_run and not shutil.which("gcloud"):
        raise SystemExit("`gcloud` isn't installed or isn't on the PATH (and needs `gcloud auth login`).")
    result = package("release")
    for line in describe(result):
        print(line)
    if not result.stories:
        print("Nothing to publish: no story passes the release checks yet.")
        return 1
    upload(result, dry_run=dry_run)
    print(("Would publish" if dry_run else "Published") + f" the catalog at {url(CATALOG_PATH)}")
    return 0
