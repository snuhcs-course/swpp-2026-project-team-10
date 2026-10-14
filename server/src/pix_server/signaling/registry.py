# AI-generated with Claude Code, 2026-10-14, reviewed by Jaewan Park
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
from contextlib import suppress
from dataclasses import dataclass, field
from uuid import uuid4

from redis.exceptions import RedisError

from pix_server.config import Settings
from pix_server.signaling.messages import ClientMessage, Leave, ProtocolError, RoomCreate, RoomJoin, Signal
from pix_server.store import Delivery, SessionStore

logger = logging.getLogger(__name__)

EXPIRED = ProtocolError("EXPIRED", "This room code has expired")
JOIN_ERRORS = {
    "INVALID_STATE": "Leave the current session before creating or joining another",
    "EXPIRED": EXPIRED.message,
    "NOT_FOUND": "No active room with this code",
    "FULL": "This room already has a subject",
}


@dataclass(eq=False)
class Peer:
    id: str
    outbox: asyncio.Queue[str]
    closing: asyncio.Event = field(default_factory=asyncio.Event)
    close_code: int = 1000


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


class SessionRegistry:
    def __init__(self, settings: Settings, store: SessionStore) -> None:
        self.settings = settings
        self.store = store
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

    async def _listen(self) -> None:
        """Passes on what other workers publish for this worker's sockets."""
        while not self._stopping:
            try:
                received = await self.store.receive()
            except RedisError:
                if not self._stopping:
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
                await self._deliver(await self.store.beat())
                for connection in list(self._unreleased):
                    await self._deliver(await self.store.disconnect(connection))
                    self._unreleased.discard(connection)
            except RedisError as exc:
                if self._store_reachable:
                    logger.error("The session store is unreachable: %s", type(exc).__name__)
                self._store_reachable = False
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

    async def connect(self) -> Peer:
        peer = Peer(id=f"c_{uuid4().hex}", outbox=asyncio.Queue(maxsize=self.settings.outbound_queue_size))
        self._peers[peer.id] = peer
        try:
            await self._deliver(await self.store.connect(peer.id))
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
                self.send(peer, {"type": "room.created", "code": code, "sessionId": session_id, "iceServers": []})
                return
            if status == "INVALID_STATE":
                raise ProtocolError(status, JOIN_ERRORS[status])
        raise ProtocolError("FULL", "No room code available; try again")

    async def _join(self, peer: Peer, code: str) -> None:
        status, session_id, deliveries = await self.store.join(peer.id, code)
        if status == "OK":
            self.send(peer, {"type": "session.joined", "sessionId": session_id, "role": "SUBJECT", "iceServers": []})
        # The photographer's peer.joined is among these, and it must be on its way before this socket's next signal.
        await self._deliver(deliveries)
        if status != "OK":
            raise ProtocolError(status, JOIN_ERRORS[status])

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
