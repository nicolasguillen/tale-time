import json
import shutil

from storytool import audio, repo
from storytool.audio_text import TEXT
from storytool.check import audio_clips, check_story
from storytool.repo import REQUESTED_AUDIO, ROOT, load_json, load_story

FOX = ROOT / "content" / "stories" / "fox-and-the-lantern"
TINY = load_story(ROOT / "schema" / "fixtures" / "tiny")
VOICE = load_json(ROOT / "content" / "style" / "voice.json")


def test_clips_match_the_clip_ids_the_check_expects():
    story = load_story(FOX)
    assert [c.id for c in audio.clips(story["en"])] == audio_clips(story["en"])
    assert [c.id for c in audio.clips(story["es"])] == audio_clips(story["en"])  # the same ids in every language
    assert [c.id for c in audio.clips(TINY["en"])] == audio_clips(TINY["en"])


def test_clip_scripts_are_the_exact_texts():
    by_id = {c.id: c for c in audio.clips(TINY["en"])}
    assert by_id["title"].script == "Tiny"
    assert by_id["f1"].script == "Pip saw two doors."
    assert by_id["f1-q"].script == "The red door or the blue door?"
    assert (by_id["f1-a"].script, by_id["f1-b"].script) == ("Red door", "Blue door")
    assert "e2-end" not in by_id  # an ending's title isn't read: the ending screen closes with the shared lines
    assert {c.kind for c in by_id.values()} == set(audio.KINDS)
    assert {c.id: c.script for c in audio.clips(TINY["es"])}["f1-q"] == "¿La puerta roja o la puerta azul?"


def test_every_clip_and_every_text_is_requested():
    text = audio.render_story(audio.Story(FOX), "en")
    for clip in audio.clips(load_json(FOX / "story" / "en.json")):
        assert f"`{clip.id}`" in text
        assert clip.script.splitlines()[0] in text
    assert "MEE-ah" in text  # pronunciations from the bible
    assert "text-to-speech" in text  # says plainly that nothing is generated


def fox_with_spanish(tmp_path, status):
    """A copy of the fox story (no pictures, no clips) whose Spanish translation is `status`."""
    folder = tmp_path / "fox"
    shutil.copytree(FOX, folder, ignore=shutil.ignore_patterns("images", "audio"))
    sheet = folder / "translations" / "es.json"
    sheet.write_text(json.dumps({**load_json(sheet), "status": status}, ensure_ascii=False))
    return folder


def test_each_language_is_requested_in_that_language(tmp_path):
    text = audio.render_story(audio.Story(fox_with_spanish(tmp_path, "draft")), "es")
    for clip in audio.clips(load_json(FOX / "story" / "es.json")):
        assert clip.script.splitlines()[0] in text
    assert "# Narración solicitada: El zorro y el farolito (español latinoamericano)" in text
    assert "Voz cálida de cuentacuentos" in text  # the Spanish narrator from voice.json
    assert "Español latinoamericano neutro" in text  # its accent
    assert "¡Lucecita, lucecita, alumbra mi caminito!" in text  # the refrain from translations/es.json
    assert "Chispita" in text and "Glim" not in text  # names from the sheet, not the bible
    assert "Ánimo: sorpresa tranquila." in text  # moods from the sheet
    assert "texto a voz" in text  # says plainly that nothing is generated, in Spanish
    assert "**El texto en español latinoamericano es un borrador:**" in text  # draft until a native read-through
    for word in ("Direction", "Script", "Mood", "Who's in it", "The page text"):
        assert word not in text  # nothing left in English
    approved = audio.render_story(audio.Story(fox_with_spanish(tmp_path / "approved", "approved")), "es")
    assert "es un borrador" not in approved


def test_every_language_has_the_same_wording():
    for lang, words in TEXT.items():
        assert set(words) == set(TEXT["en"]), lang
        assert lang in VOICE["narrators"], lang


def test_spanish_numbers_group_from_five_digits():
    es, en = audio.Words("es"), audio.Words("en")
    assert (es.number(1477), es.number(12_345), en.number(1477)) == ("1477", "12 345", "1,477")


def test_a_delivered_clip_turns_green(tmp_path):
    folder = fox_with_spanish(tmp_path, "draft")
    (folder / "audio" / "en").mkdir(parents=True)
    (folder / "audio" / "en" / "title.wav").write_bytes(b"")
    story = audio.Story(folder)
    text = audio.render_story(story, "en")
    assert "### ✅ `title`" in text
    assert "### ⬜ `p01-meadow`" in text
    assert "| 1/1 | 0/20 |" in text
    assert "### ⬜ `title`" in audio.render_story(story, "es")  # a clip counts in its own language only
    shared = audio.render(stories=[story])
    assert "| en | first | 1/1 | 0/20 |" in shared  # the index too
    assert "| es | draft translation | 0/1 | 0/20 |" in shared


def test_delivered_wav_counts_for_the_check(tmp_path):
    clips = audio_clips(TINY["en"])
    for lang in TINY:
        (tmp_path / "audio" / lang).mkdir(parents=True)
        for clip in clips:
            (tmp_path / "audio" / lang / f"{clip}.wav").write_bytes(b"")
    issues = check_story(TINY, folder=tmp_path, strict=True)
    assert not [i for i in issues if i.code == "audio-missing"]


def test_an_ending_title_clip_delivered_earlier_is_left_alone(tmp_path):
    folder = tmp_path / "audio" / "es"
    folder.mkdir(parents=True)
    for clip in [*audio_clips(TINY["en"]), "e1-end", "e2-end"]:
        (folder / f"{clip}.wav").write_bytes(b"")
    issues = check_story({"es": TINY["es"]}, folder=tmp_path, strict=True)
    assert not [str(i) for i in issues if "-end" in i.message or i.code.startswith("audio")]


def test_each_story_has_one_file_per_language_inside_its_audio_folder():
    files = audio.outputs()
    assert REQUESTED_AUDIO in files
    assert FOX / "audio" / "en" / "REQUESTED_AUDIO.md" in files
    assert FOX / "audio" / "es" / "REQUESTED_AUDIO.md" in files
    assert files.get(FOX / "REQUESTED_AUDIO.md") is None  # the story folder itself has none any more
    shared = audio.render()
    assert "[The Fox and the Lantern](content/stories/fox-and-the-lantern/audio/en/REQUESTED_AUDIO.md)" in shared
    assert "[El zorro y el farolito](content/stories/fox-and-the-lantern/audio/es/REQUESTED_AUDIO.md)" in shared
    assert "`p01-meadow`" not in shared  # the clips live in the story's own files
    assert "(../../../../../REQUESTED_AUDIO.md)" in audio.render_story(audio.Story(FOX), "en")
    assert "(../../REQUESTED_ASSETS.md)" in audio.render_story(audio.Story(FOX), "en")


def test_a_language_without_its_translation_sheet_waits(tmp_path):
    folder = tmp_path / "fox"
    shutil.copytree(FOX, folder, ignore=shutil.ignore_patterns("images", "audio", "translations"))
    story = audio.Story(folder)
    assert story.requested(VOICE) == ["en"]
    assert "*The Fox and the Lantern* in `es`" in audio.render(stories=[story])


def test_old_and_unused_requests_files_are_removed(tmp_path, monkeypatch):
    stories = tmp_path / "content" / "stories"
    folder = stories / "fox-and-the-lantern"
    shutil.copytree(FOX, folder, ignore=shutil.ignore_patterns("images", "audio"))
    old = folder / "REQUESTED_AUDIO.md"
    old.write_text(audio.GENERATED + " old -->\n")
    (folder / "audio" / "de").mkdir(parents=True)
    unused = folder / "audio" / "de" / "REQUESTED_AUDIO.md"
    unused.write_text(audio.GENERATED + " old -->\n")
    mine = folder / "audio" / "fr" / "REQUESTED_AUDIO.md"
    mine.parent.mkdir()
    mine.write_text("Notes I wrote myself.\n")
    monkeypatch.setattr(audio, "STORIES", stories)
    monkeypatch.setattr(repo, "STORIES", stories)
    monkeypatch.setattr(audio, "REQUESTED_AUDIO", tmp_path / "REQUESTED_AUDIO.md")
    files = audio.outputs()
    assert files[old] is None and files[unused] is None  # generated, so removed
    assert mine not in files  # never touches a file it didn't write
    assert files[folder / "audio" / "es" / "REQUESTED_AUDIO.md"].startswith(audio.STORY_HEADER)


def test_requested_audio_is_up_to_date():
    assert audio.write(check_only=True), "REQUESTED_AUDIO.md is stale: run `uv run story assets`"


# The end of every story: the same lines for every story, requested once per language ----------------------------

APP = audio.AppNarration(ROOT / "content" / "app" / "narration.json")


def app_copy(tmp_path):
    """The app's narration.json in a folder of its own, with no clips delivered."""
    folder = tmp_path / "app"
    folder.mkdir()
    shutil.copy(ROOT / "content" / "app" / "narration.json", folder / "narration.json")
    return audio.AppNarration(folder / "narration.json")


def test_the_end_of_every_story_is_requested_once_per_language_in_that_language():
    assert APP.requested(VOICE) == ["en", "es"]
    en, es = audio.render_app(APP, "en", VOICE), audio.render_app(APP, "es", VOICE)
    for lang, text in (("en", en), ("es", es)):
        for clip in repo.APP_CLIPS:
            line = APP.languages[lang][clip]
            assert f"`{clip}`" in text and audio._quote(line["script"]) in text and line["direction"] in text
            assert f"`content/app/audio/{lang}/{clip}.wav`" in text
    assert "# Requested audio: the end of every story (English)" in en and "Warm storyteller" in en
    assert "# Narración solicitada: el final de cada cuento (español latinoamericano)" in es
    assert "Voz cálida de cuentacuentos" in es and "texto a voz" in es  # its narrator; nothing is generated
    for word in ("Direction", "Script", "When", "The End", "Every ending found"):
        assert word not in es  # nothing left in English
    assert "(../../../../REQUESTED_AUDIO.md)" in en  # back to the index


def test_a_delivered_app_clip_turns_green_in_its_language_only(tmp_path):
    app = app_copy(tmp_path)
    app.audio_dir("es").mkdir(parents=True)
    (app.audio_dir("es") / "the-end.wav").write_bytes(b"")
    es = audio.render_app(app, "es", VOICE)
    assert "### ✅ `the-end`" in es and "### ⬜ `more-endings`" in es
    assert "| 1/1 | 0/1 | 0/1 | **1/3** |" in es
    assert "### ⬜ `the-end`" in audio.render_app(app, "en", VOICE)
    index = audio.render(VOICE, stories=[], app=app)
    assert "## The end of every story" in index
    assert "| 1/1 | 0/1 | 0/1 | **1/3** |" in index and "| 0/1 | 0/1 | 0/1 | **0/3** |" in index


def test_the_index_links_each_language_of_the_end_of_every_story():
    index = audio.render()
    assert "[English (`en`)](content/app/audio/en/REQUESTED_AUDIO.md)" in index
    assert "[Latin American Spanish (`es`)](content/app/audio/es/REQUESTED_AUDIO.md)" in index
    assert "Total |" in index and "`more-endings`" in index


def test_app_lines_without_a_narrator_wait(tmp_path):
    app = app_copy(tmp_path)
    app.languages["fr"] = app.languages["en"]
    assert app.requested(VOICE) == ["en", "es"]
    assert "Not requested yet, waiting for a narrator or all three lines (`uv run story check`): `fr`." in \
        audio.render(VOICE, stories=[], app=app)


def test_the_app_requests_files_are_written_and_old_ones_removed(tmp_path, monkeypatch):
    app = app_copy(tmp_path)
    old = app.audio_dir("de") / "REQUESTED_AUDIO.md"
    old.parent.mkdir(parents=True)
    old.write_text(audio.GENERATED + " old -->\n")
    mine = app.audio_dir("fr") / "REQUESTED_AUDIO.md"
    mine.parent.mkdir(parents=True)
    mine.write_text("Notes I wrote myself.\n")
    monkeypatch.setattr(audio, "APP_NARRATION", app.folder / "narration.json")
    files = audio.outputs()
    assert files[app.audio_dir("en") / "REQUESTED_AUDIO.md"].startswith(audio.APP_HEADER)
    assert files[app.audio_dir("es") / "REQUESTED_AUDIO.md"].startswith(audio.APP_HEADER)
    assert files[old] is None  # generated, for a language that has no lines any more
    assert mine not in files  # never touches a file it didn't write
