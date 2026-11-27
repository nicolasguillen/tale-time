import hashlib
import json
import shutil

import pytest
from jsonschema import Draft202012Validator

from storytool.check import audio_clips, image_ids
from storytool.package import Encoder, Packager
from storytool.repo import ROOT, load_json
from storytool.timings import words

TINY = ROOT / "schema" / "fixtures" / "tiny"
CATALOG_SCHEMA = Draft202012Validator(load_json(ROOT / "schema" / "published-catalog.schema.json"))
MANIFEST_SCHEMA = Draft202012Validator(load_json(ROOT / "schema" / "manifest.schema.json"))
EN = load_json(TINY / "story" / "en.json")


class FakeEncoder(Encoder):
    """Stands in for Pillow, ffmpeg and afconvert: the 'converted' file is the source with a tag, every word is said a
    tenth of a second after the last, and every call is counted."""

    def __init__(self):
        self.calls = 0
        self.measured = 0

    def image(self, source, box, dest):
        self.calls += 1
        dest.write_bytes(f"webp {box[0]}x{box[1]} ".encode() + source.read_bytes())

    def audio(self, source, dest):
        self.calls += 1
        dest.write_bytes(b"aac " + source.read_bytes())

    def timings(self, source, text):
        self.measured += 1
        return [[300 + 100 * i, 390 + 100 * i] for i in range(len(words(text)))]


@pytest.fixture
def content(tmp_path):
    """A content/ folder with the tiny fixture on the shelf: a draft Spanish translation, no pictures and no clips."""
    root = tmp_path / "content"
    story = root / "stories" / "tiny"
    shutil.copytree(TINY / "story", story / "story")
    (story / "translations").mkdir()
    (story / "translations" / "es.json").write_text(json.dumps({"status": "draft"}))
    (root / "catalog.json").write_text(json.dumps({"stories": [{"id": "tiny", "access": "free", "approved": ["en"]}]}))
    return root


def approve(content, langs):
    """Lists `langs` as the tiny story's languages approved for production."""
    (content / "catalog.json").write_text(json.dumps({"stories": [{"id": "tiny", "access": "free", "approved": langs}]}))


def deliver_images(content, ids=None):
    folder = content / "stories" / "tiny" / "images"
    folder.mkdir(exist_ok=True)
    for image_id in ids or set(image_ids(EN)):
        (folder / f"{image_id}.png").write_bytes(f"picture {image_id}".encode())


def deliver_clips(content, lang, clips=None):
    folder = content / "stories" / "tiny" / "audio" / lang
    folder.mkdir(parents=True, exist_ok=True)
    for clip in clips or audio_clips(EN):
        (folder / f"{clip}.wav").write_bytes(f"{lang} {clip}".encode())


def build(content, tmp_path, channel="draft", encoder=None):
    packager = Packager(channel, content=content, out=tmp_path / "out" / channel, cache=tmp_path / "cache",
                        encoder=encoder or FakeEncoder(), check=lambda folder, strict: [])
    result = packager.build()
    catalog = json.loads(result.catalog.read_text())
    manifests = {s["id"]: json.loads((result.root / s["id"] / s["manifest"]["file"]).read_text())
                 for s in catalog["stories"]}
    return result, catalog, manifests


def test_draft_has_every_language_and_whatever_is_delivered(content, tmp_path):
    deliver_images(content, ["cover", "p1"])
    deliver_clips(content, "es", ["title", "p1"])
    result, catalog, manifests = build(content, tmp_path)
    assert not list(CATALOG_SCHEMA.iter_errors(catalog))
    entry = catalog["stories"][0]
    assert list(entry["languages"]) == ["en", "es"]  # the first language first, drafts too
    assert entry["title"] == {"en": "Tiny", "es": "Chiquito"}
    assert entry["endings"] == 2
    assert entry["languages"]["es"]["narration"] and not entry["languages"]["en"]["narration"]
    manifest = manifests["tiny"]
    assert not list(MANIFEST_SCHEMA.iter_errors(manifest))
    assert set(manifest["images"]) == {"cover", "p1"}  # the others show the app's placeholder
    assert set(manifest["languages"]["es"]["audio"]) == {"title", "p1"}
    assert manifest["languages"]["en"]["audio"] == {}
    assert entry["cover"]["file"] == manifest["images"]["cover"]


def test_every_file_is_named_after_its_contents_and_listed_with_its_size_and_hash(content, tmp_path):
    deliver_images(content)
    deliver_clips(content, "en")
    result, catalog, manifests = build(content, tmp_path)
    folder = result.root / "tiny"
    manifest = manifests["tiny"]
    for name, meta in manifest["files"].items():
        data = (folder / name).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        assert (meta["bytes"], meta["sha256"]) == (len(data), digest)
        assert name.rsplit(".", 1)[0].endswith(digest[:10])
    on_disk = {p.name for p in folder.iterdir()}
    assert on_disk == set(manifest["files"]) | {catalog["stories"][0]["manifest"]["file"]}
    # A language's size counts the pictures, its text, its narration and its timings.
    images = sum(manifest["files"][n]["bytes"] for n in manifest["images"].values())
    en = manifest["languages"]["en"]
    expected = images + sum(manifest["files"][n]["bytes"] for n in [en["story"], *en["audio"].values(), en["timings"]])
    assert catalog["stories"][0]["languages"]["en"]["bytes"] == expected


def test_a_narrated_page_comes_with_when_its_words_are_said(content, tmp_path):
    deliver_clips(content, "es", ["title", "p1", "f1-q", "f1-a"])
    result, catalog, manifests = build(content, tmp_path)
    manifest = manifests["tiny"]
    assert not list(MANIFEST_SCHEMA.iter_errors(manifest))
    assert "timings" not in manifest["languages"]["en"]  # nothing narrated in English yet
    es = manifest["languages"]["es"]
    heard = json.loads((result.root / "tiny" / es["timings"]).read_text())
    # Only the pages' texts are shown word by word: not the title, the question or the labels.
    assert heard["format"] == 1 and list(heard["clips"]) == ["p1"]
    es_text = load_json(TINY / "story" / "es.json")["nodes"]["p1"]["text"]
    assert len(heard["clips"]["p1"]) == len(words(es_text))
    assert es["timings"].startswith("timings-es-") and es["timings"] in manifest["files"]
    names = [es["story"], *es["audio"].values(), es["timings"]]
    assert catalog["stories"][0]["languages"]["es"]["bytes"] == sum(manifest["files"][n]["bytes"] for n in names)


def test_timings_are_measured_again_only_when_the_clip_or_the_text_changes(content, tmp_path):
    deliver_clips(content, "en", ["p1", "p2"])
    encoder = FakeEncoder()
    build(content, tmp_path, encoder=encoder)
    assert encoder.measured == 2
    build(content, tmp_path, encoder=encoder)
    assert encoder.measured == 2
    (content / "stories" / "tiny" / "audio" / "en" / "p1.wav").write_bytes(b"a better take")
    build(content, tmp_path, encoder=encoder)
    assert encoder.measured == 3
    en = content / "stories" / "tiny" / "story" / "en.json"
    story = json.loads(en.read_text())
    story["nodes"]["p2"]["text"] = "Behind the blue door was a big garden."
    en.write_text(json.dumps(story))
    build(content, tmp_path, encoder=encoder)
    assert encoder.measured == 4


def test_pictures_are_sized_by_kind(content, tmp_path):
    deliver_images(content)
    result, _, manifests = build(content, tmp_path)
    folder = result.root / "tiny"
    images = manifests["tiny"]["images"]
    assert (folder / images["cover"]).read_bytes().startswith(b"webp 900x1200 ")
    assert (folder / images["p1"]).read_bytes().startswith(b"webp 1600x1200 ")
    assert (folder / images["f1-a"]).read_bytes().startswith(b"webp 768x768 ")


def test_a_changed_clip_changes_its_name_and_the_manifest_and_nothing_else(content, tmp_path):
    deliver_images(content)
    deliver_clips(content, "en")
    _, before, old = build(content, tmp_path)
    (content / "stories" / "tiny" / "audio" / "en" / "p1.wav").write_bytes(b"a better take")
    _, after, new = build(content, tmp_path)
    assert before["stories"][0]["manifest"]["file"] != after["stories"][0]["manifest"]["file"]
    changed = set(new["tiny"]["files"]) ^ set(old["tiny"]["files"])
    assert changed == {old["tiny"]["languages"]["en"]["audio"]["p1"], new["tiny"]["languages"]["en"]["audio"]["p1"]}


def test_converted_files_are_cached_by_their_source(content, tmp_path):
    deliver_images(content)
    deliver_clips(content, "es")
    encoder = FakeEncoder()
    build(content, tmp_path, encoder=encoder)
    first = encoder.calls
    assert first == len(set(image_ids(EN))) + len(audio_clips(EN))
    _, catalog, _ = build(content, tmp_path, encoder=encoder)
    assert encoder.calls == first
    again = FakeEncoder()
    _, same, _ = build(content, tmp_path, encoder=again)
    assert again.calls == 0 and same == catalog  # the same names from the cache


def test_a_rebuild_holds_the_new_version_only(content, tmp_path):
    deliver_images(content)
    result, before, _ = build(content, tmp_path)
    (content / "stories" / "tiny" / "images" / "p1.png").write_bytes(b"a new picture")
    _, after, manifests = build(content, tmp_path)
    assert not (result.root / "tiny" / before["stories"][0]["manifest"]["file"]).exists()
    on_disk = {p.name for p in (result.root / "tiny").iterdir()}
    assert on_disk == set(manifests["tiny"]["files"]) | {after["stories"][0]["manifest"]["file"]}


def test_production_has_the_languages_the_catalog_approves_only(content, tmp_path):
    deliver_images(content)
    approve(content, ["en", "es"])
    _, catalog, _ = build(content, tmp_path, "production")
    assert list(catalog["stories"][0]["languages"]) == ["en"], "a translation still in draft stays out"
    (content / "stories" / "tiny" / "translations" / "es.json").write_text(json.dumps({"status": "approved"}))
    _, catalog, _ = build(content, tmp_path, "production")
    assert list(catalog["stories"][0]["languages"]) == ["en", "es"]
    approve(content, ["en"])
    _, catalog, _ = build(content, tmp_path, "production")
    assert list(catalog["stories"][0]["languages"]) == ["en"], "an approved translation the catalog doesn't list"


def test_a_story_with_no_language_approved_isnt_published(content, tmp_path):
    deliver_images(content)
    (content / "catalog.json").write_text(json.dumps({"stories": [{"id": "tiny", "access": "free"}]}))
    result, catalog, _ = build(content, tmp_path, "production")
    assert catalog["stories"] == []
    assert "no language approved for production" in result.skipped["tiny"][0]
    _, catalog, _ = build(content, tmp_path, "draft")
    assert list(catalog["stories"][0]["languages"]) == ["en", "es"]


def test_production_can_leave_out_the_first_language(content, tmp_path):
    deliver_images(content)
    deliver_clips(content, "es")
    approve(content, ["es"])
    (content / "stories" / "tiny" / "translations" / "es.json").write_text(json.dumps({"status": "approved"}))
    _, catalog, manifests = build(content, tmp_path, "production")
    entry = catalog["stories"][0]
    assert list(entry["languages"]) == ["es"] and list(entry["title"]) == ["es"]
    assert list(manifests["tiny"]["languages"]) == ["es"]
    assert entry["languages"]["es"]["narration"] is True
    assert set(manifests["tiny"]["images"]) == set(image_ids(EN))
    assert not list(CATALOG_SCHEMA.iter_errors(catalog))
    assert not list(MANIFEST_SCHEMA.iter_errors(manifests["tiny"]))
    _, catalog, _ = build(content, tmp_path, "draft")
    assert list(catalog["stories"][0]["languages"]) == ["en", "es"]


def test_production_narrates_a_language_once_all_its_clips_are_delivered(content, tmp_path):
    deliver_images(content)
    deliver_clips(content, "en", audio_clips(EN)[:3])
    _, catalog, manifests = build(content, tmp_path, "production")
    assert manifests["tiny"]["languages"]["en"]["audio"] == {}
    assert catalog["stories"][0]["languages"]["en"]["narration"] is False
    deliver_clips(content, "en")
    _, catalog, manifests = build(content, tmp_path, "production")
    assert set(manifests["tiny"]["languages"]["en"]["audio"]) == set(audio_clips(EN))
    assert catalog["stories"][0]["languages"]["en"]["narration"] is True


def test_an_ending_title_clip_delivered_earlier_is_neither_needed_nor_shipped(content, tmp_path):
    deliver_images(content)
    deliver_clips(content, "en", [*audio_clips(EN), "e1-end"])
    _, catalog, manifests = build(content, tmp_path, "production")
    assert catalog["stories"][0]["languages"]["en"]["narration"] is True
    assert "e1-end" not in manifests["tiny"]["languages"]["en"]["audio"]
    deliver_clips(content, "es", ["title", "e2-end"])
    _, _, manifests = build(content, tmp_path, "draft")
    assert set(manifests["tiny"]["languages"]["es"]["audio"]) == {"title"}


def test_production_leaves_out_a_story_with_pictures_missing(content, tmp_path):
    deliver_images(content, ["cover", "p1"])
    result, catalog, _ = build(content, tmp_path, "production")
    assert catalog["stories"] == []
    assert result.skipped["tiny"][0] == f"{len(set(image_ids(EN))) - 2} of {len(set(image_ids(EN)))} pictures not delivered yet"
    _, catalog, _ = build(content, tmp_path, "draft")
    assert [s["id"] for s in catalog["stories"]] == ["tiny"]


def test_production_leaves_out_a_story_that_fails_the_check(content, tmp_path):
    deliver_images(content)

    class Broken:
        level, code = "error", "dangling"

        def __str__(self):
            return "error   dangling: p2 leads nowhere"

    packager = Packager("production", content=content, out=tmp_path / "out", cache=tmp_path / "cache",
                        encoder=FakeEncoder(), check=lambda folder, strict: [Broken()])
    result = packager.build()
    assert result.skipped == {"tiny": ["error   dangling: p2 leads nowhere"]}


def test_a_paid_story_keeps_its_product(content, tmp_path):
    (content / "catalog.json").write_text(json.dumps({"stories": [
        {"id": "tiny", "access": "paid", "product": "story.tiny"}]}))
    _, catalog, _ = build(content, tmp_path)
    assert catalog["stories"][0]["access"] == "paid" and catalog["stories"][0]["product"] == "story.tiny"
    assert not list(CATALOG_SCHEMA.iter_errors(catalog))


# The end of every story: the same lines for every story, once per language, in app/ ------------------------------

from storytool.package import APP_FOLDER, describe  # noqa: E402
from storytool.repo import APP_CLIPS  # noqa: E402


def narrate_app(content):
    """Puts the app's narration.json (English and Spanish) into the content folder, with no clips delivered."""
    (content / "app").mkdir(exist_ok=True)
    shutil.copy(ROOT / "content" / "app" / "narration.json", content / "app" / "narration.json")


def deliver_app_clips(content, lang, clips=APP_CLIPS):
    folder = content / "app" / "audio" / lang
    folder.mkdir(parents=True, exist_ok=True)
    for clip in clips:
        (folder / f"{clip}.wav").write_bytes(f"app {lang} {clip}".encode())


def test_draft_publishes_every_delivered_app_clip_next_to_the_catalog(content, tmp_path):
    narrate_app(content)
    deliver_app_clips(content, "es")
    deliver_app_clips(content, "en", ["the-end"])
    result, catalog, _ = build(content, tmp_path)
    assert not list(CATALOG_SCHEMA.iter_errors(catalog))
    assert list(catalog) == ["format", "narration", "stories"] and catalog["format"] == 1
    narration = catalog["narration"]
    assert list(narration) == ["en", "es"] and list(narration["en"]) == ["the-end"]
    assert list(narration["es"]) == list(APP_CLIPS)
    folder = result.root / APP_FOLDER
    for lang, clips in narration.items():
        for clip, meta in clips.items():
            data = (folder / meta["file"]).read_bytes()
            assert data == f"aac app {lang} {clip}".encode()  # converted like every clip
            assert meta == {"file": f"{lang}-{clip}-{hashlib.sha256(data).hexdigest()[:10]}.m4a",
                            "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    assert {p.name for p in folder.iterdir()} == {m["file"] for c in narration.values() for m in c.values()}
    assert "  app narration (the end of every story): en 1/3, es 3/3" in describe(result)


def test_production_publishes_a_languages_app_clips_once_all_three_are_there(content, tmp_path):
    narrate_app(content)
    deliver_images(content)
    deliver_app_clips(content, "es")
    deliver_app_clips(content, "en", ["the-end", "more-endings"])
    result, catalog, _ = build(content, tmp_path, "production")
    assert not list(CATALOG_SCHEMA.iter_errors(catalog))
    assert list(catalog["narration"]) == ["es"] and list(catalog["narration"]["es"]) == list(APP_CLIPS)
    assert {p.name.split("-")[0] for p in (result.root / APP_FOLDER).iterdir()} == {"es"}
    assert "app narration (the end of every story): en 0/3, es 3/3" in describe(result)[-1]


def test_no_narration_in_the_catalog_until_an_app_clip_is_delivered(content, tmp_path):
    result, catalog, _ = build(content, tmp_path)  # no narration.json at all
    assert "narration" not in catalog and not result.app
    narrate_app(content)
    result, catalog, _ = build(content, tmp_path)
    assert "narration" not in catalog and not (result.root / APP_FOLDER).exists()
    deliver_app_clips(content, "en", ["the-end"])
    _, catalog, _ = build(content, tmp_path, "production")
    assert "narration" not in catalog  # one of three isn't enough for production


def test_app_clips_are_converted_once(content, tmp_path):
    narrate_app(content)
    deliver_app_clips(content, "es")
    encoder = FakeEncoder()
    build(content, tmp_path, encoder=encoder)
    assert encoder.calls == len(APP_CLIPS)
    _, catalog, _ = build(content, tmp_path, encoder=encoder)
    assert encoder.calls == len(APP_CLIPS) and len(catalog["narration"]["es"]) == len(APP_CLIPS)
