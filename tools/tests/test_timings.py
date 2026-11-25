import math
import shutil
import wave
from array import array

import pytest

from storytool.timings import RATE, Heard, Pause, break_after, heard_in, listen, match, syllables, timings, words


def tone(seconds, level=0.3):
    return [level * math.sin(2 * math.pi * 220 * i / RATE) for i in range(int(seconds * RATE))]


def quiet(seconds):
    return [0.0] * int(seconds * RATE)


def test_words_are_runs_of_non_space_like_the_app_splits_them():
    assert words('"¡Una estrella bebé!", exclamó.') == ['"¡Una', "estrella", 'bebé!",', "exclamó."]


def test_syllables_count_groups_of_vowels():
    assert [syllables(w) for w in ["Mia", "farolito", "mmm-mmm,", "—", "abuelo."]] == [1, 4, 1, 0, 3]


def test_breaks_after_sentences_clauses_and_quotes():
    assert break_after("rápido.", "Comía") == "sentence"
    assert break_after('ellas!"', "Tuck") == "sentence"
    assert break_after("abuela,", "el") == "clause"
    assert break_after("cosa:", "un") == "clause"
    assert break_after("exclamó", '"Esta') == "clause"
    assert break_after("alto", "y") is None


def test_pauses_are_found_and_a_click_inside_one_does_not_split_it():
    click = [0.5] * 30  # 2 ms
    samples = array("f", quiet(0.3) + tone(1.0) + quiet(0.2) + click + quiet(0.2) + tone(1.0) + quiet(0.05)
                    + tone(0.5) + quiet(0.5))
    heard = heard_in(samples)
    assert heard.start == pytest.approx(0.3, abs=0.02)
    assert heard.end == pytest.approx(3.25, abs=0.02)
    # The 50 ms gap is inside a word, too short for a pause.
    assert len(heard.pauses) == 1
    assert heard.pauses[0].start == pytest.approx(1.3, abs=0.02)
    assert heard.pauses[0].end == pytest.approx(1.7, abs=0.02)


def test_each_pause_goes_to_the_sentence_end_its_timing_fits():
    said = words("Pip woke up. He saw a door, a red one. Then he ran.")
    # 3 syllables, then 7, then 3, at 0.25 s each, with a pause after each sentence.
    heard = Heard(0.3, 0.3 + 0.75 + 0.5 + 1.75 + 0.5 + 0.75,
                  (Pause(1.05, 1.55), Pause(3.3, 3.8)))
    assert match(said, heard) == [(3, 0), (10, 1)]


def test_a_pause_near_a_comma_goes_to_the_comma():
    said = words("Pip, the fox, ran far and fast.")
    # 8 syllables at 0.25 s; the pause comes a little after "fox," (0.75 s): the comma, not mid-phrase.
    heard = Heard(0.0, 2.4, (Pause(0.9, 1.3),))
    assert match(said, heard) == [(3, 0)]


def test_a_pause_far_from_any_comma_is_mid_phrase():
    said = words("Pip, the fox, ran far and fast and then he went home.")
    # 13 syllables at 0.25 s, read through the commas, with one long breath after "then" (2.25 s).
    heard = Heard(0.0, 3.65, (Pause(2.25, 2.65),))
    assert match(said, heard) == [(9, 0)]


def test_words_follow_the_pauses_and_spread_by_syllables():
    heard = Heard(0.3, 2.8, (Pause(1.05, 1.55),))
    t = timings("Pip ran far. Bananas!", heard)
    assert [start for start, _ in t] == [0.3, 0.55, 0.8, 1.55]
    assert t[2][1] == 1.05 and t[3][1] == 2.8  # each word ends where the next starts, or at a pause


def test_no_pauses_spreads_the_whole_clip():
    t = timings("big red cat", Heard(0.5, 2.0, ()))
    assert t == [(0.5, 1.0), (1.0, 1.5), (1.5, 2.0)]


@pytest.mark.skipif(not shutil.which("ffmpeg"), reason="needs ffmpeg")
def test_listen_reads_a_delivered_wav(tmp_path):
    clip = tmp_path / "p1.wav"
    samples = quiet(0.3) + tone(1.0) + quiet(0.4) + tone(0.8) + quiet(0.5)
    with wave.open(str(clip), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(array("h", (int(x * 32767) for x in samples)).tobytes())
    heard = listen(clip)
    assert heard.start == pytest.approx(0.3, abs=0.02)
    assert [(round(p.start, 1), round(p.end, 1)) for p in heard.pauses] == [(1.3, 1.7)]
    assert [round(a, 1) for a, _ in timings("Hello there. Bye now.", heard)] == [0.3, 0.8, 1.7, 2.1]
