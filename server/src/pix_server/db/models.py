# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Tables of the account database (Design Documentation 2.7). A change here needs an Alembic revision."""

import secrets
import time
from datetime import UTC, datetime

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Index, MetaData, String, TypeDecorator, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"


def new_id(prefix: str) -> str:
    """A ULID after the prefix, such as `u_01JA4K…`: 48 bits of milliseconds and 80 random bits."""
    value = (time.time_ns() // 1_000_000) << 80 | secrets.randbits(80)
    chars = []
    for _ in range(26):
        chars.append(_CROCKFORD[value & 31])
        value >>= 5
    return f"{prefix}_{''.join(reversed(chars))}"


class UtcDateTime(TypeDecorator):
    """`timestamptz` on PostgreSQL. SQLite drops the zone, so a value is converted to UTC when it is written and marked
    UTC again when it is read."""

    impl = DateTime(timezone=True)
    cache_ok = True

    def process_bind_param(self, value: datetime | None, dialect) -> datetime | None:
        if value is not None and value.tzinfo is not None:
            return value.astimezone(UTC)
        return value

    def process_result_value(self, value: datetime | None, dialect) -> datetime | None:
        if value is not None and value.tzinfo is None:
            return value.replace(tzinfo=UTC)
        return value


class Base(DeclarativeBase):
    # Named constraints, so a later migration can drop or change one on both PostgreSQL and SQLite.
    metadata = MetaData(
        naming_convention={
            "ix": "ix_%(table_name)s_%(column_0_name)s",
            "uq": "uq_%(table_name)s_%(column_0_name)s",
            "ck": "ck_%(table_name)s_%(constraint_name)s",
            "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
            "pk": "pk_%(table_name)s",
        }
    )


class User(Base):
    __tablename__ = "users"
    # Every user signs in with a password, with Google, or with both.
    __table_args__ = (CheckConstraint("google_sub IS NOT NULL OR password_hash IS NOT NULL", name="sign_in"),)

    id: Mapped[str] = mapped_column(String, primary_key=True)
    # Google's subject id, the only Google data kept.
    google_sub: Mapped[str | None] = mapped_column(String, unique=True)
    # Null until a user who signed up with Google chooses one.
    pix_id: Mapped[str | None] = mapped_column(String, unique=True)
    # An argon2id hash in its PHC string form.
    password_hash: Mapped[str | None] = mapped_column(String)
    display_name: Mapped[str] = mapped_column(String)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime)


class AuthSession(Base):
    """One sign-in on one phone. Its refresh token is kept only as a hash and is replaced on every refresh."""

    __tablename__ = "sessions"

    id: Mapped[str] = mapped_column(String, primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    refresh_hash: Mapped[str] = mapped_column(String, unique=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime)
    # Moves forward on every refresh, so a phone in use stays signed in.
    expires_at: Mapped[datetime] = mapped_column(UtcDateTime)


class SpentRefreshToken(Base):
    """A refresh token a session has already exchanged. One that comes back may have been copied, so it ends its
    session. Kept for one session lifetime, after which the token would have expired anyway."""

    __tablename__ = "spent_refresh_tokens"

    token_hash: Mapped[str] = mapped_column(String, primary_key=True)
    session_id: Mapped[str] = mapped_column(ForeignKey("sessions.id", ondelete="CASCADE"), index=True)
    spent_at: Mapped[datetime] = mapped_column(UtcDateTime)


class Device(Base):
    """A phone's Firebase token, for pushes to that phone. A session has at most one."""

    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String, primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    # The sign-in that registered it; signing out there, or the session ending, deletes it.
    session_id: Mapped[str] = mapped_column(ForeignKey("sessions.id", ondelete="CASCADE"), unique=True)
    fcm_token: Mapped[str] = mapped_column(String, unique=True)
    platform: Mapped[str] = mapped_column(String)
    updated_at: Mapped[datetime] = mapped_column(UtcDateTime)


class FriendRequest(Base):
    """A pending request; it is deleted when it is answered or withdrawn. At most one exists between two users, in
    either direction: `user_a` and `user_b` are the pair with the smaller id first, and they are unique."""

    __tablename__ = "friend_requests"
    __table_args__ = (
        UniqueConstraint("user_a", "user_b"),
        Index("ix_friend_requests_to_user_id", "to_user_id"),
    )

    id: Mapped[str] = mapped_column(String, primary_key=True)
    from_user_id: Mapped[str] = mapped_column(ForeignKey("users.id"))
    to_user_id: Mapped[str] = mapped_column(ForeignKey("users.id"))
    user_a: Mapped[str] = mapped_column(String)
    user_b: Mapped[str] = mapped_column(String)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime)


class Friendship(Base):
    """One row per pair, with the smaller user id in `user_a`."""

    __tablename__ = "friendships"
    __table_args__ = (Index("ix_friendships_user_b", "user_b"),)

    user_a: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    user_b: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime)
