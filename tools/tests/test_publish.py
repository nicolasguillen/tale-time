import pytest

from storytool import publish
from test_package import build, content, deliver_images  # noqa: F401 (content is a fixture)

DRAFT = f"gs://{publish.BUCKET}/stories/draft/"


@pytest.fixture
def gcloud(monkeypatch):
    """Records the gcloud commands instead of running them."""
    calls = []
    monkeypatch.setattr(publish, "_gcloud", lambda *args, dry_run: calls.append(args))
    return calls


@pytest.mark.parametrize("channel", ["draft", "production"])
def test_a_channel_goes_into_its_own_folder_with_the_catalog_last(content, tmp_path, gcloud, channel):
    deliver_images(content)
    result, _, _ = build(content, tmp_path, channel)
    publish.upload(result)
    folder = f"gs://{publish.BUCKET}/stories/{channel}/"
    assert all(args[-1].startswith(folder) for args in gcloud)
    *files, catalog = gcloud
    assert catalog[-1] == folder + "catalog.json" and "--cache-control=no-cache" in catalog
    assert all(args[-1] == folder + "tiny/" and "--no-clobber" in args for args in files)
    manifests = [i for i, args in enumerate(files) if any("manifest-" in a for a in args)]
    assert manifests == [len(files) - 1]  # the manifest after every file it names


def test_draft_deletes_from_the_host_only_what_the_package_doesnt_have(content, tmp_path):
    deliver_images(content)
    result, catalog, manifests = build(content, tmp_path, "draft")
    kept = [DRAFT + "catalog.json", DRAFT + "tiny/" + catalog["stories"][0]["manifest"]["file"],
            *(DRAFT + "tiny/" + name for name in manifests["tiny"]["files"])]
    old = [DRAFT + "tiny/p1-0123456789.webp", DRAFT + "gone/manifest-0123456789.json"]
    elsewhere = [f"gs://{publish.BUCKET}/stories/production/tiny/p1-0123456789.webp"]
    assert publish.stale(kept + old + elsewhere, result) == old


def test_production_is_never_pruned(content, tmp_path):
    deliver_images(content)
    result, _, _ = build(content, tmp_path, "production")
    with pytest.raises(ValueError):
        publish.prune(result)
