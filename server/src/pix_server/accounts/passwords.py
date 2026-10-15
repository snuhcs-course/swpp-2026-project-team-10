# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Password hashing with argon2id (Design Documentation 2.8, Accounts). Only the hash is stored."""

import asyncio

from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError


class Passwords:
    """Hashes off the event loop: one argon2id hash takes tens of milliseconds of CPU, and the socket handlers of
    the same worker must not wait for it."""

    def __init__(self, hasher: PasswordHasher) -> None:
        self._hasher = hasher
        # Checked when a Pix ID has no password, so that answer takes as long as a wrong password and does not
        # tell which Pix IDs exist.
        self._stand_in = hasher.hash("pix stand-in password")

    async def hash(self, password: str) -> str:
        return await asyncio.to_thread(self._hasher.hash, password)

    async def verify(self, stored: str | None, password: str) -> bool:
        try:
            await asyncio.to_thread(self._hasher.verify, stored or self._stand_in, password)
        except (VerificationError, InvalidHashError):
            return False
        return stored is not None
