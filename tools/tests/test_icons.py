from io import BytesIO

import pytest
from PIL import Image

from storytool import icons
from storytool.repo import APP, find_image

DELIVERED = all(find_image(APP, i) for i in [*icons.ANDROID_LAYERS, icons.IOS_ICON])


def test_app_icons_are_up_to_date():
    result = icons.sync(check_only=True)
    assert not result.changed, "the apps' icons don't match content/app/: run `uv run story icons`"


@pytest.mark.skipif(not DELIVERED, reason="the app icon isn't delivered yet")
def test_android_layers_are_108_dp_at_every_density_and_ios_gets_the_opaque_1024_icon(tmp_path):
    files, missing = icons.wanted(android=tmp_path / "res", ios=tmp_path / "AppIcon.appiconset")
    assert missing == []
    for image_id, name in icons.ANDROID_LAYERS.items():
        for density, scale in icons.DENSITIES.items():
            with Image.open(BytesIO(files[tmp_path / "res" / f"mipmap-{density}" / f"{name}.webp"])) as layer:
                assert layer.format == "WEBP"
                assert layer.size == (round(108 * scale),) * 2
                with Image.open(find_image(APP, image_id)) as source:
                    assert ("A" in layer.getbands()) == ("A" in source.getbands())  # the foreground stays see-through
    with Image.open(BytesIO(files[tmp_path / "AppIcon.appiconset" / icons.IOS_FILE])) as ios:
        assert (ios.format, ios.size, ios.mode) == ("PNG", (1024, 1024), "RGB")


def test_a_missing_icon_is_reported_and_nothing_is_written_for_it(tmp_path):
    result = icons.sync(app=tmp_path / "app", android=tmp_path / "res", ios=tmp_path / "ios")
    assert result.missing == [*icons.ANDROID_LAYERS, icons.IOS_ICON]
    assert result.changed == [] and not (tmp_path / "res").exists()
