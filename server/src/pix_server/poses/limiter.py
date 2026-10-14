# AI-generated with Claude Code, 2026-10-14, reviewed by Jaewan Park
"""Per-client request cap that bounds what one phone can spend on the image API (Design Documentation 2.8)."""

from pix_server.store import SessionStore


class RateLimiter:
    """Sliding window per key, kept in the session store, so every worker counts against the same window."""

    def __init__(self, store: SessionStore, limit: int, window_seconds: float) -> None:
        self._store = store
        self._limit = limit
        self._window = window_seconds

    async def allow(self, key: str) -> bool:
        return await self._store.allow(f"rate:pose:{key}", self._limit, self._window)
