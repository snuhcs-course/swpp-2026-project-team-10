# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""JSON shapes of the account and friend endpoints (Design Documentation 2.5.3)."""

from typing import Annotated, Self

from pydantic import BaseModel, StringConstraints

from pix_server.accounts.rules import DISPLAY_NAME_MAX
from pix_server.db.models import User

DisplayName = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=DISPLAY_NAME_MAX)]


class UserOut(BaseModel):
    id: str
    pixId: str | None
    displayName: str

    @classmethod
    def of(cls, user: User, **extra) -> Self:
        return cls(id=user.id, pixId=user.pix_id, displayName=user.display_name, **extra)


class AccountOut(UserOut):
    """The caller's own account, with the ways they can sign in."""

    hasPassword: bool
    hasGoogle: bool

    @classmethod
    def mine(cls, user: User) -> Self:
        return cls.of(user, hasPassword=user.password_hash is not None, hasGoogle=user.google_sub is not None)


class TokensOut(BaseModel):
    accessToken: str
    refreshToken: str
    # Seconds until the access token expires.
    expiresIn: int


class AuthOut(TokensOut):
    user: AccountOut


class FriendOut(UserOut):
    online: bool
    inSession: bool
