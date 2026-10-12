# AI-generated with Claude Code, 2026-10-04, reviewed by Jaewan Park
"""Per-client request cap that bounds what one phone can spend on the image API (Design Documentation 2.8)."""

from collections import deque
from collections.abc import Callable


class RateLimiter:
    """Sliding window per key. Process-local, like the session registry, so it assumes a single worker."""

    def __init__(self, limit: int, window_seconds: float, clock: Callable[[], float]) -> None:
        self._limit = limit
        self._window = window_seconds
        self._clock = clock
        self._hits: dict[str, deque[float]] = {}

    def allow(self, key: str) -> bool:
        now = self._clock()
        hits = self._hits.setdefault(key, deque())
        while hits and hits[0] <= now - self._window:
            hits.popleft()
        if len(hits) >= self._limit:
            return False
        hits.append(now)
        return True
