"""When each word of a narrated page is said, measured from its delivered clip (plan.md §7.4).

The app reveals a page's words as the narrator says them. `story package` measures when that is, from the clip and the
page's text, and ships it next to the clips (docs/story-format.md, "Published stories"). Nothing here makes or changes
a sound, and nothing calls a model: it listens for the pauses.

A narrator reading to a child stops between sentences and at many commas. So:
1. ffmpeg decodes the clip, and its quiet stretches are its pauses: 30 dB under its speech, for at least MIN_PAUSE
   (shorter ones are the gaps inside and between words).
2. Each pause is matched, in order, to a place between two words: after a sentence (likely), a comma, a colon, a dash
   or a quote (likely too), or mid-phrase (rarely). The best match is the one where each stretch of speech between two
   pauses lasts about as long as its syllables take at the clip's own pace, a small dynamic program.
3. Inside each stretch, the words are spread by their syllables.

The words are the text's runs of non-space characters, in order: the app splits the text the same way, so the n-th
timing is the n-th word on screen. Sentence by sentence this lands on the narrator's voice; inside a sentence it's a
close guess, which is all the reveal needs (each word fades in over 0.3 s).
"""
import re
import shutil
import subprocess
from array import array
from dataclasses import dataclass
from math import log10
from pathlib import Path

VERSION = "1"  # part of the cache key of every measured clip: bump it when the measuring changes

RATE = 16_000  # samples per second, decoded mono
FRAME = 160  # samples per loudness frame: 10 ms
FRAME_S = FRAME / RATE
MIN_PAUSE = 0.15  # seconds: a quiet stretch at least this long is a pause
QUIET_BELOW_SPEECH = 30.0  # dB under the clip's speech level counts as quiet
MIN_QUIET_DB = -60.0  # never quieter than this, so a clean digital silence still has a threshold to cross

WORD = re.compile(r"[^ \t\n\r\f\v]+")  # what the app counts as a word: Kotlin's \S+, which is ASCII spaces
VOWELS = re.compile(r"[aeiouyáéíóúýàèìòùâêîôûäëïöüÿæœ]+", re.IGNORECASE)
LETTER = re.compile(r"\w")
CLOSERS = "\"'”’»)]"
OPENERS = "\"'“‘«(¡¿["
SENTENCE_END = ".!?…"
CLAUSE_END = ",;:—–"

# What each choice costs the match, in the units of a stretch of speech whose length is off (seconds², per second).
PAUSED = {"sentence": 0.0, "clause": 0.02, None: 0.5}  # a pause after a sentence, a clause, or mid-phrase (rare)
UNPAUSED = {"sentence": 0.3, "clause": 0.03}  # a sentence's end (or a comma) the narrator read straight through
LEFT_OUT_PAUSE = 2.0  # per second of a pause that fits nowhere in the text
MAX_LEFT_OUT = 2  # in a row
MERGE_GAP = 0.06  # seconds: two pauses this close are one (a click between them)


@dataclass(frozen=True)
class Pause:
    start: float
    end: float

    @property
    def length(self) -> float:
        return self.end - self.start


@dataclass(frozen=True)
class Heard:
    """What the clip sounds like: where the speech starts and ends, and the pauses in between."""
    start: float
    end: float
    pauses: tuple[Pause, ...]


def listen(clip: Path) -> Heard:
    """Finds the speech and the pauses in a delivered clip."""
    if not shutil.which("ffmpeg"):
        raise SystemExit("`ffmpeg` isn't installed: packaging needs it (brew install ffmpeg)")
    raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(clip), "-ac", "1", "-ar", str(RATE),
                          "-f", "f32le", "-"], check=True, capture_output=True).stdout
    samples = array("f")
    samples.frombytes(raw[:len(raw) - len(raw) % 4])
    return heard_in(samples)


def heard_in(samples) -> Heard:
    """The speech and pauses in mono samples at RATE (floats, ±1)."""
    levels = []
    for i in range(0, len(samples) - FRAME + 1, FRAME):
        frame = samples[i:i + FRAME]
        levels.append(10 * log10(sum(x * x for x in frame) / FRAME + 1e-12))
    if not levels:
        return Heard(0.0, 0.0, ())
    speech = sorted(levels)[int(len(levels) * 0.9)]
    threshold = max(speech - QUIET_BELOW_SPEECH, MIN_QUIET_DB)
    loud = [level >= threshold for level in levels]
    if not any(loud):
        return Heard(0.0, len(levels) * FRAME_S, ())
    first = loud.index(True)
    last = len(loud) - 1 - loud[::-1].index(True)
    pauses, quiet_from = [], None
    for i in range(first, last + 2):
        if i <= last and not loud[i]:
            quiet_from = i if quiet_from is None else quiet_from
        elif quiet_from is not None:
            pause = Pause(round(quiet_from * FRAME_S, 3), round(i * FRAME_S, 3))
            if pauses and pause.start - pauses[-1].end < MERGE_GAP:
                pause = Pause(pauses.pop().start, pause.end)
            pauses.append(pause)
            quiet_from = None
    pauses = [p for p in pauses if p.length >= MIN_PAUSE]
    return Heard(round(first * FRAME_S, 3), round((last + 1) * FRAME_S, 3), tuple(pauses))


def words(text: str) -> list[str]:
    """The words the app shows one by one: every run of non-space characters."""
    return WORD.findall(text)


def syllables(word: str) -> int:
    """About how many syllables a word takes to say: its groups of vowels (one for a word without any, like "mmm"),
    none for a word without letters (a dash)."""
    if not LETTER.search(word):
        return 0
    return max(1, len(VOWELS.findall(word)))


def break_after(word: str, following: str) -> str | None:
    """Where a narrator stops between `word` and the `following` one: after a "sentence", a "clause" (a comma, a colon,
    a dash, a quote), or None, mid-phrase."""
    end = word.rstrip(CLOSERS)[-1:] or word[-1:]
    if end in SENTENCE_END:
        return "sentence"
    if end in CLAUSE_END or word[-1] in CLOSERS or following[0] in OPENERS:
        return "clause"
    return None


def match(said: list[str], heard: Heard) -> list[tuple[int, int]]:
    """Where each pause falls in the text: (k, p) for pause p after word k (1..n-1), in order. A pause that fits
    nowhere (a click, a breath inside a word) is left out."""
    n = len(said)
    upto = [0]
    for w in said:
        upto.append(upto[-1] + syllables(w))
    pauses = list(heard.pauses)
    quiet = [0.0]
    for pause in pauses:
        quiet.append(quiet[-1] + pause.length)
    pace = max(heard.end - heard.start - quiet[-1], 0.001) / (upto[-1] or 1)  # seconds per syllable
    kinds = [None] + [break_after(said[k - 1], said[k]) for k in range(1, n)]
    # How much leaving the breaks up to after word k without a pause costs (prefix sums), and what a pause costs where.
    unpaused = [0.0, 0.0]
    for k in range(1, n):
        unpaused.append(unpaused[-1] + UNPAUSED.get(kinds[k], 0.0))
    left_out = [0.0]
    for pause in pauses:
        left_out.append(left_out[-1] + LEFT_OUT_PAUSE * pause.length)

    def time_after(p: int) -> float:
        return heard.start if p < 0 else pauses[p].end

    def time_before(p: int) -> float:
        return heard.end if p >= len(pauses) else pauses[p].start

    def cost(ka: int, pa: int, kb: int, pb: int) -> float:
        """The cost of the speech from after pause pa (word ka) to pause pb (word kb), with the pauses between them
        left out, and the breaks between them unpaused."""
        heard_s = time_before(pb) - time_after(pa) - (quiet[pb] - quiet[pa + 1])
        expected = pace * (upto[kb] - upto[ka])
        off = (heard_s - expected) ** 2 / max(expected, 0.5)
        return off + (left_out[pb] - left_out[pa + 1]) + (unpaused[kb] - unpaused[ka + 1] if kb > ka else 0.0)

    # best[(k, p)]: the cheapest way to say the text up to word k by the end of pause p, with pause p after word k.
    # (0, -1) is the start. A dynamic program over the word boundaries and the pauses, in order; at most MAX_LEFT_OUT
    # pauses in a row are left out.
    best: dict[tuple[int, int], tuple[float, tuple[int, int] | None]] = {(0, -1): (0.0, None)}
    for p in range(len(pauses)):
        before = [(k, q, c) for (k, q), (c, _) in best.items() if p - 1 - MAX_LEFT_OUT <= q < p]
        for k in range(1, n):
            here = PAUSED.get(kinds[k], PAUSED[None])
            options = [(c + here + cost(kq, q, k, p), (kq, q)) for kq, q, c in before if kq < k]
            if options:
                best[(k, p)] = min(options)
    last = len(pauses)
    ends = [(c + cost(k, q, n, last), (k, q)) for (k, q), (c, _) in best.items() if q >= last - 1 - MAX_LEFT_OUT]
    _, state = min(ends)
    matched = []
    while state != (0, -1):
        matched.append(state)
        state = best[state][1]
    return matched[::-1]


def timings(text: str, heard: Heard) -> list[tuple[float, float]]:
    """When each word of `text` starts and ends in the clip, in seconds."""
    said = words(text)
    if not said:
        return []
    upto = [0]
    for w in said:
        upto.append(upto[-1] + syllables(w))
    pauses = list(heard.pauses)
    matched = match(said, heard)

    def time_after(p: int) -> float:
        return heard.start if p < 0 else pauses[p].end

    # Spread the words of each stretch by their syllables, stepping over the pauses left out of the match.
    out: list[tuple[float, float]] = []
    anchors = [(0, -1), *matched, (len(said), len(pauses))]
    for (word_a, pause_a), (word_b, pause_b) in zip(anchors, anchors[1:]):
        time_a = time_after(pause_a)
        time_b = heard.end if pause_b >= len(pauses) else pauses[pause_b].start
        inside = pauses[pause_a + 1:pause_b]
        heard_s = max(time_b - time_a - sum(p.length for p in inside), 0.0)
        syl = (upto[word_b] - upto[word_a]) or 1

        def at(offset: float, ending: bool) -> float:
            """The clip's time `offset` seconds of speech into the stretch. A word ending right at a pause ends before
            it; one starting there starts after it."""
            t = time_a + offset
            for p in inside:
                if t > p.start or (t == p.start and not ending):
                    t += p.length
            return t

        for k in range(word_a, word_b):
            begin = heard_s * (upto[k] - upto[word_a]) / syl
            finish = heard_s * (upto[k + 1] - upto[word_a]) / syl
            out.append((round(at(begin, False), 3), round(min(at(finish, True), time_b), 3)))
    return out
