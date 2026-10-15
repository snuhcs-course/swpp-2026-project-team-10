# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Verifies Google ID tokens from the app's Google sign-in (Design Documentation AD-12). The token is checked once
and dropped; only Google's subject id is kept."""

import re
from collections.abc import Callable
from dataclasses import dataclass
from typing import Protocol

import httpx2
from google.auth import exceptions, jwt

CERTS_URL = "https://www.googleapis.com/oauth2/v1/certs"
ISSUERS = frozenset({"accounts.google.com", "https://accounts.google.com"})
CERTS_TIMEOUT_SECONDS = 5
# Used when Google's answer gives no max-age; Google rotates its keys over days.
DEFAULT_CERTS_SECONDS = 3600


@dataclass(frozen=True)
class GoogleAccount:
    subject: str
    name: str | None


class InvalidGoogleToken(Exception):
    """The token is malformed, expired, not signed by Google, or meant for another app."""


class GoogleUnavailable(Exception):
    """Google sign-in is not configured, or Google's keys could not be fetched."""


class GoogleVerifier(Protocol):
    async def verify(self, id_token: str) -> GoogleAccount: ...


class GoogleTokenVerifier:
    """Checks the signature against Google's published keys, which it keeps for as long as Google allows, and the
    audience, issuer, and expiry."""

    def __init__(self, client_id: str | None, http: httpx2.AsyncClient, clock: Callable[[], float]) -> None:
        self._client_id = client_id
        self._http = http
        self._clock = clock
        self._certs: dict[str, str] = {}
        self._certs_until = 0.0

    async def verify(self, id_token: str) -> GoogleAccount:
        if not self._client_id:
            raise GoogleUnavailable("PIX_GOOGLE_CLIENT_ID is not set")
        certs = await self._keys()
        try:
            claims = jwt.decode(id_token, certs=certs, audience=self._client_id, clock_skew_in_seconds=10)
        except (ValueError, exceptions.GoogleAuthError) as exc:
            raise InvalidGoogleToken(str(exc)) from exc
        if claims.get("iss") not in ISSUERS or not claims.get("sub"):
            raise InvalidGoogleToken("Not issued by Google")
        return GoogleAccount(subject=str(claims["sub"]), name=claims.get("name"))

    async def _keys(self) -> dict[str, str]:
        now = self._clock()
        if self._certs and now < self._certs_until:
            return self._certs
        try:
            response = await self._http.get(CERTS_URL, timeout=CERTS_TIMEOUT_SECONDS)
            response.raise_for_status()
            certs = response.json()
        except (httpx2.HTTPError, ValueError) as exc:
            raise GoogleUnavailable("Google's keys could not be fetched") from exc
        max_age = re.search(r"max-age=(\d+)", response.headers.get("cache-control", ""))
        self._certs = certs
        self._certs_until = now + (int(max_age.group(1)) if max_age else DEFAULT_CERTS_SECONDS)
        return certs
