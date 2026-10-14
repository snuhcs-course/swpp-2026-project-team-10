# AI-generated with Claude Code, 2026-10-14, reviewed by Jaewan Park
"""The registry on the session store, without sockets. Each registry stands for one worker process."""

import asyncio
import json

import pytest
from redis.exceptions import RedisError

from pix_server.signaling.messages import ProtocolError, parse_message
from pix_server.signaling.router import send_messages

LEFT = {"type": "peer.left", "reason": "CONNECTION_LOST"}


async def handle(registry, peer, **message):
    text = json.dumps(message)
    await registry.handle(peer, parse_message(text), text)


async def receive(peer):
    """The next message for this socket; one from another worker takes a moment to arrive."""
    return json.loads(await asyncio.wait_for(peer.outbox.get(), timeout=2))


async def create_room(registry):
    photographer = await registry.connect()
    await handle(registry, photographer, type="room.create")
    return photographer, await receive(photographer)


async def create_pair(first, second=None):
    """A joined room. With two registries the photographer is on the first worker and the subject on the second."""
    second = second or first
    photographer, room = await create_room(first)
    subject = await second.connect()
    await handle(second, subject, type="room.join", code=room["code"])
    assert (await receive(subject))["sessionId"] == room["sessionId"]
    assert await receive(photographer) == {"type": "peer.joined"}
    return photographer, subject, room


async def eventually(attempt, timeout=2):
    """Repeats `attempt` until it succeeds, while the worker recovers from a store failure in the background."""
    async with asyncio.timeout(timeout):
        while True:
            try:
                return await attempt()
            except RedisError:
                await asyncio.sleep(0.01)


async def closed_for_retry(peer):
    await asyncio.wait_for(peer.closing.wait(), timeout=2)
    assert peer.close_code == 1013


async def kill(registry, open_store):
    """The worker's process dies: nothing is released, and later its heartbeat runs out."""
    for task in registry._tasks:
        task.cancel()
    await asyncio.gather(*registry._tasks, return_exceptions=True)
    connection = open_store()
    await connection.delete(f"worker:{registry.store.worker}")
    await connection.aclose()


async def test_empty_session_cleanup_starts_at_the_last_disconnect(make_registry, clock):
    registry = await make_registry()
    photographer, subject, _ = await create_pair(registry)
    await registry.disconnect(photographer)
    clock.advance(120)
    await registry.sweep()
    assert await registry.stats() == (1, 1)  # The subject still occupies it.
    await registry.disconnect(subject)
    clock.advance(59)
    await registry.sweep()
    assert await registry.stats() == (1, 0)
    await registry.disconnect(subject)  # Repeated disconnect must not reset the timer.
    clock.advance(1)
    await registry.sweep()
    assert await registry.stats() == (0, 0)


async def test_expiry_is_enforced_at_join_even_before_the_sweeper_runs(make_registry, clock):
    registry = await make_registry()
    photographer, room = await create_room(registry)
    subject = await registry.connect()
    clock.advance(600)
    with pytest.raises(ProtocolError) as exc:
        await handle(registry, subject, type="room.join", code=room["code"])
    assert exc.value.code == "EXPIRED"
    assert (await receive(photographer))["code"] == "EXPIRED"
    assert (await registry.stats())[0] == 0
    # Both sockets are free again.
    await handle(registry, photographer, type="room.create")
    assert (await receive(photographer))["type"] == "room.created"
    # Expired-code tombstones are bounded in time, too.
    clock.advance(600)
    with pytest.raises(ProtocolError) as exc:
        await handle(registry, subject, type="room.join", code=room["code"])
    assert exc.value.code == "NOT_FOUND"


async def test_abandoned_waiting_room_is_removed_after_sixty_seconds(make_registry, clock):
    registry = await make_registry()
    photographer, _ = await create_room(registry)
    await registry.disconnect(photographer)
    clock.advance(60)
    await registry.sweep()
    assert await registry.stats() == (0, 0)


async def test_slow_consumer_is_bounded_and_does_not_block_other_rooms(make_registry, settings):
    settings.outbound_queue_size = 1
    registry = await make_registry()
    photographer, subject, _ = await create_pair(registry)
    registry.send(subject, "first")
    registry.send(subject, "overflow")
    assert subject.closing.is_set()
    assert subject.close_code == 1013
    assert subject.outbox.qsize() == 1
    other_photographer, other_subject, _ = await create_pair(registry)
    signal = {"type": "signal", "candidate": {"sdpMLineIndex": 0, "candidate": "ice"}}
    await handle(registry, other_photographer, **signal)
    assert await receive(other_subject) == signal
    await registry.disconnect(subject)
    assert await receive(photographer) == LEFT


async def test_stalled_socket_send_times_out(make_registry, settings):
    settings.send_timeout_seconds = 0.01
    registry = await make_registry()
    peer = await registry.connect()
    registry.send(peer, {"type": "peer.joined"})

    class StalledSocket:
        async def send_text(self, text):
            await asyncio.Event().wait()

    await asyncio.wait_for(send_messages(StalledSocket(), registry, peer), timeout=1)
    assert peer.close_code == 1013


async def test_stopping_a_worker_closes_its_sockets_and_keeps_the_room_for_sixty_seconds(make_registry, clock):
    stopped, other = await make_registry(), await make_registry()
    photographer, subject, _ = await create_pair(stopped)
    await stopped.stop()
    for peer in (photographer, subject):
        assert peer.closing.is_set()
        assert peer.close_code == 1001
        await stopped.disconnect(peer)  # Socket finalizers are safe after the stop.
    # The room stays in the store for a phone that comes back (Design 2.6.7), then goes like any empty session.
    assert await other.stats() == (1, 0)
    clock.advance(60)
    await other.sweep()
    assert await other.stats() == (0, 0)


async def test_room_created_through_one_worker_is_joined_through_another(make_registry):
    first, second = await make_registry(), await make_registry()
    photographer, room = await create_room(first)
    subject = await second.connect()
    answer = {"type": "signal", "sdp": {"type": "answer", "sdp": "v=0"}}
    # The subject signals right after joining; the photographer must still hear about the join first.
    await handle(second, subject, type="room.join", code=room["code"])
    await handle(second, subject, **answer)
    assert await receive(subject) == {
        "type": "session.joined",
        "sessionId": room["sessionId"],
        "role": "SUBJECT",
        "iceServers": [],
    }
    assert await receive(photographer) == {"type": "peer.joined"}
    assert await receive(photographer) == answer
    offer = {"type": "signal", "sdp": {"type": "offer", "sdp": "v=0"}}
    await handle(first, photographer, **offer)
    assert await receive(subject) == offer
    assert await first.stats() == await second.stats() == (1, 2)
    # Leaving and joining again also cross the workers.
    await handle(second, subject, type="leave")
    assert await receive(photographer) == {"type": "peer.left", "reason": "PEER_LEFT"}
    with pytest.raises(ProtocolError) as exc:
        await handle(first, photographer, **offer)
    assert exc.value.code == "PEER_UNAVAILABLE"
    await handle(second, subject, type="room.join", code=room["code"])
    assert await receive(photographer) == {"type": "peer.joined"}


async def test_two_workers_cannot_both_seat_a_subject(make_registry):
    first, second = await make_registry(), await make_registry()
    photographer, room = await create_room(first)
    subjects = [await first.connect(), await second.connect()]
    results = await asyncio.gather(
        handle(first, subjects[0], type="room.join", code=room["code"]),
        handle(second, subjects[1], type="room.join", code=room["code"]),
        return_exceptions=True,
    )
    refused = [result for result in results if isinstance(result, ProtocolError)]
    assert [error.code for error in refused] == ["FULL"]
    assert await receive(photographer) == {"type": "peer.joined"}
    assert photographer.outbox.empty()


async def test_worker_without_a_heartbeat_loses_its_sockets(make_registry, open_store):
    alive, dead = await make_registry(), await make_registry()
    photographer, _, room = await create_pair(alive, dead)
    await kill(dead, open_store)
    await alive.sweep()
    assert await receive(photographer) == LEFT
    assert await alive.stats() == (1, 1)
    # The subject's place is free again.
    replacement = await alive.connect()
    await handle(alive, replacement, type="room.join", code=room["code"])
    assert (await receive(replacement))["type"] == "session.joined"


async def test_one_worker_sweeps_per_interval_and_reaches_sockets_of_the_other(make_registry, clock):
    first, second = await make_registry(), await make_registry()
    photographer, _ = await create_room(first)
    assert await first.store.beat() == []  # Takes the sweeper lock for this interval.
    clock.advance(600)
    assert await second.store.beat() == []  # Renews its heartbeat only, so the room is still there.
    assert (await second.stats())[0] == 1
    await second.sweep()
    assert (await receive(photographer))["code"] == "EXPIRED"
    assert (await second.stats())[0] == 0


async def test_disconnect_during_a_store_outage_is_finished_afterwards(make_registry, settings, outage):
    registry = await make_registry()
    photographer, subject, _ = await create_pair(registry)
    outage(True)
    with pytest.raises(RedisError):
        await registry.connect()
    await registry.disconnect(subject)  # Does not raise; the housekeeping loop tries again.
    outage(False)
    assert photographer.outbox.empty()
    settings.cleanup_interval_seconds = 0.01
    restarted = asyncio.create_task(registry._keep_house())
    try:
        assert await receive(photographer) == LEFT
    finally:
        restarted.cancel()
    assert await registry.stats() == (1, 1)


async def test_a_store_outage_closes_every_socket_of_the_worker(make_registry, settings, outage):
    settings.cleanup_interval_seconds = 0.01
    registry = await make_registry()
    photographer, subject, _ = await create_pair(registry)
    waiting, _ = await create_room(registry)  # Sends nothing more, so no message of its own would fail.
    outage(True)
    for peer in (photographer, subject, waiting):
        await closed_for_retry(peer)
        await registry.disconnect(peer)  # What each socket handler does; the store is still down.
    outage(False)
    # Back again: the closed sockets are released from the store, and a new one gets a room.
    newcomer = await eventually(registry.connect)
    await handle(registry, newcomer, type="room.create")
    assert (await receive(newcomer))["type"] == "room.created"
    async with asyncio.timeout(2):
        while (await registry.stats())[1] != 1:
            await asyncio.sleep(0.01)


async def test_a_redis_restart_closes_the_sockets_and_the_worker_registers_again(
    make_registry, settings, restart_store
):
    settings.cleanup_interval_seconds = 0.01
    registry = await make_registry()
    photographer, room = await create_room(registry)
    restart_store()
    # The restart threw no error, but the worker is no longer registered, which it notices on its next beat.
    await closed_for_retry(photographer)
    await registry.disconnect(photographer)
    subject = await eventually(registry.connect)
    with pytest.raises(ProtocolError) as exc:
        await handle(registry, subject, type="room.join", code=room["code"])
    assert exc.value.code == "NOT_FOUND"  # The room went with the restart.
    await handle(registry, subject, type="room.create")
    assert (await receive(subject))["type"] == "room.created"


async def test_a_worker_taken_for_dead_closes_its_sockets_when_it_comes_back(make_registry, settings, open_store):
    stalled, other = await make_registry(), await make_registry()
    photographer, subject, _ = await create_pair(stalled, other)
    await kill(stalled, open_store)  # Its process stalls for longer than the heartbeat lasts.
    await other.sweep()
    assert await receive(subject) == LEFT
    # When the stalled worker runs again, its photographer's place is gone, so its socket must not stay open.
    settings.cleanup_interval_seconds = 0.01
    resumed = asyncio.create_task(stalled._keep_house())
    try:
        await closed_for_retry(photographer)
    finally:
        resumed.cancel()
