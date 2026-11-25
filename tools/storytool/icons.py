"""`story icons`: puts the delivered app icon (content/app/) into the Android and iOS apps.

The icon is requested in REQUESTED_ASSETS.md and delivered into content/app/ like every other picture: `app-icon` for
iOS and the stores, and its two Android layers, `app-icon-foreground` and `app-icon-background`. This only converts
them; it never makes or changes a picture:

- Android: the adaptive launcher icon (androidApp/src/main/res/mipmap-anydpi/ic_launcher.xml) stacks two layers, each a
  108 dp square whose middle 66 dp circle is always shown. A delivered 1024 px layer is that whole square, so it's scaled
  down to 108 dp at every screen density and saved as WebP in mipmap-<density>/.
- iOS: the asset catalog's AppIcon set takes one opaque 1024 px PNG, and Xcode makes every other size from it. A
  delivered PNG of exactly that is copied as it is.

The files it writes are committed, so a fresh checkout builds with the icon. `story icons --check` and the tests fail
when they don't match what's delivered.
"""
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from .repo import APP, ROOT, find_image

ANDROID_RES = ROOT / "androidApp" / "src" / "main" / "res"
IOS_ICONSET = ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset"

LAYER_DP = 108
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
# Delivered image id -> the Android resource name of that layer (referenced by mipmap-anydpi/ic_launcher.xml).
ANDROID_LAYERS = {"app-icon-foreground": "ic_launcher_foreground", "app-icon-background": "ic_launcher_background"}
WEBP_QUALITY = 90

IOS_ICON = "app-icon"
IOS_FILE = "app-icon.png"  # named in AppIcon.appiconset/Contents.json
IOS_SIZE = 1024


@dataclass
class Result:
    changed: list[Path]  # written, or out of date when only checking
    missing: list[str]  # the icon images not delivered yet


def _layer(source: Path, px: int) -> bytes:
    """One Android layer at one density: scaled down to `px` square, WebP. Transparency is kept, and scaled with
    premultiplied alpha so the edges don't darken."""
    from PIL import Image

    with Image.open(source) as opened:
        picture = opened.convert("RGBa" if "A" in opened.getbands() else "RGB")
        picture = picture.resize((px, px), Image.Resampling.LANCZOS)
        if picture.mode == "RGBa":
            picture = picture.convert("RGBA")
        out = BytesIO()
        picture.save(out, "WEBP", quality=WEBP_QUALITY, method=6)
        return out.getvalue()


def _ios(source: Path) -> bytes:
    """The iOS icon: the delivered file itself when it's already an opaque 1024 px PNG, else converted to one. An icon
    with see-through pixels is refused: iOS shows them black and App Store Connect rejects it."""
    from PIL import Image

    with Image.open(source) as opened:
        if opened.width != opened.height or opened.width < IOS_SIZE:
            raise SystemExit(f"{source.relative_to(ROOT)} is {opened.width}×{opened.height} px: the app icon must be "
                             f"square and at least {IOS_SIZE} px")
        bands = opened.getbands()
        if "A" in bands and opened.getchannel("A").getextrema()[0] < 255:
            raise SystemExit(f"{source.relative_to(ROOT)} has transparent pixels: the iOS app icon must be opaque")
        if opened.format == "PNG" and opened.width == IOS_SIZE and "A" not in bands and opened.mode == "RGB":
            return source.read_bytes()
        picture = opened.convert("RGB").resize((IOS_SIZE, IOS_SIZE), Image.Resampling.LANCZOS)
        out = BytesIO()
        picture.save(out, "PNG")
        return out.getvalue()


def wanted(app: Path = APP, android: Path = ANDROID_RES, ios: Path = IOS_ICONSET) -> tuple[dict[Path, bytes], list[str]]:
    """Every icon file the apps should have, from what's delivered, and the icon images not delivered yet."""
    files: dict[Path, bytes] = {}
    missing: list[str] = []
    for image_id, name in ANDROID_LAYERS.items():
        source = find_image(app, image_id)
        if source is None:
            missing.append(image_id)
            continue
        for density, scale in DENSITIES.items():
            files[android / f"mipmap-{density}" / f"{name}.webp"] = _layer(source, round(LAYER_DP * scale))
    source = find_image(app, IOS_ICON)
    if source is None:
        missing.append(IOS_ICON)
    else:
        files[ios / IOS_FILE] = _ios(source)
    return files, missing


def sync(check_only: bool = False, **where) -> Result:
    """Writes the icon files that differ from what's delivered (or only lists them, with `check_only`)."""
    files, missing = wanted(**where)
    changed = [path for path, data in files.items() if not path.is_file() or path.read_bytes() != data]
    if not check_only:
        for path in changed:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(files[path])
    return Result(changed, missing)
