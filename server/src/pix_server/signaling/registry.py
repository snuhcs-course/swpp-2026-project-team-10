# AI-generated with Claude Code, 2026-10-02, reviewed by Jaewan Park
"""In-memory room state, owned by one asyncio event loop.

All transitions and outbound enqueues are synchronous: a join cannot interleave
with another join, and notifications are queued before subsequent signals.
Socket I/O is handled separately so a slow phone cannot block other sessions.
"""

import asyncio
import json
import secrets
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from uuid import uuid4

from pix_server.config import Settings
from pix_server.signaling.messages import ClientMessage, Leave, ProtocolError, RoomCreate, RoomJoin, Signal


@dataclass(eq=False)
class Peer:
    outbox: asyncio.Queue[str]
    closing: asyncio.Event = field(default_factory=asyncio.Event)
    close_code: int = 1000
    session_id: str | None = None


@dataclass
class Session:
    id: str
    code: str
    photographer: Peer | None
    expires_at: float | None
    subject: Peer | None = None
    empty_since: float | None = None


class SessionRegistry:
    def __init__(self, settings: Settings, clock: Callable[[], float] = time.monotonic) -> None:
        self.settings = settings
        self.clock = clock
        self._peers: set[Peer] = set()
        self._sessions: dict[str, Session] = {}
        self._codes: dict[str, str] = {}
        self._expired_codes: dict[str, float] = {}

    @property
    def session_count(self) -> int:
        return len(self._sessions)

    def connect(self) -> Peer:
        peer = Peer(outbox=asyncio.Queue(maxsize=self.settings.outbound_queue_size))
        self._peers.add(peer)
        return peer

    def send(self, peer: Peer, message: dict | str) -> None:
        if peer.closing.is_set():
            return
        text = message if isinstance(message, str) else json.dumps(message, separators=(",", ":"))
        try:
            peer.outbox.put_nowait(text)
        except asyncio.QueueFull:
            # The socket handler observes this event and disconnects this peer.
            peer.close_code = 1013
            peer.closing.set()

    def error(self, peer: Peer, error: ProtocolError) -> None:
        self.send(peer, {"type": "error", "code": error.code, "message": error.message})

    def handle(self, peer: Peer, message: ClientMessage, raw_text: str) -> None:
        self.expire()
        if peer.closing.is_set():
            return
        if isinstance(message, RoomCreate):
            self._create(peer)
        elif isinstance(message, RoomJoin):
            self._join(peer, message.code)
        elif isinstance(message, Signal):
            self._relay(peer, message, raw_text)
        elif isinstance(message, Leave):
            self._leave(peer)

    def _require_idle(self, peer: Peer) -> None:
        if peer.session_id is not None:
            raise ProtocolError("INVALID_STATE", "Leave the current session before creating or joining another")

    def _create(self, peer: Peer) -> None:
        self._require_idle(peer)
        for _ in range(100):
            code = f"{secrets.randbelow(1_000_000):06d}"
            if code not in self._codes and code not in self._expired_codes:
                break
        else:
            raise ProtocolError("FULL", "No room code available; try again")
        session = Session(
            id=f"s_{uuid4().hex}",
            code=code,
            photographer=peer,
            expires_at=self.clock() + self.settings.room_ttl_seconds,
        )
        self._sessions[session.id] = session
        self._codes[code] = session.id
        peer.session_id = session.id
        self.send(peer, {"type": "room.created", "code": code, "sessionId": session.id, "iceServers": []})

    def _join(self, peer: Peer, code: str) -> None:
        self._require_idle(peer)
        if code in self._expired_codes:
            raise ProtocolError("EXPIRED", "This room code has expired")
        session = self._sessions.get(self._codes.get(code, ""))
        if session is None or session.photographer is None or session.photographer.closing.is_set():
            raise ProtocolError("NOT_FOUND", "No active room with this code")
        if session.subject is not None:
            raise ProtocolError("FULL", "This room already has a subject")
        session.subject = peer
        session.expires_at = None
        peer.session_id = session.id
        self.send(peer, {"type": "session.joined", "sessionId": session.id, "role": "SUBJECT", "iceServers": []})
        self.send(session.photographer, {"type": "peer.joined"})

    def _relay(self, peer: Peer, message: Signal, raw_text: str) -> None:
        session = self._sessions.get(peer.session_id or "")
        if session is None:
            raise ProtocolError("INVALID_STATE", "Create or join a room before signaling")
        is_photographer = session.photographer is peer
        if message.sdp is not None:
            expected = "offer" if is_photographer else "answer"
            if message.sdp.type != expected:
                raise ProtocolError("INVALID_STATE", f"This peer may only send an SDP {expected}")
        other = session.subject if is_photographer else session.photographer
        if other is None or other.closing.is_set():
            raise ProtocolError("PEER_UNAVAILABLE", "The other phone is not connected")
        self.send(other, raw_text)

    def _leave(self, peer: Peer) -> None:
        session = self._sessions.get(peer.session_id or "")
        if session is None:
            return
        if session.photographer is peer:
            other = session.subject
            self._remove(session)
        else:
            other = session.photographer
            session.subject = None
            peer.session_id = None
            if other is None:
                session.empty_since = self.clock()
        if other is not None:
            self.send(other, {"type": "peer.left", "reason": "PEER_LEFT"})

    def disconnect(self, peer: Peer) -> None:
        self._peers.discard(peer)
        peer.closing.set()
        session = self._sessions.get(peer.session_id or "")
        peer.session_id = None
        if session is None:
            return
        if session.photographer is peer:
            session.photographer = None
            other = session.subject
        else:
            session.subject = None
            other = session.photographer
        if other is not None:
            self.send(other, {"type": "peer.left", "reason": "CONNECTION_LOST"})
        else:
            session.empty_since = self.clock()

    def _remove(self, session: Session) -> None:
        self._sessions.pop(session.id)
        self._codes.pop(session.code)
        for peer in (session.photographer, session.subject):
            if peer is not None:
                peer.session_id = None

    def expire(self) -> None:
        now = self.clock()
        for code, forget_at in list(self._expired_codes.items()):
            if now >= forget_at:
                del self._expired_codes[code]
        for session in list(self._sessions.values()):
            if session.expires_at is not None and now >= session.expires_at:
                self._remove(session)
                # Remember recent expirations without retaining sockets/sessions.
                self._expired_codes[session.code] = now + self.settings.room_ttl_seconds
                if session.photographer is not None:
                    self.error(session.photographer, ProtocolError("EXPIRED", "This room code has expired"))
            elif (
                session.empty_since is not None and now >= session.empty_since + self.settings.empty_session_ttl_seconds
            ):
                self._remove(session)

    def shutdown(self) -> None:
        for peer in self._peers:
            peer.session_id = None
            peer.close_code = 1001
            peer.closing.set()
        self._peers.clear()
        self._sessions.clear()
        self._codes.clear()
        self._expired_codes.clear()
