import copy

import pytest

from storytool import graph
from storytool.budgets import BANDS, Budget
from storytool.check import (ERROR, WARNING, audio_clips, check_bible, check_consistency, check_outline,
                              check_shots, check_story, image_ids, word_count)
from storytool.repo import ROOT, has_story, load_json, load_story

TINY = load_story(ROOT / "schema" / "fixtures" / "tiny")  # story/en.json and story/es.json
# Lets the tiny fixture pass the budget checks, so each test sees only the rule it breaks.
LENIENT = Budget((1, 100), (1, 100), (0, 100), (1, 100), (1, 100), 100)


def tiny(lang="en"):
    """The tiny fixture in one language: its story/<lang>.json."""
    return copy.deepcopy(TINY[lang])


def issues(*docs, **kw):
    """Every issue with a story made of these language files, each under the language it says it's in."""
    return check_story({d["language"]: d for d in docs}, budget=kw.pop("budget", LENIENT), **kw)


def codes(*docs, **kw):
    return {i.code for i in issues(*docs, **kw) if i.level == ERROR}


def test_tiny_fixture_is_valid():
    assert list(TINY) == ["en", "es"]  # the first language first
    assert codes(tiny()) == set()
    assert codes(tiny(), tiny("es")) == set()


def test_unknown_field_is_a_schema_error():
    s = tiny()
    s["nodes"]["p1"]["music"] = "lullaby"
    assert codes(s) == {"schema"}


def test_three_option_fork_is_a_schema_error():
    s = tiny()
    s["nodes"]["f1"]["options"].append({"label": "Green door", "image": "f1-c", "target": "e2"})
    assert codes(s) == {"schema"}


def test_page_with_options_and_next_is_a_schema_error():
    s = tiny()
    s["nodes"]["f1"]["next"] = "p2"
    assert codes(s) == {"schema"}


def test_dangling_target():
    s = tiny()
    s["nodes"]["p2"]["next"] = "nowhere"
    assert "dangling" in codes(s)


def test_missing_start():
    s = tiny()
    s["start"] = "nope"
    assert codes(s) == {"start"}


def test_unreachable_node():
    s = tiny()
    s["nodes"]["lost"] = {"text": "Nobody comes here.", "image": "lost", "next": "e1"}
    assert [i.node for i in issues(s) if i.code == "unreachable"] == ["lost"]


def test_cycle():
    s = tiny()
    s["nodes"]["p2"]["next"] = "p1"
    found = [i for i in issues(s) if i.code == "cycle"]
    assert found and "p1" in found[0].message


def test_fork_with_the_same_target_twice():
    s = tiny()
    s["nodes"]["f1"]["options"][1]["target"] = "e1"
    assert "same-targets" in codes(s)


def test_a_translation_is_the_same_story_in_other_words():
    es = tiny("es")
    es["nodes"]["p2"]["next"] = "e1"  # another link
    es["nodes"]["f1"]["options"][0]["image"] = "f1-x"  # another card
    del es["nodes"]["e2"]  # a page less
    es["cover"] = "portada"
    found = [(i.node, i.code) for i in issues(tiny(), es)]
    assert sorted(found, key=str) == sorted([(None, "translation-structure"), ("e2", "translation-structure"),
                                             ("f1", "translation-structure"), ("p2", "translation-structure")], key=str)
    es = tiny("es")
    es["nodes"] = dict(reversed(es["nodes"].items()))  # the same pages in another order
    assert codes(tiny(), es) == {"translation-structure"}


def test_each_file_says_its_language_and_exactly_one_is_the_first():
    es = tiny("es")
    del es["translatedFrom"]
    assert codes(tiny(), es) == {"story-first"}
    es["translatedFrom"] = "de"
    assert codes(tiny(), es) == {"translated-from"}
    assert {i.code for i in check_story({"en": tiny(), "de": tiny("es")}, budget=LENIENT)} == {"story-language"}


def test_the_language_a_story_was_written_in_comes_first(tmp_path):
    import json
    (tmp_path / "story").mkdir()
    es, en = tiny("es"), tiny()
    del es["translatedFrom"]
    en["translatedFrom"] = "es"  # written in Spanish, translated into English
    for doc in (es, en):
        (tmp_path / "story" / f"{doc['language']}.json").write_text(json.dumps(doc))
    assert list(load_story(tmp_path)) == ["es", "en"]
    assert codes(es, en) == set()
    assert load_story(tmp_path / "nothing-yet") is None


def test_every_text_in_every_language_has_words():
    es = tiny("es")
    es["nodes"]["f1"]["question"] = "   "
    found = [(i.node, i.code, i.message) for i in issues(tiny(), es)]
    assert found == [("f1", "text-empty", "question is empty in story/es.json")]


def test_budgets_of_the_age_band():
    found = codes(tiny(), budget=BANDS["4-7"])
    assert {"pages-total", "endings", "words", "pages-per-playthrough-low", "forks-per-playthrough-low"} <= found


def test_path_too_long():
    tight = Budget((1, 100), (1, 3), (0, 100), (1, 100), (1, 100), 100)
    assert "pages-per-playthrough-high" in codes(tiny(), budget=tight)


def test_the_words_fit_the_budget_in_every_language():
    es = tiny("es")
    es["nodes"]["p1"]["text"] = "Pip se despertó muy, muy, muy, muy, muy temprano."
    words = Budget((1, 8), (1, 100), (0, 100), (1, 100), (1, 100), 100)  # every other page fits
    found = [(i.node, i.message) for i in issues(tiny(), es, budget=words) if i.code == "words"]
    assert found == [("p1", "9 words in story/es.json, the band allows 1–8")]


def test_stats_and_paths():
    s = tiny()
    st = graph.stats(s)
    assert (st.min_pages, st.max_pages, st.min_forks, st.max_forks, st.paths) == (3, 4, 1, 1, 2)
    assert [[n for n, _ in p] for p in graph.all_paths(s)] == [["p1", "f1", "e1"], ["p1", "f1", "p2", "e2"]]


def test_covering_paths_cover_every_edge():
    s = tiny()
    covered = set()
    for path in graph.covering_paths(s):
        covered |= {(a, b) for (a, _), (b, _) in zip(path, path[1:])}
    assert covered == set(graph.edges(s))


def test_word_count():
    assert word_count("\"Hooonk,\" she said, which means \"Hurry along, dears.\"") == 8
    assert word_count("ribbit-ribbit, it's Grandma's") == 3


def test_ids_and_clips():
    s = tiny()
    assert image_ids(s) == ["cover", "p1", "f1", "f1-a", "f1-b", "p2", "e1", "e2"]
    assert audio_clips(s) == ["title", "p1", "f1", "f1-q", "f1-a", "f1-b", "p2", "e1", "e2"]  # no ending titles


def test_missing_pictures_are_requests_in_draft_and_errors_when_strict(tmp_path):
    en, es = tiny(), tiny("es")
    draft = issues(en, es, folder=tmp_path)
    assert {i.level for i in draft} == {"request"}
    assert {i.message.split(":")[0] for i in draft if i.code == "audio-missing"} == {"narration 'en'", "narration 'es'"}
    assert codes(en, es, folder=tmp_path, strict=True) == {"image-missing"}
    # Narration isn't needed to publish: a language without all its clips reads without narration.
    strict = issues(en, es, folder=tmp_path, strict=True)
    assert {(i.code, i.level) for i in strict if i.code == "audio-missing"} == {("audio-missing", "warning")}
    (tmp_path / "images").mkdir()
    for image_id in set(image_ids(en)):
        (tmp_path / "images" / f"{image_id}.png").write_bytes(b"")
    assert codes(en, es, folder=tmp_path, strict=True) == set()


def _shot(image_id):
    if image_id in ("f1-a", "f1-b"):
        return {"id": image_id, "subject": "A door.", "background": "A wall."}
    return {"id": image_id, "scene": "Pip somewhere."}


def test_shots_must_cover_every_image():
    s = tiny()
    shots = {"story": "tiny", "images": [_shot(i) for i in image_ids(s) if i != "p2"] + [_shot("extra"), _shot("e1")]}
    found = {(i.code, i.level) for i in check_shots(s, shots)}
    assert found == {("shot-missing", "error"), ("shot-unused", "warning"), ("shot-duplicate", "error")}


def test_cards_need_a_subject_and_pages_a_scene():
    s = tiny()
    shots = {"story": "tiny", "images": [_shot(i) for i in image_ids(s)]}
    shots["images"][3] = {"id": "f1-a", "scene": "A door."}  # a card written like a page
    del shots["images"][1]["scene"]  # p1 without a scene
    found = sorted(i.message for i in check_shots(s, shots) if i.code == "shot-fields")
    assert found == ["'f1-a' is a choice card and needs subject, background",
                     "'p1' is a page, ending or cover and needs scene"]


def test_shots_name_bible_cast_and_places_and_edit_earlier_images_of_their_kind():
    from storytool.check import check_shot_links
    s = tiny()
    bible = {"cast": [{"id": "pip"}], "places": [{"id": "woods", "look": "Tall woods."}]}
    shots = {"story": "tiny", "images": [_shot(i) for i in image_ids(s)]}
    by_id = {shot["id"]: shot for shot in shots["images"]}
    by_id["p1"].update(place="woods", cast=[{"id": "pip", "state": "waving"}])
    by_id["p2"].update(editFrom="p1")  # fine: an earlier page
    by_id["f1-b"].update(editFrom="f1-a")  # fine: an earlier card
    assert check_shot_links(s, shots, bible) == []
    by_id["f1"].update(place="moon", cast=[{"id": "ghost", "state": "boo"}])
    by_id["p1"].update(editFrom="p2")  # later in reading order
    by_id["e1"].update(editFrom="f1-a")  # a card, for a page
    by_id["e2"].update(editFrom="nowhere")
    found = sorted((i.code, i.message.split("'")[1]) for i in check_shot_links(s, shots, bible))
    assert found == [("shot-cast", "f1"), ("shot-edit", "e1"), ("shot-edit", "e2"), ("shot-edit", "p1"),
                     ("shot-place", "f1")]


FOX = ROOT / "content" / "stories" / "fox-and-the-lantern"


def test_bible_and_outline_of_the_spike_pass():
    assert check_bible(load_json(FOX / "bible.json")) == []
    assert check_outline(load_json(FOX / "outline.json"), "4-7") == []


def test_bible_needs_a_never_list():
    bible = load_json(FOX / "bible.json")
    bible["never"] = []
    assert {i.code for i in check_bible(bible)} == {"schema"}


def test_bible_needs_a_value_shape_and_arc():
    bible = load_json(FOX / "bible.json")
    del bible["craft"]
    assert {i.code for i in check_bible(bible)} == {"schema"}


def test_bible_craft_uses_the_ids_of_the_craft_guides():
    bible = load_json(FOX / "bible.json")
    bible["craft"]["value"] = "being-nice"  # not in craft/values.md
    assert {i.code for i in check_bible(bible)} == {"schema"}
    bible = load_json(FOX / "bible.json")
    bible["craft"]["structure"] = "hero-journey"  # not in craft/structures.md
    assert {i.code for i in check_bible(bible)} == {"schema"}


def test_craft_ids_match_the_craft_guides():
    """Every value, shape and arc id the bible schema allows has its own section in the craft guides, and back."""
    import re
    schema = load_json(ROOT / "schema" / "bible.schema.json")
    craft = schema["properties"]["craft"]["properties"]
    guides = ROOT / "tools" / "prompts" / "craft"
    in_values = set(re.findall(r"^### `([a-z-]+)`", (guides / "values.md").read_text(), re.M))
    in_structures = set(re.findall(r"^### `([a-z-]+)`", (guides / "structures.md").read_text(), re.M))
    assert set(schema["$defs"]["value"]["enum"]) == in_values
    assert set(craft["structure"]["enum"]) | set(craft["arc"]["enum"]) == in_structures


def test_outline_graph_rules():
    outline = load_json(FOX / "outline.json")
    outline["nodes"]["p08b-goose"]["next"] = "p07b-bridge"  # a loop back to the bridge
    assert "cycle" in {i.code for i in check_outline(outline, "4-7")}


def test_story_must_match_the_outline():
    outline, story = load_json(FOX / "outline.json"), load_json(FOX / "story" / "en.json")
    assert check_consistency(outline, story) == []
    story["nodes"]["p08a-across"]["next"] = "p10a-friends"
    del story["nodes"]["e5-windowsill"]
    found = {(i.node, i.code) for i in check_consistency(outline, story)}
    assert found == {("p08a-across", "outline-mismatch"), ("e5-windowsill", "outline-mismatch")}


@pytest.mark.parametrize("story_id", ["fox-and-the-lantern", "noelia-and-the-baby-squirrel"])
def test_real_story_passes_in_draft_mode(story_id):
    from storytool.check import check_folder
    issues = check_folder(ROOT / "content" / "stories" / story_id)
    assert [str(i) for i in issues if i.level != "request"] == []


def test_requested_assets_is_up_to_date():
    from storytool import assets
    assert assets.write(check_only=True), "REQUESTED_ASSETS.md is stale: run `uv run story assets`"


# Languages: translation sheets and narrators ----------------------------------------------------------------------

FOX = ROOT / "content" / "stories" / "fox-and-the-lantern"


def fox_copy(tmp_path):
    import shutil
    folder = tmp_path / "fox-and-the-lantern"
    shutil.copytree(FOX, folder, ignore=shutil.ignore_patterns("images", "audio", "*.md"))
    return folder


def language_codes(folder):
    from storytool.check import check_folder
    return {(i.code, i.level) for i in check_folder(folder) if i.level != "request"}


def test_every_language_after_the_first_needs_its_translation_sheet(tmp_path):
    folder = fox_copy(tmp_path)
    assert language_codes(folder) == set()
    (folder / "translations" / "es.json").unlink()
    assert language_codes(folder) == {("translation-missing", ERROR)}


def test_the_translation_sheet_names_everyone_and_keeps_every_refrain_and_mood(tmp_path):
    import json
    folder = fox_copy(tmp_path)
    path = folder / "translations" / "es.json"
    sheet = json.loads(path.read_text())
    del sheet["cast"]["glim"]
    del sheet["cast"]["mia"]["refrain"]
    del sheet["moods"]["p01-meadow"]
    sheet["cast"]["dragon"] = {"name": "Dragón"}
    sheet["from"] = "de"
    path.write_text(json.dumps(sheet))
    assert language_codes(folder) == {("translation-name", ERROR), ("translation-cast", ERROR),
                                      ("translation-mood", ERROR), ("translation-unknown", WARNING),
                                      ("translation-from", ERROR)}


def test_a_language_needs_a_narrator_and_a_sheet_for_it_must_be_one_of_the_story_languages(tmp_path):
    import json
    folder = fox_copy(tmp_path)
    story = json.loads((folder / "story" / "en.json").read_text())
    story.update(language="xx", translatedFrom="en")
    (folder / "story" / "xx.json").write_text(json.dumps(story))
    (folder / "translations" / "xx.json").write_text((folder / "translations" / "es.json").read_text()
                                                    .replace('"language": "es"', '"language": "xx"'))
    (folder / "translations" / "de.json").write_text("{}")
    assert language_codes(folder) == {("narrator-missing", ERROR), ("translation-unused", WARNING),
                                      ("bible-title", WARNING)}


def test_the_narrators_pass_their_schema():
    from storytool.check import check_voice
    voice = load_json(ROOT / "content" / "style" / "voice.json")
    assert check_voice(voice) == []
    del voice["narrators"]["es"]["clips"]["question"]
    assert {i.code for i in check_voice(voice)} == {"schema"}


# Catalog (the shelf, free and paid) -------------------------------------------------------------------------------

from storytool.check import check_app_assets, check_catalog  # noqa: E402

STORIES_SEEN = {"fox-and-the-lantern": {}, "draft": None}


def catalog_codes(catalog):
    return {i.code for i in check_catalog(catalog, STORIES_SEEN)}


def test_real_catalog_and_app_images_pass():
    folders = [p for p in (ROOT / "content" / "stories").iterdir() if has_story(p)]
    stories = {p.name: load_story(p) for p in folders}
    translations = {p.name: {lang: load_json(p / "translations" / f"{lang}.json")["status"]
                             for lang in list(stories[p.name])[1:]} for p in folders}
    assert check_catalog(load_json(ROOT / "content" / "catalog.json"), stories, translations) == []
    assert check_app_assets(load_json(ROOT / "content" / "app" / "assets.json")) == []


def test_app_images_edit_an_earlier_image():
    icon = {"id": "app-icon", "kind": "icon", "subject": "An open book."}
    layer = {"id": "app-icon-background", "kind": "icon-background", "subject": "Its background.",
             "editFrom": "app-icon"}
    assert check_app_assets({"images": [icon, layer]}) == []
    assert {i.code for i in check_app_assets({"images": [layer, icon]})} == {"app-edit"}


def test_a_paid_story_needs_a_product_and_a_free_one_has_none():
    assert catalog_codes({"stories": [{"id": "fox-and-the-lantern", "access": "paid"}]}) == {"schema"}
    assert catalog_codes({"stories": [{"id": "fox-and-the-lantern", "access": "free", "product": "x.y"}]}) == {"schema"}


def test_product_ids_work_on_both_stores():
    entry = {"id": "fox-and-the-lantern", "access": "paid", "product": "story.fox-and-the-lantern"}
    assert catalog_codes({"stories": [entry]}) == {"schema"}  # Google Play allows no '-'


def test_catalog_entries_must_be_written_unique_stories():
    found = catalog_codes({"stories": [
        {"id": "fox-and-the-lantern", "access": "free"},
        {"id": "fox-and-the-lantern", "access": "paid", "product": "story.fox"},
        {"id": "draft", "access": "paid", "product": "story.fox"},
        {"id": "nowhere", "access": "free"},
    ]})
    assert found == {"catalog-duplicate", "catalog-unwritten", "catalog-product", "catalog-unknown"}


def test_a_story_is_approved_in_languages_it_is_written_in_and_a_native_speaker_approved():
    stories = {"fox-and-the-lantern": {"en": {}, "es": {}}}
    entry = {"id": "fox-and-the-lantern", "access": "free", "approved": ["es"]}
    reviewed = {"fox-and-the-lantern": {"es": "approved"}}
    assert check_catalog({"stories": [entry]}, stories, reviewed) == []
    assert check_catalog({"stories": [{**entry, "approved": ["en"]}]}, stories) == []
    assert {i.code for i in check_catalog({"stories": [entry]}, stories, {"fox-and-the-lantern": {"es": "draft"}})} \
        == {"catalog-approved"}
    assert {i.code for i in check_catalog({"stories": [{**entry, "approved": ["fr"]}]}, stories, reviewed)} \
        == {"catalog-approved"}
    assert {i.code for i in check_catalog({"stories": [{**entry, "approved": []}]}, stories)} == {"schema"}


def test_the_shelf_should_open_with_a_free_story():
    found = {(i.code, i.level) for i in check_catalog(
        {"stories": [{"id": "fox-and-the-lantern", "access": "paid", "product": "story.fox"}]}, STORIES_SEEN)}
    assert found == {("catalog-first-free", "warning")}


def test_no_story_can_be_called_app():
    found = {(i.code, i.level) for i in check_catalog(
        {"stories": [{"id": "fox-and-the-lantern", "access": "free"}, {"id": "app", "access": "free"}]},
        {**STORIES_SEEN, "app": {"en": {}}})}
    assert found == {("catalog-reserved", "error")}


# The end of every story (content/app/narration.json) ---------------------------------------------------------------

from storytool.check import REQUEST, check_app_narration  # noqa: E402
from storytool.repo import APP_CLIPS  # noqa: E402

NARRATION = load_json(ROOT / "content" / "app" / "narration.json")
NARRATORS = load_json(ROOT / "content" / "style" / "voice.json")["narrators"]


def narration_codes(doc, narrators=NARRATORS, **kw):
    return {(i.code, i.level) for i in check_app_narration(doc, narrators, **kw)}


def test_the_real_app_narration_passes():
    assert check_app_narration(NARRATION, NARRATORS) == []
    assert list(NARRATION) == list(NARRATORS)


def test_the_app_narration_has_exactly_the_three_lines_each_with_words():
    doc = copy.deepcopy(NARRATION)
    del doc["en"]["all-endings"]
    assert narration_codes(doc) == {("schema", ERROR)}
    doc = copy.deepcopy(NARRATION)
    doc["es"]["bonus"] = {"script": "¡Hola!", "direction": "Alegre."}
    assert narration_codes(doc) == {("schema", ERROR)}
    doc = copy.deepcopy(NARRATION)
    doc["es"]["the-end"]["script"] = ""
    assert narration_codes(doc) == {("schema", ERROR)}
    doc = copy.deepcopy(NARRATION)
    doc["en-US"] = doc["en"]
    assert narration_codes(doc) == {("schema", ERROR)}


def test_every_narrator_has_its_lines_and_every_line_a_narrator():
    doc = copy.deepcopy(NARRATION)
    doc["fr"] = doc.pop("es")
    found = check_app_narration(doc, NARRATORS)
    assert {(i.code, i.level) for i in found} == {("app-narration-missing", ERROR), ("narrator-missing", ERROR)}
    assert any("'es'" in i.message for i in found if i.code == "app-narration-missing")
    assert any("'fr'" in i.message for i in found if i.code == "narrator-missing")


def test_app_clips_not_delivered_are_requests_and_warnings_when_strict(tmp_path):
    (tmp_path / "audio" / "es").mkdir(parents=True)
    (tmp_path / "audio" / "es" / "the-end.wav").write_bytes(b"")
    found = check_app_narration(NARRATION, NARRATORS, tmp_path)
    assert [(i.code, i.level) for i in found] == [("app-audio-missing", REQUEST)] * 2
    assert "app narration 'es': 1 of 3 clips delivered" in found[1].message
    assert {i.level for i in check_app_narration(NARRATION, NARRATORS, tmp_path, strict=True)} == {WARNING}
    for lang in NARRATION:
        (tmp_path / "audio" / lang).mkdir(exist_ok=True)
        for clip in APP_CLIPS:
            (tmp_path / "audio" / lang / f"{clip}.flac").write_bytes(b"")
    assert check_app_narration(NARRATION, NARRATORS, tmp_path, strict=True) == []
