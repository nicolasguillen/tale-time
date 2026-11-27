"""Where things live in the repository."""
import json
import os
from pathlib import Path

ROOT = Path(os.environ.get("STORY_ROOT") or Path(__file__).resolve().parents[2])
SCHEMA = ROOT / "schema" / "story.schema.json"
CONTENT = ROOT / "content"
STORIES = CONTENT / "stories"
VOICE = CONTENT / "style" / "voice.json"
PLACEHOLDERS = CONTENT / "placeholders"
CATALOG = CONTENT / "catalog.json"
APP = CONTENT / "app"
APP_ASSETS = APP / "assets.json"
APP_STYLE = APP / "style.json"  # the app's own look (the icon), not any story's
# The lines the narrator says at the end of every story, recorded once per language: scripts and directions here, the
# delivered clips in content/app/audio/<lang>/ (audio_dir(APP, lang)). The app plays them by these ids, in this order.
APP_NARRATION = APP / "narration.json"
APP_CLIPS = ("the-end", "more-endings", "all-endings")
REQUESTED_ASSETS = ROOT / "REQUESTED_ASSETS.md"
REQUESTED_AUDIO = ROOT / "REQUESTED_AUDIO.md"  # the index; each story's are in audio/<lang>/, one per language

# Delivered images may come in any of these formats; packaging converts them to WebP.
IMAGE_EXTENSIONS = (".webp", ".png", ".jpg", ".jpeg")
# Delivered narration may come in any of these formats; packaging converts it to AAC (.m4a).
AUDIO_EXTENSIONS = (".wav", ".flac", ".m4a")


def load_json(path: Path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def story_dir(story_id: str) -> Path:
    path = STORIES / story_id
    if not path.is_dir():
        known = ", ".join(sorted(p.name for p in STORIES.iterdir() if p.is_dir())) if STORIES.is_dir() else ""
        raise SystemExit(f"No story '{story_id}' in {STORIES}. Known: {known or 'none'}")
    return path


def style_home(folder: Path, bible: dict) -> Path:
    """The story folder whose art style this story uses: its own, unless its bible declares `styleFrom`."""
    return folder.parent / bible["styleFrom"] if bible.get("styleFrom") else folder


def style_dir(folder: Path) -> Path:
    """A story's art style: style.json and its delivered style anchor."""
    return folder / "style"


def characters_dir(folder: Path) -> Path:
    """A story's delivered reference sheets, one per character or prop."""
    return folder / "characters"


def audio_dir(folder: Path, lang: str) -> Path:
    """A story's narration in one language (or the app's, with content/app/): the delivered clips and that language's
    REQUESTED_AUDIO.md."""
    return folder / "audio" / lang


def translation_file(folder: Path, lang: str) -> Path:
    """The translation sheet for one of the story's languages after its first (schema/translation.schema.json)."""
    return folder / "translations" / f"{lang}.json"


def story_file(folder: Path, lang: str) -> Path:
    """The story in one language (schema/story.schema.json): its text, graph and images."""
    return folder / "story" / f"{lang}.json"


def first_language(story: dict[str, dict]) -> str:
    """The language a story was written in: the one file without `translatedFrom`. `story check` makes sure there's
    exactly one; until then, the first of them (or of every language) alphabetically."""
    originals = sorted(lang for lang, doc in story.items() if not doc.get("translatedFrom"))
    return (originals or sorted(story))[0]


def load_story(folder: Path) -> dict[str, dict] | None:
    """Every language of a story, language -> its story/<lang>.json, the first language first and then the others
    alphabetically. None while the story has no text yet (before step 3)."""
    files = sorted((folder / "story").glob("*.json"))
    if not files:
        return None
    story = {path.stem: load_json(path) for path in files}
    first = first_language(story)
    return {first: story[first], **{lang: doc for lang, doc in story.items() if lang != first}}


def has_story(folder: Path) -> bool:
    return any((folder / "story").glob("*.json"))


def story_ids() -> list[str]:
    """Every story folder that has started the pipeline (has a bible or a story)."""
    return sorted(p.name for p in STORIES.iterdir() if (p / "bible.json").is_file() or has_story(p))


def find_image(folder: Path, image_id: str) -> Path | None:
    """The delivered file for an image id, or None while it's still a placeholder."""
    for ext in IMAGE_EXTENSIONS:
        path = folder / f"{image_id}{ext}"
        if path.is_file():
            return path
    return None


def find_audio(folder: Path, clip_id: str) -> Path | None:
    """The delivered file for a narration clip in a language folder (audio/<lang>/), or None while it's missing."""
    for ext in AUDIO_EXTENSIONS:
        path = folder / f"{clip_id}{ext}"
        if path.is_file():
            return path
    return None
