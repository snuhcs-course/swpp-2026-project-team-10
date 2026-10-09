# AI-generated with Claude Code, 2026-10-02, reviewed by Jaewan Park
import asyncio
import json

import pytest

from pix_server.signaling.messages import ProtocolError, parse_message
from pix_server.signaling.registry import SessionRegistry
from pix_server.signaling.router import send_messages


def handle(registry, peer, **message):
    text = json.dumps(message)
    registry.handle(peer, parse_message(text), text)


def create_pair(registry):
    photographer, subject = registry.connect(), registry.connect()
    handle(registry, photographer, type="room.create")
    room = json.loads(photographer.outbox.get_nowait())
    handle(registry, subject, type="room.join", code=room["code"])
    photographer.outbox.get_nowait()
    subject.outbox.get_nowait()
    return photographer, subject, room


def test_empty_session_cleanup_starts_at_the_last_disconnect(settings, clock):
    registry = SessionRegistry(settings, clock)
    photographer, subject, _ = create_pair(registry)
    registry.disconnect(photographer)
    clock.advance(120)
    registry.expire()
    assert registry.session_count == 1  # The subject still occupies it.
    registry.disconnect(subject)
    clock.advance(59)
    registry.expire()
    assert registry.session_count == 1
    registry.disconnect(subject)  # Repeated disconnect must not reset the timer.
    clock.advance(1)
    registry.expire()
    assert registry.session_count == 0


def test_expiry_is_enforced_at_join_even_before_the_sweeper_runs(settings, clock):
    registry = SessionRegistry(settings, clock)
    photographer, subject = registry.connect(), registry.connect()
    handle(registry, photographer, type="room.create")
    room = json.loads(photographer.outbox.get_nowait())
    clock.advance(600)
    with pytest.raises(ProtocolError) as exc:
        handle(registry, subject, type="room.join", code=room["code"])
    assert exc.value.code == "EXPIRED"
    assert registry.session_count == 0
    assert photographer.session_id is None
    assert subject.session_id is None
    # Expired-code tombstones are bounded in time, too.
    clock.advance(600)
    with pytest.raises(ProtocolError) as exc:
        handle(registry, subject, type="room.join", code=room["code"])
    assert exc.value.code == "NOT_FOUND"


def test_abandoned_waiting_room_is_removed_after_sixty_seconds(settings, clock):
    registry = SessionRegistry(settings, clock)
    photographer = registry.connect()
    handle(registry, photographer, type="room.create")
    registry.disconnect(photographer)
    clock.advance(60)
    registry.expire()
    assert registry.session_count == 0


def test_slow_consumer_is_bounded_and_does_not_block_other_rooms(settings, clock):
    settings.outbound_queue_size = 1
    registry = SessionRegistry(settings, clock)
    photographer, subject, _ = create_pair(registry)
    registry.send(subject, "first")
    registry.send(subject, "overflow")
    assert subject.closing.is_set()
    assert subject.close_code == 1013
    assert subject.outbox.qsize() == 1
    other_photographer, other_subject, _ = create_pair(registry)
    signal = {"type": "signal", "candidate": {"sdpMLineIndex": 0, "candidate": "ice"}}
    handle(registry, other_photographer, **signal)
    assert json.loads(other_subject.outbox.get_nowait()) == signal
    registry.disconnect(subject)
    assert json.loads(photographer.outbox.get_nowait()) == {"type": "peer.left", "reason": "CONNECTION_LOST"}


async def test_stalled_socket_send_times_out(settings, clock):
    settings.send_timeout_seconds = 0.01
    registry = SessionRegistry(settings, clock)
    peer = registry.connect()
    registry.send(peer, {"type": "peer.joined"})

    class StalledSocket:
        async def send_text(self, text):
            await asyncio.Event().wait()

    await asyncio.wait_for(send_messages(StalledSocket(), registry, peer), timeout=1)
    assert peer.close_code == 1013


def test_shutdown_releases_state_and_requests_socket_closure(settings, clock):
    registry = SessionRegistry(settings, clock)
    photographer, subject, _ = create_pair(registry)
    registry.shutdown()
    assert registry.session_count == 0
    for peer in (photographer, subject):
        assert peer.session_id is None
        assert peer.closing.is_set()
        assert peer.close_code == 1001
        registry.disconnect(peer)  # Socket finalizers are safe after shutdown.
