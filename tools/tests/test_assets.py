"""The image requests: locked descriptions word for word, the right images attached, edits and layers, one requests
file per story with its own style and characters, and sharing only when a bible declares it."""
import json
import shutil

from storytool.assets import Story, outputs, render, render_story
from storytool.check import check_folder
from storytool.repo import REQUESTED_ASSETS, STORIES, load_json

FOX = Story("fox-and-the-lantern")
STYLE_DOC = FOX.style.doc
ANCHOR = f"content/stories/fox-and-the-lantern/style/{STYLE_DOC['anchor']['id']}.png"


def text(image_id):
    return "\n".join(FOX.prompt(image_id))


def attached(image_id):
    return [r.path.rsplit("/", 1)[-1] for r in FOX.refs(image_id)]


def test_every_prompt_pastes_the_style_block_and_each_look_word_for_word():
    for image_id in FOX.ordered_images():
        prompt = text(image_id)
        assert f"Style: {STYLE_DOC['prompt']}" in prompt
        for cast_id in FOX.cast_ids(FOX.shot[image_id]):
            assert f"{FOX.name(cast_id)}: {FOX.who[cast_id]['look']}" in prompt, (image_id, cast_id)
        place = FOX.shot[image_id].get("place")
        if place:
            assert f"Place: {FOX.places[place]}" in prompt


def test_each_attached_image_gets_its_role_in_order():
    prompt = text("p05b-bramble")
    refs = FOX.refs("p05b-bramble")
    assert refs[0].path == ANCHOR
    for n, ref in enumerate(refs, 1):
        assert f"Image {n} is {ref.role}." in prompt
    # Bramble has no sheet: described in words only.
    assert attached("p05b-bramble") == ["style-anchor.png", "mia.png", "tuck.png", "lantern.png", "glim.png",
                                        "p04b-garden.png"]


def test_the_first_picture_of_a_place_is_the_location_reference_for_the_rest():
    assert FOX.place_ref("p06-brook") is None
    assert FOX.place_ref("p07a-stones") == "p06-brook"
    assert FOX.place_ref("cover") == "p01-meadow"  # the cover is made last
    assert FOX.ordered_images()[-1] == "cover"
    assert "p06-brook.png" not in attached("p06-brook-a")  # cards keep a plain background


def test_an_edit_attaches_the_picture_to_edit_first_instead_of_the_style_anchor():
    assert attached("p08b-goose")[0] == "p07b-bridge.png"
    assert ANCHOR not in [r.path for r in FOX.refs("p08b-goose")]
    assert FOX.crowded("p08b-goose") == 0


def test_crowded_scenes_are_built_in_layers_with_held_props_alongside():
    assert FOX.crowded("p03-tuck") == 0  # Mia, Tuck and Glim
    assert FOX.crowded("p07b-bridge") == 4
    steps = FOX.layers("p07b-bridge")
    assert [title for title, _, _ in steps] == ["The place", "Add Gertie", "Add Tuck", "Add Mia and the lantern",
                                                "Add Glim"]
    place, base, lines = steps[0]
    assert [r.kind for r in base] == ["style", "place"]
    assert any(line.startswith("Paint only the place and its light for now, with no characters") for line in lines)
    _, sheets, lines = steps[3]
    assert [r.path.rsplit("/", 1)[-1] for r in sheets] == ["mia.png", "lantern.png"]
    assert lines[-1] == "Keep everything else exactly the same."


def test_extras_stay_in_the_background_layer():
    _, _, lines = FOX.layers("e2-sky-parade")[0]
    assert any("Paint only the place, its light and the frogs and the fireflies for now" in line for line in lines)


def test_the_index_links_each_story_and_holds_no_story_style_or_character():
    doc = render()
    assert "[The Fox and the Lantern](content/stories/fox-and-the-lantern/REQUESTED_ASSETS.md)" in doc
    assert "style-anchor" not in doc  # each story's anchor is in its own file
    assert STYLE_DOC["prompt"] not in doc
    assert "Mia: " not in doc


def test_each_story_has_its_own_style_anchor_and_sheets_in_its_folder():
    doc = render_story(FOX)
    assert "`content/stories/fox-and-the-lantern/style/style-anchor.png` (or `.webp` / `.jpg`)" in doc
    assert "`content/stories/fox-and-the-lantern/characters/mia.png` (or `.webp` / `.jpg`)" in doc
    assert STYLE_DOC["prompt"] in doc
    noelia = render_story(Story("noelia-and-the-baby-squirrel"))
    assert STYLE_DOC["prompt"] not in noelia  # a different book, a different style
    assert "content/stories/noelia-and-the-baby-squirrel/style/style-anchor." in noelia  # requested or delivered
    assert "fox-and-the-lantern" not in noelia


def _copy(tmp_path, story_id, as_id):
    folder = tmp_path / as_id
    shutil.copytree(STORIES / story_id, folder, ignore=shutil.ignore_patterns("images", "audio", "*.md"))
    return folder


def test_a_story_shares_a_style_or_a_character_only_when_its_bible_declares_it(tmp_path):
    _copy(tmp_path, "fox-and-the-lantern", "fox")
    pup = _copy(tmp_path, "fox-and-the-lantern", "pup")
    shutil.rmtree(pup / "style")
    bible = load_json(pup / "bible.json")
    bible["styleFrom"] = "fox"
    mia = next(c for c in bible["cast"] if c["id"] == "mia")
    mia["from"] = "fox"
    (pup / "bible.json").write_text(json.dumps(bible))
    story = Story("pup", stories=tmp_path)
    assert story.refs("p01-meadow")[0].path.endswith("fox/style/style-anchor.png")
    assert [c["id"] for c in story.shared_sheets()] == ["mia"]
    assert "mia" not in [c["id"] for c in story.sheets()]  # made in its home story, not requested again
    assert any(r.path.endswith("fox/characters/mia.png") for r in story.refs("p01-meadow"))
    assert not [str(i) for i in check_folder(pup) if i.level == "error"]
    # A shared character must keep its look word for word.
    mia["look"] = "A different girl with short red hair, a green dress and blue rain boots."
    (pup / "bible.json").write_text(json.dumps(bible))
    assert "shared-look" in [i.code for i in check_folder(pup)]


def test_a_story_without_its_own_style_or_a_declared_one_fails_once_it_has_shots(tmp_path):
    folder = _copy(tmp_path, "fox-and-the-lantern", "fox")
    shutil.rmtree(folder / "style")
    issues = check_folder(folder)
    assert [i.level for i in issues if i.code == "style-missing"] == ["error"]


def test_each_story_has_its_own_file_with_a_prompt_per_image():
    files = outputs()
    assert REQUESTED_ASSETS in files
    assert STORIES / "fox-and-the-lantern" / "REQUESTED_ASSETS.md" in files
    doc = render_story(FOX)
    assert doc.count("```text") >= len(FOX.ordered_images()) + len(FOX.sheets())
    assert "(../../../REQUESTED_ASSETS.md)" in doc  # back to the shared file for the style anchor
    assert "### ⬜ `p01-meadow`: Page illustration" in doc
