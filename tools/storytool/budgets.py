"""Size budgets per age band (plan.md §4.2). Ranges are inclusive. "Pages" counts every node: pages, forks, endings."""
from dataclasses import dataclass


@dataclass(frozen=True)
class Budget:
    words_per_page: tuple[int, int]
    pages_per_playthrough: tuple[int, int]
    forks_per_playthrough: tuple[int, int]
    pages_total: tuple[int, int]
    endings: tuple[int, int]
    max_images: int


BANDS: dict[str, Budget] = {
    "3-5": Budget((15, 40), (6, 8), (2, 3), (10, 16), (3, 3), 30),
    "4-7": Budget((40, 80), (8, 12), (3, 5), (18, 28), (4, 6), 50),
    "7-10": Budget((80, 150), (12, 18), (5, 7), (30, 45), (6, 8), 80),
}
