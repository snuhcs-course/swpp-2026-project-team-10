# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""How many poses the image API is asked for (Design Documentation 2.8, Privacy): per client address, so one phone
cannot use up the image API, and over everyone, which bounds what a day can cost."""

from typing import Literal

from pix_server.config import Settings
from pix_server.store import SessionStore, Window

GLOBAL_KEY = "rate:pose:all"


class PoseLimiter:
    """Both windows are counted together, in the session store, so every worker counts against the same ones, and a
    request refused by one is counted in neither."""

    def __init__(self, store: SessionStore, settings: Settings) -> None:
        self._store = store
        self._settings = settings

    async def refuse(self, client: str) -> Literal["client", "global"] | None:
        """Counts a request for `client`; returns the window that refused it, or None."""
        per_client = Window(
            f"rate:pose:{client}", self._settings.pose_rate_limit, self._settings.pose_rate_window_seconds
        )
        overall = Window(GLOBAL_KEY, self._settings.pose_global_limit, self._settings.pose_global_window_seconds)
        full = await self._store.allow(per_client, overall)
        if full is None:
            return None
        return "client" if full is per_client else "global"
