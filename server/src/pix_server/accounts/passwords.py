# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Password hashing with argon2id (Design Documentation 2.8, Accounts). Only the hash is stored."""

import asyncio

from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

# Hashes one worker runs at once. Each argon2id hash holds 64 MiB and four threads by default, and the default thread
# pool would run up to 32 of them, so a burst of sign-ins, which needs no account, queues here instead.
HASHES_AT_ONCE = 2


class Passwords:
    """Hashes off the event loop: one argon2id hash takes tens of milliseconds of CPU, and the socket handlers of
    the same worker must not wait for it."""

    def __init__(self, hasher: PasswordHasher, at_once: int = HASHES_AT_ONCE) -> None:
        self._hasher = hasher
        self._slots = asyncio.Semaphore(at_once)
        # Checked when a Pix ID has no password, so that answer takes as long as a wrong password and does not
        # tell which Pix IDs exist.
        self._stand_in = hasher.hash("pix stand-in password")

    async def hash(self, password: str) -> str:
        async with self._slots:
            return await asyncio.to_thread(self._hasher.hash, password)

    async def verify(self, stored: str | None, password: str) -> bool:
        try:
            async with self._slots:
                await asyncio.to_thread(self._hasher.verify, stored or self._stand_in, password)
        except (VerificationError, InvalidHashError):
            return False
        return stored is not None
