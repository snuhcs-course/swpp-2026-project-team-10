# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""Session state shared by every server process, kept in Redis (Design Documentation 2.7, AD-18).

Each server process is one worker with its own id. The store runs the room transitions as scripts, carries
messages to sockets held by other workers over pub/sub, and keeps the worker's heartbeat. It knows nothing
about WebSockets or message formats; `SessionRegistry` does.
"""

import asyncio
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from uuid import uuid4

from redis.asyncio import Redis

from pix_server.config import Settings
from pix_server.store import scripts

logger = logging.getLogger(__name__)

SWEEPER_LOCK = "sweeper"
# How long one wait for a pub/sub message lasts. It bounds the read, so no socket timeout cuts the subscription.
RECEIVE_POLL_SECONDS = 1.0


def open_redis(settings: Settings) -> Redis:
    """Connects to `PIX_REDIS_URL`, or keeps the state inside this process when it is not set.

    The in-process store is fakeredis, a development dependency: it lets a laptop run the server without
    installing Redis, with one worker only. The deployed image does not contain it, so a server without
    `PIX_REDIS_URL` refuses to start there instead of quietly keeping rooms in one process.
    """
    if settings.redis_url:
        return Redis.from_url(settings.redis_url, decode_responses=True, socket_connect_timeout=2, socket_timeout=5)
    try:
        import fakeredis
    except ImportError as exc:
        raise RuntimeError("PIX_REDIS_URL is not set, and the in-process store (fakeredis) is not installed") from exc
    logger.warning("PIX_REDIS_URL is not set: sessions stay inside this process, so run one worker only")
    return fakeredis.FakeAsyncRedis(decode_responses=True)


@dataclass(frozen=True)
class Window:
    """A sliding window: at most `limit` events within `seconds`, counted under `key`."""

    key: str
    limit: int
    seconds: float


@dataclass(frozen=True)
class Delivery:
    """A message a script decided on, for a connection held by `worker`."""

    connection: str
    worker: str
    kind: str
    detail: str


def _deliveries(rows: list[list[str]]) -> list[Delivery]:
    return [Delivery(*row) for row in rows]


class SessionStore:
    def __init__(self, redis: Redis, settings: Settings, clock: Callable[[], float] = time.time) -> None:
        self.redis = redis
        self.settings = settings
        self.clock = clock
        self.worker = f"w_{uuid4().hex}"
        self._channel = f"worker:{self.worker}"
        self._pubsub = None
        self._scripts = {
            name: redis.register_script(getattr(scripts, name))
            for name in (
                "BEAT",
                "CONNECT",
                "CREATE",
                "JOIN",
                "REJOIN",
                "INVITE",
                "ACCEPT_INVITATION",
                "END_INVITATION",
                "ROUTE",
                "LEAVE",
                "DISCONNECT",
                "RELEASE",
                "STATS",
            )
        }
        self._register = redis.register_script(scripts.REGISTER)
        self._rate_limit = redis.register_script(scripts.RATE_LIMIT)
        self._presence = redis.register_script(scripts.PRESENCE)

    def _heartbeat_ms(self) -> str:
        return str(max(1, round(self.settings.worker_ttl_seconds * 1000)))

    async def _run(self, name: str, *args: str) -> list:
        """Runs a session script. It raises a `ResponseError` starting with WORKER_LOST if this worker has lost its
        state in the store; the worker then has to close its sockets and call `register` again."""
        common = [
            f"{self.clock():.6f}",
            f"{self.settings.room_ttl_seconds:.6f}",
            f"{self.settings.empty_session_ttl_seconds:.6f}",
            self.worker,
            self._heartbeat_ms(),
        ]
        return await self._scripts[name](args=[*common, *args])

    # --- worker ----------------------------------------------------------------------------------------------------

    async def start(self) -> None:
        """Subscribes to this worker's channel and waits for Redis to confirm, so no message is published too early."""
        self._pubsub = self.redis.pubsub()
        await self._pubsub.subscribe(self._channel)
        async with asyncio.timeout(5):
            while True:
                message = await self._pubsub.get_message(timeout=RECEIVE_POLL_SECONDS)
                if message is not None and message["type"] == "subscribe":
                    break
        await self.register()

    async def register(self) -> None:
        """Adds this worker to the store, with no connections, so the session scripts run for it."""
        await self._register(args=[self.worker, self._heartbeat_ms()])

    async def beat(self) -> list[Delivery]:
        """Renews the heartbeat. The worker that gets the lock for this interval also sweeps."""
        interval_ms = max(1, round(self.settings.cleanup_interval_seconds * 1000))
        holds_lock = await self.redis.set(SWEEPER_LOCK, self.worker, nx=True, px=interval_ms)
        return _deliveries(await self._run("BEAT", "sweep" if holds_lock else ""))

    async def sweep(self) -> list[Delivery]:
        """Expires rooms, removes empty sessions, and drops the sockets of workers without a heartbeat."""
        return _deliveries(await self._run("BEAT", "sweep"))

    async def release(self) -> list[Delivery]:
        """Gives up every connection of this worker; called when it stops."""
        return _deliveries(await self._run("RELEASE"))

    async def close(self) -> None:
        if self._pubsub is not None:
            await self._pubsub.aclose()
            self._pubsub = None

    async def ping(self) -> None:
        await self.redis.ping()

    # --- room transitions ------------------------------------------------------------------------------------------

    async def connect(self, connection: str, user: str | None = None) -> list[Delivery]:
        """Registers a socket; one with a signed-in `user` makes that user online (FR-5.9)."""
        return _deliveries(await self._run("CONNECT", connection, user or ""))

    async def create(self, connection: str, session_id: str, code: str, token_hash: str) -> tuple[str, list[Delivery]]:
        """Opens a room; `token_hash` is the hash of the photographer's rejoin token."""
        status, out = await self._run("CREATE", connection, session_id, code, token_hash)
        return status, _deliveries(out)

    async def join(
        self, connection: str, code: str, token_hash: str, failures: Window | None = None
    ) -> tuple[str, str, list[Delivery]]:
        """Seats a subject, whose rejoin token has the hash `token_hash`. A wrong code counts in `failures`, and while
        that window is full every join answers RATE_LIMITED."""
        limits = [failures.key, str(failures.limit), f"{failures.seconds:.6f}"] if failures else ["", "0", "0"]
        status, session_id, out = await self._run("JOIN", connection, code, *limits, uuid4().hex, token_hash)
        return status, session_id, _deliveries(out)

    async def rejoin(
        self, connection: str, session_id: str, token_hash: str, keep: bool
    ) -> tuple[str, str, list[Delivery]]:
        """Gives the place whose rejoin token has the hash `token_hash` to `connection`: `(status, role, deliveries)`.
        `keep` says the phone keeps its peer connection, so the other phone hears `peer.rejoined`, not `peer.joined`."""
        status, role, out = await self._run("REJOIN", connection, session_id, token_hash, "1" if keep else "0")
        return status, role, _deliveries(out)

    async def invite(self, session_id: str, token_hash: str, invitation: str, expires_at: float) -> list[Delivery]:
        """Opens an invitation's room, with nobody in it and no code; `token_hash` is the hash of the photographer's
        rejoin token. It expires at `expires_at` unless the friend has taken its place."""
        return _deliveries(await self._run("INVITE", session_id, token_hash, invitation, f"{expires_at:.6f}"))

    async def accept_invitation(self, session_id: str, token_hash: str, deadline: float) -> tuple[bool, list[Delivery]]:
        """Opens the subject's place of an invitation's room to the rejoin token with the hash `token_hash`, to be taken
        by `deadline`. False when the room has ended or has a subject already."""
        status, out = await self._run("ACCEPT_INVITATION", session_id, token_hash, f"{deadline:.6f}")
        return status == "OK", _deliveries(out)

    async def end_invitation(self, session_id: str, notice: str = "") -> list[Delivery]:
        """Removes the room of an invitation nobody accepted; the photographer hears the error `notice`, if any."""
        return _deliveries(await self._run("END_INVITATION", session_id, notice))

    async def seated(self, session_id: str, connection: str) -> bool:
        """Whether `connection` still holds the subject's place in the session and its photographer is still there.
        One read of one hash, so it changes nothing and needs no script."""
        photographer, subject = await self.redis.hmget(f"session:{session_id}", "photographer", "subject")
        return bool(photographer) and subject == connection

    async def route(self, connection: str, sdp_type: str) -> tuple[str, str, str, str, list[Delivery]]:
        """Finds where a signal goes: `(status, expected SDP type, connection, worker, deliveries)`."""
        status, expected, target, worker, out = await self._run("ROUTE", connection, sdp_type)
        return status, expected, target, worker, _deliveries(out)

    async def leave(self, connection: str) -> list[Delivery]:
        return _deliveries(await self._run("LEAVE", connection))

    async def disconnect(self, connection: str) -> list[Delivery]:
        return _deliveries(await self._run("DISCONNECT", connection))

    async def stats(self) -> tuple[int, int]:
        sessions, connections = await self._run("STATS")
        return int(sessions), int(connections)

    # --- delivery between workers ----------------------------------------------------------------------------------

    async def publish(self, worker: str, connection: str, text: str) -> None:
        await self.redis.publish(f"worker:{worker}", f"{connection}\n{text}")

    async def receive(self) -> tuple[str, str] | None:
        """Waits a moment for a message another worker published for one of this worker's connections."""
        message = await self._pubsub.get_message(ignore_subscribe_messages=True, timeout=RECEIVE_POLL_SECONDS)
        if message is None or message["type"] != "message":
            return None
        connection, _, text = message["data"].partition("\n")
        return connection, text

    # --- presence --------------------------------------------------------------------------------------------------

    async def presence(self, users: list[str]) -> dict[str, tuple[bool, bool]]:
        """`(online, in a session)` for each user."""
        if not users:
            return {}
        flags = await self._presence(args=users)
        return {user: (bool(online), bool(busy)) for user, (online, busy) in zip(users, flags, strict=True)}

    # --- rate limit ------------------------------------------------------------------------------------------------

    async def allow(self, *windows: Window) -> Window | None:
        """Counts one request in every window, or in none if one of them is full, and returns that one. Shared by
        every worker."""
        sizes = [value for window in windows for value in (f"{window.seconds:.6f}", str(window.limit))]
        full = await self._rate_limit(
            keys=[window.key for window in windows], args=[f"{self.clock():.6f}", uuid4().hex, *sizes]
        )
        return windows[full - 1] if full else None
