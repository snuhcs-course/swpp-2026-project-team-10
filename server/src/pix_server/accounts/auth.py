# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Access and refresh tokens, and the request dependencies built on them (Design Documentation AD-12, 2.8).

A sign-in gives the phone two tokens. The access token is a JWT signed with HS256 and `PIX_JWT_SECRET` that names the
user (`sub`) and the sign-in (`sid`) and expires after `PIX_ACCESS_TOKEN_MINUTES`; the server checks it without a
database lookup and stores no copy. The refresh token is random, lasts `PIX_SESSION_TTL_DAYS` from its last use, and
is kept in the `sessions` table only as a hash; `POST /auth/refresh` exchanges it for a new pair. Signing out deletes
the session, so its refresh token stops working at once and its access token within minutes.
"""

import hashlib
import secrets
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Annotated

import jwt
from fastapi import Depends, FastAPI, Request, WebSocket
from sqlalchemy.ext.asyncio import AsyncSession

from pix_server.db.models import User
from pix_server.errors import ApiError

ISSUER = "pix"
ALGORITHM = "HS256"


@dataclass(frozen=True)
class TokenClaims:
    user_id: str
    # The sign-in (`sessions` row) the token was issued for.
    session_id: str


class Tokens:
    """Issues and reads access tokens. Expiry is checked against `clock`, so tests can move time."""

    def __init__(self, secret: str, ttl_seconds: float, clock: Callable[[], float]) -> None:
        self._secret = secret
        self.ttl_seconds = int(ttl_seconds)
        self._clock = clock

    def issue(self, user_id: str, session_id: str) -> str:
        now = int(self._clock())
        claims = {"iss": ISSUER, "sub": user_id, "sid": session_id, "iat": now, "exp": now + self.ttl_seconds}
        return jwt.encode(claims, self._secret, algorithm=ALGORITHM)

    def read(self, token: str) -> TokenClaims | None:
        """The claims of a token this server signed and that has not expired, or None."""
        try:
            claims = jwt.decode(
                token,
                self._secret,
                algorithms=[ALGORITHM],
                issuer=ISSUER,
                # PyJWT would compare the times with the real clock; the comparison below uses ours.
                options={"require": ["iss", "sub", "sid", "iat", "exp"], "verify_exp": False, "verify_iat": False},
            )
        except jwt.InvalidTokenError:
            return None
        if not isinstance(claims["exp"], int) or claims["exp"] <= self._clock():
            return None
        return TokenClaims(user_id=str(claims["sub"]), session_id=str(claims["sid"]))


def new_refresh_token() -> str:
    return secrets.token_urlsafe(32)


def hash_token(token: str) -> str:
    """Refresh tokens are random and long, so a fast hash is enough to keep a leaked table from giving valid ones."""
    return hashlib.sha256(token.encode()).hexdigest()


def bearer_token(header: str | None) -> str | None:
    scheme, _, token = (header or "").partition(" ")
    token = token.strip()
    return token if scheme.lower() == "bearer" and token else None


def now_of(app: FastAPI) -> datetime:
    return datetime.fromtimestamp(app.state.clock(), UTC)


def millis(moment: datetime) -> int:
    return round(moment.timestamp() * 1000)


@dataclass
class Caller:
    user: User
    session_id: str


async def database(request: Request) -> AsyncIterator[AsyncSession]:
    async with request.app.state.database() as session:
        yield session


Database = Annotated[AsyncSession, Depends(database)]


async def caller(request: Request, db: Database) -> Caller:
    token = bearer_token(request.headers.get("authorization"))
    tokens: Tokens = request.app.state.tokens
    claims = tokens.read(token) if token is not None else None
    user = await db.get(User, claims.user_id) if claims is not None else None
    if user is None:
        raise ApiError(401, "UNAUTHENTICATED", "Sign in to use this")
    return Caller(user=user, session_id=claims.session_id)


SignedIn = Annotated[Caller, Depends(caller)]


def socket_user(websocket: WebSocket) -> str | None:
    """The user a signaling socket belongs to, which makes them online for their friends (FR-5.9). A missing,
    invalid, or expired access token leaves the socket anonymous, because room codes work without an account
    (FR-5.1). The token is checked once, when the socket opens."""
    token = bearer_token(websocket.headers.get("authorization"))
    tokens: Tokens = websocket.app.state.tokens
    claims = tokens.read(token) if token is not None else None
    return claims.user_id if claims is not None else None
