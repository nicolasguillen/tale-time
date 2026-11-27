import shutil

import pytest

from storytool import originals
from storytool.repo import ROOT


class FakeBucket:
    """originals/ in a folder: upload copies a staged tree in without replacing anything, download copies out."""

    def __init__(self, root):
        self.root = root
        self.uploads = 0

    def names(self):
        return {p.relative_to(self.root).as_posix() for p in self.root.rglob("*") if p.is_file()}

    def upload(self, stage):
        self.uploads += 1
        for path in stage.rglob("*"):
            target = self.root / path.relative_to(stage)
            if path.is_file() and not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(path, target)

    def download(self, names, stage):
        for name in names:
            (stage / name).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(self.root / name, stage / name)


@pytest.fixture
def content(tmp_path):
    """A content/ folder with a picture, a clip, the app icon and an app clip delivered, next to files that aren't
    originals."""
    root = tmp_path / "content"
    for path, data in {
        "stories/tiny/images/p1.png": b"picture p1",
        "stories/tiny/characters/fox.jpg": b"sheet fox",
        "stories/tiny/audio/es/p1.wav": b"clip p1",
        "app/app-icon.png": b"icon",
        "app/audio/es/the-end.wav": b"clip the end",
        "app/narration.json": b"{}",
        "app/audio/es/REQUESTED_AUDIO.md": b"# requests",
        "stories/tiny/story/en.json": b"{}",
        "stories/tiny/audio/es/REQUESTED_AUDIO.md": b"# requests",
        "placeholders/page.svg": b"<svg/>",
        ".build/stories/draft/tiny/p1-0123456789.webp": b"converted",
        ".work/sketch.png": b"work in progress",
    }.items():
        (root / path).parent.mkdir(parents=True, exist_ok=True)
        (root / path).write_bytes(data)
    return root


@pytest.fixture
def bucket(tmp_path):
    (tmp_path / "bucket").mkdir()
    return FakeBucket(tmp_path / "bucket")


def lock(content):
    return content / "originals.json"


def test_only_delivered_pictures_and_clips_are_originals(content):
    assert sorted(originals.files_here(content)) == [
        "app/app-icon.png", "app/audio/es/the-end.wav", "stories/tiny/audio/es/p1.wav",
        "stories/tiny/characters/fox.jpg", "stories/tiny/images/p1.png"]


def test_an_original_is_kept_under_its_path_named_after_its_contents():
    entry = originals.Entry(10, "3fa2b1c4d5e6f708" + "0" * 48)
    assert originals.object_name("stories/fox/images/p01-meadow.png", entry) == \
        "stories/fox/images/p01-meadow-3fa2b1c4d5.png"


def test_push_uploads_what_the_bucket_doesnt_have_and_lists_it(content, bucket):
    assert originals.push(content, lock(content), bucket) == 0
    listed = originals.read_lock(lock(content))
    assert sorted(listed) == sorted(originals.files_here(content))
    assert bucket.names() == {originals.object_name(p, e) for p, e in listed.items()}
    assert not (content / ".build" / "originals-upload").exists()

    assert originals.push(content, lock(content), bucket) == 0
    assert bucket.uploads == 1  # nothing new the second time


def test_a_redelivered_file_is_a_new_original_and_the_old_one_stays(content, bucket):
    originals.push(content, lock(content), bucket)
    before = set(bucket.names())
    (content / "stories/tiny/images/p1.png").write_bytes(b"picture p1, redrawn")
    assert originals.state(content, lock(content)).changed == ["stories/tiny/images/p1.png"]
    originals.push(content, lock(content), bucket)
    assert before < bucket.names() and len(bucket.names()) == len(before) + 1
    assert originals.state(content, lock(content)).changed == []


def test_push_stops_when_a_listed_original_isnt_here_unless_told_to_forget_it(content, bucket):
    originals.push(content, lock(content), bucket)
    (content / "stories/tiny/characters/fox.jpg").unlink()
    assert originals.push(content, lock(content), bucket) == 1
    assert "stories/tiny/characters/fox.jpg" in originals.read_lock(lock(content))
    assert originals.push(content, lock(content), bucket, forget=True) == 0
    assert "stories/tiny/characters/fox.jpg" not in originals.read_lock(lock(content))
    assert any(name.startswith("stories/tiny/characters/fox-") for name in bucket.names())  # still kept


def test_pull_brings_back_what_is_missing_and_keeps_what_changed_here(content, bucket):
    originals.push(content, lock(content), bucket)
    (content / "stories/tiny/audio/es/p1.wav").unlink()
    shutil.rmtree(content / "app")
    (content / "stories/tiny/images/p1.png").write_bytes(b"not pushed yet")
    assert originals.pull(content, lock(content), bucket) == 0
    assert (content / "stories/tiny/audio/es/p1.wav").read_bytes() == b"clip p1"
    assert (content / "app/app-icon.png").read_bytes() == b"icon"
    assert (content / "app/audio/es/the-end.wav").read_bytes() == b"clip the end"
    assert (content / "stories/tiny/images/p1.png").read_bytes() == b"not pushed yet"


def test_pull_reports_what_isnt_in_the_bucket(content, bucket):
    originals.push(content, lock(content), bucket)
    for name in bucket.names():
        if name.startswith("app/"):
            (bucket.root / name).unlink()
    shutil.rmtree(content / "app")
    (content / "stories/tiny/audio/es/p1.wav").unlink()
    assert originals.pull(content, lock(content), bucket) == 1
    assert (content / "stories/tiny/audio/es/p1.wav").is_file()
    assert not (content / "app/app-icon.png").exists()


def test_the_quick_check_sees_missing_and_unpushed_files(content, bucket):
    assert originals.quick_check(content, lock(content)) == ([], sorted(originals.files_here(content)))
    originals.push(content, lock(content), bucket)
    assert originals.quick_check(content, lock(content)) == ([], [])
    (content / "app/app-icon.png").unlink()
    (content / "stories/tiny/images/p2.webp").write_bytes(b"picture p2")
    assert originals.quick_check(content, lock(content)) == (["app/app-icon.png"], ["stories/tiny/images/p2.webp"])


def test_git_ignores_every_kind_of_original():
    ignored = (ROOT / ".gitignore").read_text().splitlines()
    assert all(f"content/**/*{ext}" in ignored for ext in originals.EXTENSIONS)


def test_every_listed_original_is_here():
    missing, _ = originals.quick_check()
    assert not missing, f"{len(missing)} delivered originals aren't here: run `uv run story originals pull`"


class OfflineBucket(FakeBucket):
    def names(self):
        raise AssertionError("went online although every original is here")


def test_packaging_downloads_only_what_is_missing(content, bucket, tmp_path):
    originals.push(content, lock(content), bucket)
    originals.ensure_here(content, lock(content), OfflineBucket(bucket.root))  # all here: no network
    shutil.rmtree(content / "stories/tiny/images")
    originals.ensure_here(content, lock(content), bucket)
    assert (content / "stories/tiny/images/p1.png").read_bytes() == b"picture p1"


def test_packaging_stops_when_an_original_cant_be_downloaded(content, bucket):
    originals.push(content, lock(content), bucket)
    shutil.rmtree(bucket.root / "app")
    (content / "app/app-icon.png").unlink()
    with pytest.raises(SystemExit):
        originals.ensure_here(content, lock(content), bucket)
