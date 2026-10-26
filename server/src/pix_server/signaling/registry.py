# AI-generated with Claude Code, 2026-10-26, reviewed by Sungmin Jo
"""The sockets of one server process, and the room protocol on top of the session store.

Room state lives in Redis (Design Documentation 2.7), so a room created through one worker can be joined
through another. A transition is one script in the store, so two joins cannot interleave. This class keeps
only what belongs to the process: each socket's bounded queue, so a slow phone cannot block other sessions.
A message for a socket of this process goes straight into its queue; one for another worker's socket is
published to that worker.
"""

import asyncio
import json
import logging
import secrets
from collections.abc import Awaitable, Callable
from contextlib import suppress
from dataclasses import dataclass, field
from uuid import uuid4

from redis.exceptions import RedisError

from pix_server.config import Settings
from pix_server.signaling.messages import ClientMessage, Leave, ProtocolError, RoomCreate, RoomJoin, Signal
from pix_server.store import Delivery, SessionStore, Window
from pix_server.turn import stun_only

logger = logging.getLogger(__name__)

EXPIRED = ProtocolError("EXPIRED", "This room code has expired")
JOIN_ERRORS = {
    "INVALID_STATE": "Leave the current session before creating or joining another",
    "EXPIRED": EXPIRED.message,
    "NOT_FOUND": "No active room with this code",
    "FULL": "This room already has a subject",
    "RATE_LIMITED": "Too many wrong codes from this network; wait a few minutes and try again",
}


@dataclass(eq=False)
class Peer:
    id: str
    outbox: asyncio.Queue[str]
    closing: asyncio.Event = field(default_factory=asyncio.Event)
    close_code: int = 1000
    # The client's address, which wrong room codes count against; empty when it is not known.
    address: str = ""


def encode(message: dict) -> str:
    return json.dumps(message, separators=(",", ":"))


def render(delivery: Delivery) -> str:
    """The JSON text of a message a store script decided on."""
    if delivery.kind == "peer.joined":
        return encode({"type": "peer.joined"})
    if delivery.kind == "peer.left":
        return encode({"type": "peer.left", "reason": delivery.detail})
    if delivery.kind == "error" and delivery.detail == EXPIRED.code:
        return encode({"type": "error", "code": EXPIRED.code, "message": EXPIRED.message})
    raise ValueError(f"Unknown delivery: {delivery.kind} {delivery.detail}")


async def stun_servers() -> list[dict]:
    return stun_only()


class SessionRegistry:
    def __init__(
        self,
        settings: Settings,
        store: SessionStore,
        ice_servers: Callable[[], Awaitable[list[dict]]] = stun_servers,
    ) -> None:
        """`ice_servers` gives the `iceServers` for one phone; each phone of a session asks once, when it gets its
        place (Design 2.5.2, AD-14)."""
        self.settings = settings
        self.store = store
        self.ice_servers = ice_servers
        self._peers: dict[str, Peer] = {}
        # Connections whose disconnect did not reach Redis; the housekeeping loop tries again.
        self._unreleased: set[str] = set()
        self._tasks: list[asyncio.Task] = []
        self._releases: set[asyncio.Task] = set()
        self._stopping = False
        self._store_reachable = True

    # --- lifecycle -------------------------------------------------------------------------------------------------

    async def start(self) -> None:
        await self.store.start()
        self._tasks = [asyncio.create_task(self._listen()), asyncio.create_task(self._keep_house())]

    async def stop(self) -> None:
        # The flag ends the loops even if a cancellation that lands inside a Redis call surfaces as a Redis error.
        self._stopping = True
        for task in self._tasks:
            task.cancel()
        await asyncio.gather(*self._tasks, *self._releases, return_exceptions=True)
        self._tasks = []
        for peer in self._peers.values():
            peer.close_code = 1001
            peer.closing.set()
        self._peers.clear()
        # Without this the other workers would learn about these sockets only when the heartbeat runs out.
        with suppress(RedisError):
            await self._deliver(await self.store.release())
        with suppress(RedisError):
            await self.store.close()

    def _lose_store(self, exc: RedisError) -> None:
        """The store failed, or this worker lost its state there. Messages for its sockets may be gone without a trace,
        so a waiting room could stall with a code that no longer works; every socket is closed with 1013 instead.
        A live session goes on phone to phone, and a phone in a waiting room shows an error and can start again."""
        if self._store_reachable:
            logger.error("The session store failed (%s); closing this worker's sockets", exc)
            for peer in self._peers.values():
                peer.close_code = 1013
                peer.closing.set()
        self._store_reachable = False

    async def _listen(self) -> None:
        """Passes on what other workers publish for this worker's sockets."""
        while not self._stopping:
            try:
                received = await self.store.receive()
            except RedisError as exc:
                if not self._stopping:
                    # The subscription broke, so messages published meanwhile are lost.
                    self._lose_store(exc)
                    await asyncio.sleep(self.settings.cleanup_interval_seconds)
                continue
            if received is not None:
                peer = self._peers.get(received[0])
                if peer is not None:
                    self.send(peer, received[1])

    async def _keep_house(self) -> None:
        while not self._stopping:
            await asyncio.sleep(self.settings.cleanup_interval_seconds)
            try:
                if not self._store_reachable:
                    # Its sockets are closed, so the worker can start clean.
                    await self.store.register()
                await self._deliver(await self.store.beat())
                for connection in list(self._unreleased):
                    await self._deliver(await self.store.disconnect(connection))
                    self._unreleased.discard(connection)
            except RedisError as exc:
                if not self._stopping:
                    self._lose_store(exc)
            else:
                if not self._store_reachable:
                    logger.info("The session store is reachable again")
                self._store_reachable = True

    async def sweep(self) -> None:
        """One pass of the sweeper, whoever holds the lock. The housekeeping loop does this every interval."""
        await self._deliver(await self.store.sweep())

    async def stats(self) -> tuple[int, int]:
        """Open sessions and open connections, over all workers."""
        return await self.store.stats()

    # --- sockets ---------------------------------------------------------------------------------------------------

    async def connect(self, user: str | None = None, address: str = "") -> Peer:
        """A socket of a signed-in `user` makes that user online until it closes."""
        peer = Peer(
            id=f"c_{uuid4().hex}", outbox=asyncio.Queue(maxsize=self.settings.outbound_queue_size), address=address
        )
        self._peers[peer.id] = peer
        try:
            await self._deliver(await self.store.connect(peer.id, user))
        except RedisError:
            del self._peers[peer.id]
            raise
        return peer

    async def disconnect(self, peer: Peer) -> None:
        peer.closing.set()
        if self._peers.pop(peer.id, None) is None:
            return
        # A socket handler that is being cancelled must still free its place in the store, so the store call runs
        # as a task of its own and the handler only waits for it.
        release = asyncio.create_task(self._release(peer.id))
        self._releases.add(release)
        release.add_done_callback(self._releases.discard)
        await asyncio.shield(release)

    async def _release(self, connection: str) -> None:
        try:
            await self._deliver(await self.store.disconnect(connection))
        except RedisError:
            self._unreleased.add(connection)

    def send(self, peer: Peer, message: dict | str) -> None:
        if peer.closing.is_set():
            return
        text = message if isinstance(message, str) else encode(message)
        try:
            peer.outbox.put_nowait(text)
        except asyncio.QueueFull:
            # The socket handler observes this event and disconnects this peer.
            peer.close_code = 1013
            peer.closing.set()

    def error(self, peer: Peer, error: ProtocolError) -> None:
        self.send(peer, {"type": "error", "code": error.code, "message": error.message})

    async def _send_to(self, connection: str, worker: str, text: str) -> None:
        if worker != self.store.worker:
            await self.store.publish(worker, connection, text)
            return
        peer = self._peers.get(connection)
        if peer is not None:
            self.send(peer, text)

    async def _deliver(self, deliveries: list[Delivery]) -> None:
        for delivery in deliveries:
            await self._send_to(delivery.connection, delivery.worker, render(delivery))

    # --- protocol --------------------------------------------------------------------------------------------------

    async def handle(self, peer: Peer, message: ClientMessage, raw_text: str) -> None:
        if peer.closing.is_set():
            return
        if isinstance(message, RoomCreate):
            await self._create(peer)
        elif isinstance(message, RoomJoin):
            await self._join(peer, message.code)
        elif isinstance(message, Signal):
            await self._relay(peer, message, raw_text)
        elif isinstance(message, Leave):
            await self._deliver(await self.store.leave(peer.id))

    async def _create(self, peer: Peer) -> None:
        session_id = f"s_{uuid4().hex}"
        for _ in range(100):
            code = f"{secrets.randbelow(1_000_000):06d}"
            status, deliveries = await self.store.create(peer.id, session_id, code)
            await self._deliver(deliveries)
            if status == "OK":
                ice_servers = await self.ice_servers()
                self.send(
                    peer, {"type": "room.created", "code": code, "sessionId": session_id, "iceServers": ice_servers}
                )
                return
            if status == "INVALID_STATE":
                raise ProtocolError(status, JOIN_ERRORS[status])
        raise ProtocolError("FULL", "No room code available; try again")

    async def _join(self, peer: Peer, code: str) -> None:
        failures = None
        if peer.address:
            failures = Window(
                f"rate:join:{peer.address}",
                self.settings.join_failure_limit,
                self.settings.join_failure_window_seconds,
            )
        status, session_id, deliveries = await self.store.join(peer.id, code, failures)
        if status != "OK":
            await self._deliver(deliveries)
            raise ProtocolError(status, JOIN_ERRORS[status])
        # The photographer's peer.joined waits for this socket's session.joined, which waits for its credentials;
        # what else the script decided, such as an expired room's notice, goes out now.
        joined = [delivery for delivery in deliveries if delivery.kind == "peer.joined"]
        await self._deliver([delivery for delivery in deliveries if delivery.kind != "peer.joined"])
        # Asked only once the place is taken, so wrong codes cost no credentials.
        ice_servers = await self.ice_servers()
        if not await self.store.seated(session_id, peer.id):
            # The photographer left meanwhile, and this socket has had its peer.left. A photographer that opened
            # another room on the same socket must not hear of this subject there.
            return
        self.send(
            peer, {"type": "session.joined", "sessionId": session_id, "role": "SUBJECT", "iceServers": ice_servers}
        )
        # It must be on its way before this socket's next signal.
        await self._deliver(joined)

    async def _relay(self, peer: Peer, message: Signal, raw_text: str) -> None:
        sdp_type = message.sdp.type if message.sdp is not None else ""
        status, expected, target, worker, deliveries = await self.store.route(peer.id, sdp_type)
        await self._deliver(deliveries)
        if status == "INVALID_STATE":
            raise ProtocolError(status, "Create or join a room before signaling")
        if status == "WRONG_SDP":
            raise ProtocolError("INVALID_STATE", f"This peer may only send an SDP {expected}")
        if status == "PEER_UNAVAILABLE":
            raise ProtocolError(status, "The other phone is not connected")
        await self._send_to(target, worker, raw_text)
