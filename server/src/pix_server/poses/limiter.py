# AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
"""How many poses and suggestions the models are asked for (Design Documentation 2.8, Privacy): per client address,
so one phone cannot use up the models, and over everyone, which bounds what a day can cost. Poses and suggestions
are counted apart, in windows of the same lengths."""

from typing import Literal

from pix_server.config import Settings
from pix_server.store import SessionStore, Window

Kind = Literal["poses", "suggestions"]
PREFIXES: dict[Kind, str] = {"poses": "rate:pose", "suggestions": "rate:suggest"}


class PoseLimiter:
    """Both windows are counted together, in the session store, so every worker counts against the same ones, and a
    request refused by one is counted in neither."""

    def __init__(self, store: SessionStore, settings: Settings) -> None:
        self._store = store
        self._settings = settings

    async def refuse(self, client: str, kind: Kind = "poses") -> Literal["client", "global"] | None:
        """Counts a request of `kind` for `client`; returns the window that refused it, or None."""
        settings = self._settings
        if kind == "poses":
            limit, global_limit = settings.pose_rate_limit, settings.pose_global_limit
        else:
            limit, global_limit = settings.suggestion_rate_limit, settings.suggestion_global_limit
        prefix = PREFIXES[kind]
        per_client = Window(f"{prefix}:{client}", limit, settings.pose_rate_window_seconds)
        overall = Window(f"{prefix}:all", global_limit, settings.pose_global_window_seconds)
        full = await self._store.allow(per_client, overall)
        if full is None:
            return None
        return "client" if full is per_client else "global"
