# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""Push notifications through Firebase Cloud Messaging's HTTP v1 API (Design Documentation 2.6.6, AD-13).

The server signs in as a service account of the app's Firebase project: it signs a JWT with the account's key and
exchanges it at Google for an access token, which it keeps until a minute before it expires. Every push is a data
message, so the app writes and opens the notification itself in every app state. A push never fails the request that
caused it: an invitation still has its link, and a friend request still shows on Friends. The key, the access token,
and the device tokens never reach the log.
"""

import asyncio
import json
import logging
from collections.abc import Callable
from dataclasses import dataclass

import httpx2
from fastapi import FastAPI
from google.auth import crypt, jwt
from sqlalchemy import delete, select
from sqlalchemy.exc import InterfaceError, OperationalError
from sqlalchemy.exc import TimeoutError as PoolTimeoutError

from pix_server.db.models import Device
from pix_server.errors import raised_by_database

logger = logging.getLogger(__name__)

SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
SEND_URL = "https://fcm.googleapis.com/v1/projects/{project}/messages:send"
GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
# How long the signed request for an access token is valid; Google's access tokens last as long.
ASSERTION_SECONDS = 3600
# An access token is renewed this long before it expires.
RENEW_SECONDS = 60


@dataclass(frozen=True)
class ServiceAccount:
    project_id: str
    client_email: str
    token_uri: str
    signer: crypt.Signer


def load_service_account(path: str | None) -> ServiceAccount | None:
    """The service account of the key file at `path`; None when it is unset, or, with a warning, when the file is not a
    readable service account key."""
    if not path:
        return None
    try:
        with open(path, encoding="utf-8") as file:
            info = json.load(file)
        if info.get("type") != "service_account":
            raise ValueError("not a service account key")
        return ServiceAccount(
            project_id=info["project_id"],
            client_email=info["client_email"],
            token_uri=info["token_uri"],
            signer=crypt.RSASigner.from_service_account_info(info),
        )
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as exc:
        logger.warning("PIX_FCM_CREDENTIALS_FILE cannot be used (%s): no push notifications", type(exc).__name__)
        return None


class PushSender:
    def __init__(
        self, account: ServiceAccount | None, http: httpx2.AsyncClient, clock: Callable[[], float], timeout: float
    ) -> None:
        """`clock` is wall-clock time, because Google checks the times in the signed request."""
        self._account = account
        self._http = http
        self._clock = clock
        self._timeout = timeout
        self._token: str | None = None
        self._token_expires = 0.0
        self._lock = asyncio.Lock()

    @property
    def enabled(self) -> bool:
        return self._account is not None

    async def send(self, tokens: list[str], data: dict[str, str], ttl_seconds: int) -> list[str]:
        """Sends the data message to each device token at high priority, which FCM drops if it cannot deliver it within
        `ttl_seconds`. Returns the tokens FCM no longer knows, whose devices are to be removed."""
        if self._account is None or not tokens:
            return []
        try:
            async with asyncio.timeout(self._timeout):
                access = await self._access_token()
                if access is None:
                    return []
                gone = await asyncio.gather(*(self._send_one(access, token, data, ttl_seconds) for token in tokens))
        except TimeoutError:
            logger.warning("FCM did not answer within %g s; pushes may be lost", self._timeout)
            return []
        except httpx2.HTTPError as exc:
            logger.warning("FCM could not be reached (%s); pushes are lost", type(exc).__name__)
            return []
        return [token for token, unknown in zip(tokens, gone, strict=True) if unknown]

    async def _access_token(self) -> str | None:
        async with self._lock:
            now = self._clock()
            if self._token is not None and now < self._token_expires - RENEW_SECONDS:
                return self._token
            account = self._account
            issued = int(now)
            claims = {
                "iss": account.client_email,
                "scope": SCOPE,
                "aud": account.token_uri,
                "iat": issued,
                "exp": issued + ASSERTION_SECONDS,
            }
            assertion = jwt.encode(account.signer, claims).decode()
            response = await self._http.post(account.token_uri, data={"grant_type": GRANT_TYPE, "assertion": assertion})
            try:
                body = response.json() if response.is_success else {}
                token, lifetime = body["access_token"], float(body["expires_in"])
            except (ValueError, KeyError, TypeError):
                logger.warning("Google answered %s without an FCM access token; pushes are lost", response.status_code)
                return None
            self._token, self._token_expires = token, now + lifetime
            return token

    async def _send_one(self, access: str, token: str, data: dict[str, str], ttl_seconds: int) -> bool:
        """True when FCM no longer knows the token: the app was removed, or its data cleared."""
        message = {"token": token, "data": data, "android": {"priority": "HIGH", "ttl": f"{ttl_seconds}s"}}
        response = await self._http.post(
            SEND_URL.format(project=self._account.project_id),
            json={"message": message},
            headers={"Authorization": f"Bearer {access}"},
        )
        if response.is_success:
            return False
        if response.status_code == 404:
            return True
        if response.status_code == 401:
            # Google stopped accepting the access token; the next push signs in again.
            self._token = None
        logger.warning("FCM refused a push with %s", response.status_code)
        return False


async def push_to_user(app: FastAPI, user_id: str, data: dict[str, str], ttl_seconds: int) -> None:
    """Sends a push to every phone the user is signed in on, and removes the devices FCM no longer knows. Runs after the
    response (a background task), so a slow FCM never holds up the request that caused it."""
    sender: PushSender = app.state.push
    if not sender.enabled:
        return
    try:
        async with app.state.database() as db:
            tokens = list(await db.scalars(select(Device.fcm_token).where(Device.user_id == user_id)))
        gone = await sender.send(tokens, data, ttl_seconds)
        if gone:
            async with app.state.database() as db:
                await db.execute(delete(Device).where(Device.fcm_token.in_(gone)))
                await db.commit()
    except (OperationalError, InterfaceError, PoolTimeoutError) as exc:
        logger.warning("The database failed around a push (%s)", type(exc).__name__)
    except OSError as exc:
        if not raised_by_database(exc):
            raise
        logger.warning("The database failed around a push (%s)", type(exc).__name__)
