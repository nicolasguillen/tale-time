"""The story graph: node kinds, successors, cycles, reachability, play-through stats and paths."""
from dataclasses import dataclass
from functools import cache

PAGE, FORK, ENDING = "page", "fork", "ending"


def kind(node: dict) -> str:
    if "ending" in node:
        return ENDING
    if "options" in node:
        return FORK
    return PAGE


def successors(node: dict) -> list[str]:
    k = kind(node)
    if k == PAGE:
        return [node["next"]]
    if k == FORK:
        return [o["target"] for o in node["options"]]
    return []


def edges(story: dict) -> list[tuple[str, str]]:
    return [(nid, t) for nid, node in story["nodes"].items() for t in successors(node)]


def reachable(story: dict) -> set[str]:
    nodes, seen, stack = story["nodes"], set(), [story["start"]]
    while stack:
        nid = stack.pop()
        if nid in seen or nid not in nodes:
            continue
        seen.add(nid)
        stack.extend(successors(nodes[nid]))
    return seen


def find_cycle(story: dict) -> list[str] | None:
    """One cycle as a list of node ids (first == last), or None."""
    nodes = story["nodes"]
    state: dict[str, int] = {}  # 1 = on the stack, 2 = done
    trail: list[str] = []

    def visit(nid: str) -> list[str] | None:
        state[nid] = 1
        trail.append(nid)
        for t in successors(nodes[nid]):
            if t not in nodes:
                continue
            if state.get(t) == 1:
                return trail[trail.index(t):] + [t]
            if t not in state and (found := visit(t)):
                return found
        trail.pop()
        state[nid] = 2
        return None

    for nid in nodes:
        if nid not in state and (found := visit(nid)):
            return found
    return None


@dataclass(frozen=True)
class Stats:
    """Over every play-through from the start: page and fork counts (pages include the ending)."""
    min_pages: int
    max_pages: int
    min_forks: int
    max_forks: int
    paths: int


def stats(story: dict) -> Stats:
    """Only valid on an acyclic graph with no dangling targets."""
    nodes = story["nodes"]

    @cache
    def from_node(nid: str) -> tuple[int, int, int, int, int]:
        node = nodes[nid]
        nexts = successors(node)
        fork = 1 if kind(node) == FORK else 0
        if not nexts:
            return 1, 1, 0, 0, 1
        subs = [from_node(t) for t in nexts]
        return (
            1 + min(s[0] for s in subs),
            1 + max(s[1] for s in subs),
            fork + min(s[2] for s in subs),
            fork + max(s[3] for s in subs),
            sum(s[4] for s in subs),
        )

    return Stats(*from_node(story["start"]))


# A path is a list of steps: (node id, index of the option picked at a fork, or None).
Path = list[tuple[str, int | None]]


def all_paths(story: dict, limit: int = 10_000) -> list[Path]:
    nodes, out = story["nodes"], []

    def walk(nid: str, path: Path):
        if len(out) >= limit:
            return
        node = nodes[nid]
        k = kind(node)
        if k == ENDING:
            out.append(path + [(nid, None)])
        elif k == PAGE:
            walk(node["next"], path + [(nid, None)])
        else:
            for i, o in enumerate(node["options"]):
                walk(o["target"], path + [(nid, i)])

    walk(story["start"], [])
    return out


def covering_paths(story: dict) -> list[Path]:
    """A small set of play-throughs that together visit every page and take every choice at least once.

    Greedy: each round takes the path with the most not-yet-covered edges. Reading these is enough to check
    every merge page against every way into it.
    """
    nodes = story["nodes"]
    uncovered = set(edges(story))
    picked: list[Path] = []
    while uncovered:
        best: dict[str, tuple[int, Path]] = {}

        def score(nid: str) -> tuple[int, Path]:
            if nid in best:
                return best[nid]
            node = nodes[nid]
            k = kind(node)
            if k == ENDING:
                result = (0, [(nid, None)])
            elif k == PAGE:
                gain = 1 if (nid, node["next"]) in uncovered else 0
                s, p = score(node["next"])
                result = (s + gain, [(nid, None)] + p)
            else:
                result = (-1, [])
                for i, o in enumerate(node["options"]):
                    gain = 1 if (nid, o["target"]) in uncovered else 0
                    s, p = score(o["target"])
                    if s + gain > result[0]:
                        result = (s + gain, [(nid, i)] + p)
            best[nid] = result
            return result

        gained, path = score(story["start"])
        if gained == 0:
            break  # Edges no path from the start can reach; reported by `check` as unreachable nodes.
        picked.append(path)
        for (nid, choice), (nxt, _) in zip(path, path[1:]):
            uncovered.discard((nid, nxt))
    return picked
