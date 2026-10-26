# AI-generated with Claude Code, 2026-10-25, reviewed by Sungmin Jo
"""Short-lived TURN credentials from Cloudflare Realtime, one set for each phone (Design Documentation 2.5.2, AD-14).

The key's API token stays on the server; a phone gets a username and credential that expire after `turn_ttl_seconds`.
Without a key, or when Cloudflare fails or is slow, a phone gets Cloudflare's STUN server only, which is free: a session
on one Wi-Fi still connects, and one across networks connects when the two networks allow a direct path.
"""

import asyncio
import logging
from urllib.parse import quote

import httpx2

from pix_server.config import Settings

logger = logging.getLogger(__name__)

API_URL = "https://rtc.live.cloudflare.com/v1/turn/keys/{key_id}/credentials/generate-ice-servers"
STUN_URL = "stun:stun.cloudflare.com:3478"


def stun_only() -> list[dict]:
    """A new list each time, so a caller that changes it changes no other phone's."""
    return [{"urls": [STUN_URL]}]


def readable(servers: object) -> list[dict] | None:
    """The entries as the app decodes them (`IceServer` in SignalMessage.kt): `urls` as a list of strings, and the
    username and credential when given, without other fields. None when the answer has another shape."""
    if not isinstance(servers, list) or not servers:
        return None
    entries = []
    for server in servers:
        if not isinstance(server, dict):
            return None
        urls = server.get("urls")
        urls = [urls] if isinstance(urls, str) else urls
        if not isinstance(urls, list) or not urls or not all(isinstance(url, str) for url in urls):
            return None
        entry: dict = {"urls": urls}
        for key in ("username", "credential"):
            if key in server:
                if not isinstance(server[key], str):
                    return None
                entry[key] = server[key]
        entries.append(entry)
    return entries


class TurnRelay:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def ice_servers(self) -> list[dict]:
        """The `iceServers` for one phone. It never raises: anything but a usable answer gives STUN only. The answer
        carries the credential, so only its status reaches the log."""
        key_id = self._settings.turn_key_id
        token = self._settings.turn_api_token
        if not key_id or token is None or not token.get_secret_value():
            return stun_only()
        url = API_URL.format(key_id=quote(key_id, safe=""))
        headers = {"Authorization": f"Bearer {token.get_secret_value()}"}
        timeout = self._settings.turn_timeout_seconds
        try:
            async with asyncio.timeout(timeout):
                response = await self._http.post(url, json={"ttl": self._settings.turn_ttl_seconds}, headers=headers)
        except TimeoutError:
            logger.warning("Cloudflare TURN did not answer within %g s; the phone gets STUN only", timeout)
            return stun_only()
        except httpx2.HTTPError as exc:
            logger.warning("Cloudflare TURN could not be reached (%s); the phone gets STUN only", type(exc).__name__)
            return stun_only()
        servers = None
        if response.is_success:
            try:
                servers = readable(response.json().get("iceServers"))
            except (ValueError, AttributeError):
                servers = None
        if servers is None:
            logger.warning(
                "Cloudflare TURN answered %s without usable ICE servers; the phone gets STUN only", response.status_code
            )
            return stun_only()
        return servers
