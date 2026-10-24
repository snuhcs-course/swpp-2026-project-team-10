# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Password hashing off the event loop, a few at a time (Design 2.8, Accounts).

Partitions: hashes and checks requested together beyond the limit; a right password, a wrong one, and no stored
hash (the stand-in).
"""

import asyncio
import threading
import time

from argon2 import PasswordHasher

from pix_server.accounts.passwords import Passwords


class CountingHasher:
    """Stands in for argon2: each call takes a moment and records how many run at once."""

    def __init__(self) -> None:
        self.running = 0
        self.most = 0
        self._lock = threading.Lock()

    def _work(self) -> None:
        with self._lock:
            self.running += 1
            self.most = max(self.most, self.running)
        time.sleep(0.02)
        with self._lock:
            self.running -= 1

    def hash(self, password: str) -> str:
        self._work()
        return f"hashed:{password}"

    def verify(self, stored: str, password: str) -> bool:
        self._work()
        return True


async def test_a_burst_of_hashes_and_checks_runs_two_at_a_time():
    hasher = CountingHasher()
    passwords = Passwords(hasher, at_once=2)
    hashes = [passwords.hash(f"password{n}") for n in range(4)]
    checks = [passwords.verify("hashed:x", "x") for _ in range(4)]
    results = await asyncio.gather(*hashes, *checks)
    assert results[:4] == [f"hashed:password{n}" for n in range(4)]
    assert results[4:] == [True] * 4
    assert hasher.most == 2


async def test_check_is_true_only_for_the_right_password_of_a_stored_hash():
    passwords = Passwords(PasswordHasher(time_cost=1, memory_cost=8, parallelism=1))
    stored = await passwords.hash("sunset2026")
    assert await passwords.verify(stored, "sunset2026")
    assert not await passwords.verify(stored, "sunset2027")
    # No stored hash: the stand-in is checked, so the answer takes as long, and it is always no.
    assert not await passwords.verify(None, "pix stand-in password")
