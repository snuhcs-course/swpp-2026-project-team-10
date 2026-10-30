# AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo and Dongje Park
import json
import re
import time
from contextlib import ExitStack

import pytest
from starlette.websockets import WebSocketDisconnect


def create_room(photographer):
    photographer.send_json({"type": "room.create"})
    room = photographer.receive_json()
    assert room["type"] == "room.created"
    assert re.fullmatch(r"[0-9]{6}", room["code"])
    assert room["sessionId"].startswith("s_")
    assert isinstance(room["iceServers"], list)
    assert isinstance(room["rejoinToken"], str) and len(room["rejoinToken"]) >= 16
    assert set(room) == {"type", "code", "sessionId", "iceServers", "rejoinToken"}
    return room


def join_room(photographer, subject, room):
    """Returns the subject's `session.joined`."""
    subject.send_json({"type": "room.join", "code": room["code"]})
    joined = subject.receive_json()
    assert joined == {
        "type": "session.joined",
        "sessionId": room["sessionId"],
        "role": "SUBJECT",
        "iceServers": joined["iceServers"],
        "rejoinToken": joined["rejoinToken"],
    }
    assert isinstance(joined["iceServers"], list)
    assert isinstance(joined["rejoinToken"], str) and len(joined["rejoinToken"]) >= 16
    assert photographer.receive_json() == {"type": "peer.joined"}
    return joined


def assert_error(socket, code):
    message = socket.receive_json()
    assert message["type"] == "error"
    assert message["code"] == code
    assert isinstance(message["message"], str)
    return message


def session_join(socket, session_id, token, rejoin=True):
    """Sends `session.join` and returns the answer."""
    socket.send_json({"type": "session.join", "sessionId": session_id, "rejoin": rejoin, "rejoinToken": token})
    return socket.receive_json()


def rejoined(session_id, role):
    """The answer to a successful `session.join`: no new relay credentials, since the phone keeps its own."""
    return {"type": "session.joined", "sessionId": session_id, "role": role, "iceServers": []}


def assert_nothing_more(*sockets):
    """An answer to an unknown message proves that nothing else was queued for these sockets before it."""
    for socket in sockets:
        socket.send_json({"type": "unknown"})
        assert_error(socket, "UNKNOWN_TYPE")


# A session id in the server's format that no session has.
SESSION_ID = "s_" + "0" * 32
OFFER = {"type": "signal", "sdp": {"type": "offer", "sdp": "v=0\r\no=restart\r\n"}}
ANSWER = {"type": "signal", "sdp": {"type": "answer", "sdp": "v=0\r\no=restart\r\n"}}
LOST = {"type": "peer.left", "reason": "CONNECTION_LOST"}


def ice_signal(tag="test"):
    return {"type": "signal", "candidate": {"sdpMid": "0", "sdpMLineIndex": 0, "candidate": tag}}


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_health_fails_while_the_session_store_is_down(client, outage):
    outage(True)
    response = client.get("/health")
    assert response.status_code == 503
    assert response.json() == {"status": "unavailable"}
    outage(False)
    assert client.get("/health").status_code == 200


def test_socket_is_asked_to_try_again_while_the_session_store_is_down(client, outage):
    outage(True)
    with client.websocket_connect("/ws") as socket:
        with pytest.raises(WebSocketDisconnect) as exc:
            socket.receive_json()
        assert exc.value.code == 1013
    outage(False)
    with client.websocket_connect("/ws") as socket:
        create_room(socket)


def test_store_outage_in_the_middle_of_a_session_closes_the_socket(client, outage):
    with client.websocket_connect("/ws") as photographer:
        create_room(photographer)
        outage(True)
        photographer.send_json(ice_signal())
        with pytest.raises(WebSocketDisconnect) as exc:
            photographer.receive_json()
        assert exc.value.code == 1013
    outage(False)
    with client.websocket_connect("/ws") as socket:
        create_room(socket)


def test_waiting_room_is_closed_when_the_session_store_fails(client, outage):
    # The photographer sends nothing while it waits, so only the server's own check can notice the outage.
    with client.websocket_connect("/ws") as photographer:
        create_room(photographer)
        outage(True)
        with pytest.raises(WebSocketDisconnect) as exc:
            photographer.receive_json()
        assert exc.value.code == 1013
    outage(False)


def test_waiting_room_is_closed_when_redis_restarts_empty(client, restart_store):
    with client.websocket_connect("/ws") as photographer:
        create_room(photographer)
        restart_store()
        with pytest.raises(WebSocketDisconnect) as exc:
            photographer.receive_json()
        assert exc.value.code == 1013


def test_internal_stats_count_open_rooms_and_sockets(client):
    assert client.get("/internal/stats").json() == {"sessions": 0, "connections": 0}
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        assert client.get("/internal/stats").json()["sessions"] == 1
        join_room(photographer, subject, room)
        assert client.get("/internal/stats").json() == {"sessions": 1, "connections": 2}


def test_each_phone_gets_its_own_relay_credentials(client, turn_api):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
    assert room["iceServers"] == turn_api.servers
    assert joined["iceServers"] == turn_api.servers
    assert len(turn_api.requests) == 2


def test_a_wrong_code_asks_for_no_relay_credentials(client, turn_api):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        create_room(photographer)
        subject.send_json({"type": "room.join", "code": "000000"})
        assert_error(subject, "NOT_FOUND")
    assert len(turn_api.requests) == 1


def test_a_failing_relay_still_opens_the_room_with_stun_only(client, turn_api):
    turn_api.status = 500
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
    assert room["iceServers"] == [{"urls": ["stun:stun.cloudflare.com:3478"]}]
    assert joined["iceServers"] == [{"urls": ["stun:stun.cloudflare.com:3478"]}]


def test_codes_preserve_leading_zeroes_and_retry_collisions(client, monkeypatch):
    choices = iter([42, 42, 7])
    monkeypatch.setattr("pix_server.signaling.registry.secrets.randbelow", lambda _: next(choices))
    with client.websocket_connect("/ws") as first, client.websocket_connect("/ws") as second:
        room1 = create_room(first)
        room2 = create_room(second)
        assert room1["code"] == "000042"
        assert room2["code"] == "000007"
        assert room1["sessionId"] != room2["sessionId"]


@pytest.mark.parametrize(
    ("sender_role", "payload"),
    [
        ("photographer", {"type": "signal", "sdp": {"type": "offer", "sdp": "v=0\r\no=offer\r\n"}}),
        ("subject", {"type": "signal", "sdp": {"type": "answer", "sdp": "v=0\r\no=answer\r\n"}}),
        ("photographer", ice_signal()),
        ("subject", ice_signal()),
        (
            "subject",
            {"type": "signal", "sdp": None, "candidate": {"sdpMid": None, "sdpMLineIndex": 0, "candidate": ""}},
        ),
        ("photographer", {"type": "signal", "sdp": {"type": "offer", "sdp": "v=0\r\n"}, "candidate": None}),
    ],
)
def test_signal_is_forwarded_verbatim(client, sender_role, payload):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        join_room(photographer, subject, room)
        sender, receiver = (photographer, subject) if sender_role == "photographer" else (subject, photographer)
        raw = json.dumps(payload, indent=2) + "\n"
        sender.send_text(raw)
        assert receiver.receive_text() == raw


def test_signal_stays_inside_its_room(client):
    with ExitStack() as stack:
        photographer1, subject1, photographer2, subject2 = [
            stack.enter_context(client.websocket_connect("/ws")) for _ in range(4)
        ]
        join_room(photographer1, subject1, create_room(photographer1))
        join_room(photographer2, subject2, create_room(photographer2))
        photographer1.send_json(ice_signal("room-one"))
        photographer2.send_json(ice_signal("room-two"))
        assert subject1.receive_json() == ice_signal("room-one")
        assert subject2.receive_json() == ice_signal("room-two")
        # A barrier response also proves there was no echo or cross-room delivery.
        for socket in (photographer1, subject1, photographer2, subject2):
            socket.send_json({"type": "unknown"})
            assert_error(socket, "UNKNOWN_TYPE")


def test_only_one_subject_can_join_even_when_requests_arrive_together(client):
    with ExitStack() as stack:
        photographer, subject1, subject2 = [stack.enter_context(client.websocket_connect("/ws")) for _ in range(3)]
        room = create_room(photographer)
        for subject in (subject1, subject2):
            subject.send_json({"type": "room.join", "code": room["code"]})
        results = [subject1.receive_json(), subject2.receive_json()]
        assert sorted(message["type"] for message in results) == ["error", "session.joined"]
        assert next(message for message in results if message["type"] == "error")["code"] == "FULL"
        assert photographer.receive_json() == {"type": "peer.joined"}
        photographer.send_json({"type": "unknown"})
        assert_error(photographer, "UNKNOWN_TYPE")


def test_socket_cannot_create_or_join_another_room_until_it_leaves(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        photographer.send_json({"type": "room.join", "code": room["code"]})
        assert_error(photographer, "INVALID_STATE")
        join_room(photographer, subject, room)
        for socket in (photographer, subject):
            for message in ({"type": "room.create"}, {"type": "room.join", "code": room["code"]}):
                socket.send_json(message)
                assert_error(socket, "INVALID_STATE")
        subject.send_json(ice_signal())
        assert photographer.receive_json() == ice_signal()


@pytest.mark.parametrize("text", ["{", "null", "[]", "1", '"string"', "{}", '{"type":1}', '{"type":null}'])
def test_malformed_frames_leave_socket_usable(client, text):
    with client.websocket_connect("/ws") as socket:
        socket.send_text(text)
        assert_error(socket, "INVALID_MESSAGE")
        create_room(socket)


@pytest.mark.parametrize("code", ["12345", "1234567", "12a456", "１２３４５６", "12345\n", 123456, None, True])
def test_invalid_room_codes(client, code):
    with client.websocket_connect("/ws") as socket:
        socket.send_json({"type": "room.join", "code": code})
        assert_error(socket, "INVALID_MESSAGE")


@pytest.mark.parametrize(
    "message",
    [
        {"type": "room.create", "sessionId": "spoofed"},
        {"type": "room.join"},
        {"type": "signal"},
        {"type": "signal", "sdp": None, "candidate": None},
        {"type": "signal", "sdp": {"type": "offer", "sdp": ""}},
        {"type": "signal", "sdp": {"type": "rollback", "sdp": "v=0"}},
        {"type": "signal", "candidate": {"sdpMLineIndex": -1, "candidate": "ice"}},
        {"type": "signal", "candidate": {"sdpMLineIndex": True, "candidate": "ice"}},
        {"type": "signal", "candidate": {"sdpMLineIndex": 0}},
        {"type": "signal", "sdp": {"type": "offer", "sdp": "v=0"}, "candidate": ice_signal()["candidate"]},
        {**ice_signal(), "sessionId": "some-other-room"},
        {"type": "session.join", "sessionId": "s_7f3a92c1", "rejoinToken": "t" * 22},
        {"type": "session.join", "sessionId": SESSION_ID},
        {"type": "session.join", "sessionId": SESSION_ID, "rejoinToken": "t" * 15},
        {"type": "session.join", "sessionId": SESSION_ID, "rejoinToken": "t" * 65},
        {"type": "session.join", "sessionId": SESSION_ID, "rejoinToken": "t" * 22, "rejoin": "true"},
        {"type": "session.join", "sessionId": SESSION_ID, "rejoinToken": "t" * 22, "role": "PHOTOGRAPHER"},
    ],
)
def test_invalid_payloads_are_rejected(client, message):
    with client.websocket_connect("/ws") as socket:
        socket.send_json(message)
        assert_error(socket, "INVALID_MESSAGE")


def test_unknown_type_and_binary_frames(client):
    with client.websocket_connect("/ws") as socket:
        socket.send_json({"type": "room.open"})
        assert socket.receive_json() == {
            "type": "error",
            "code": "UNKNOWN_TYPE",
            "message": "Unknown message type: room.open",
        }
        socket.send_bytes(b'{"type":"room.create"}')
        assert_error(socket, "INVALID_MESSAGE")
        create_room(socket)


def test_unknown_code_and_signaling_without_a_peer(client):
    with client.websocket_connect("/ws") as socket:
        socket.send_json({"type": "room.join", "code": "123456"})
        assert_error(socket, "NOT_FOUND")
        socket.send_json(ice_signal())
        assert_error(socket, "INVALID_STATE")
        create_room(socket)
        socket.send_json(ice_signal())
        assert_error(socket, "PEER_UNAVAILABLE")


def test_sdp_roles_are_enforced(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        join_room(photographer, subject, create_room(photographer))
        for socket, wrong_type in ((photographer, "answer"), (subject, "offer")):
            socket.send_json({"type": "signal", "sdp": {"type": wrong_type, "sdp": "v=0"}})
            assert_error(socket, "INVALID_STATE")


def test_cancel_invalidates_waiting_code_and_leave_is_idempotent(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        photographer.send_json({"type": "leave"})
        photographer.send_json({"type": "leave"})
        create_room(photographer)  # Wait for cancellation to finish before trying the old code.
        subject.send_json({"type": "room.join", "code": room["code"]})
        assert_error(subject, "NOT_FOUND")


def test_photographer_leave_releases_both_sockets_and_invalidates_code(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        join_room(photographer, subject, room)
        photographer.send_json({"type": "leave"})
        assert subject.receive_json() == {"type": "peer.left", "reason": "PEER_LEFT"}
        subject.send_json({"type": "room.join", "code": room["code"]})
        assert_error(subject, "NOT_FOUND")
        create_room(photographer)
        create_room(subject)


def test_subject_leave_notifies_photographer_and_allows_a_manual_rejoin(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        join_room(photographer, subject, room)
        subject.send_json({"type": "leave"})
        assert photographer.receive_json() == {"type": "peer.left", "reason": "PEER_LEFT"}
        subject.send_json(ice_signal())
        assert_error(subject, "INVALID_STATE")
        join_room(photographer, subject, room)


def test_subject_disconnect_notifies_peer_and_frees_subject_slot(client):
    with client.websocket_connect("/ws") as photographer:
        room = create_room(photographer)
        with client.websocket_connect("/ws") as subject:
            join_room(photographer, subject, room)
        assert photographer.receive_json() == {"type": "peer.left", "reason": "CONNECTION_LOST"}
        with client.websocket_connect("/ws") as replacement:
            join_room(photographer, replacement, room)


def test_photographer_disconnect_makes_room_unjoinable(client):
    with client.websocket_connect("/ws") as subject:
        with client.websocket_connect("/ws") as photographer:
            room = create_room(photographer)
            join_room(photographer, subject, room)
        assert subject.receive_json() == {"type": "peer.left", "reason": "CONNECTION_LOST"}
        subject.send_json(ice_signal())
        assert_error(subject, "PEER_UNAVAILABLE")
        with client.websocket_connect("/ws") as stranger:
            stranger.send_json({"type": "room.join", "code": room["code"]})
            assert_error(stranger, "NOT_FOUND")
        subject.send_json({"type": "leave"})
        create_room(subject)


def test_waiting_room_expires_without_inbound_traffic(client, clock):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        clock.advance(600)
        assert_error(photographer, "EXPIRED")  # Background cleanup, no triggering client message.
        subject.send_json({"type": "room.join", "code": room["code"]})
        assert_error(subject, "EXPIRED")
        create_room(photographer)


def test_joined_room_does_not_expire_at_waiting_deadline(client, clock):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        join_room(photographer, subject, room)
        clock.advance(3_600)
        subject.send_json(ice_signal())
        assert photographer.receive_json() == ice_signal()
        subject.send_json({"type": "leave"})
        assert photographer.receive_json() == {"type": "peer.left", "reason": "PEER_LEFT"}
        join_room(photographer, subject, room)


def test_oversized_text_closes_only_that_peer(client, settings):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        join_room(photographer, subject, create_room(photographer))
        subject.send_text("x" * (settings.max_message_bytes + 1))
        with pytest.raises(WebSocketDisconnect) as exc:
            subject.receive_json()
        assert exc.value.code == 1009
        assert photographer.receive_json() == {"type": "peer.left", "reason": "CONNECTION_LOST"}
        photographer.send_json({"type": "leave"})
        create_room(photographer)


def test_fifty_rooms_can_coexist_without_code_collisions(client):
    with ExitStack() as stack:
        codes = set()
        pairs = []
        for _ in range(50):
            photographer = stack.enter_context(client.websocket_connect("/ws"))
            subject = stack.enter_context(client.websocket_connect("/ws"))
            room = create_room(photographer)
            codes.add(room["code"])
            join_room(photographer, subject, room)
            pairs.append((photographer, subject, room["code"]))
        assert len(codes) == 50
        for photographer, subject, code in pairs:
            subject.send_json(ice_signal(code))
            assert photographer.receive_json() == ice_signal(code)


def try_code(socket, code):
    socket.send_json({"type": "room.join", "code": code})
    return socket.receive_json()


def wrong_code(room):
    return f"{(int(room['code']) + 1) % 1_000_000:06d}"


def test_wrong_codes_from_one_address_are_limited_until_the_window_passes(client, settings, clock):
    settings.join_failure_limit = 3
    # Shorter than the room's own 10 minutes, so the room is still waiting when the window has passed.
    settings.join_failure_window_seconds = 60
    with client.websocket_connect("/ws") as photographer:
        room = create_room(photographer)
        with client.websocket_connect("/ws") as guesser:
            for _ in range(3):
                assert try_code(guesser, wrong_code(room))["code"] == "NOT_FOUND"
            # The right code is refused too while the window is full, so a guess cannot be confirmed.
            assert try_code(guesser, room["code"])["code"] == "RATE_LIMITED"
        # The count belongs to the address, so a new socket does not start over.
        with client.websocket_connect("/ws") as again:
            assert try_code(again, room["code"])["code"] == "RATE_LIMITED"
            clock.advance(settings.join_failure_window_seconds - 1)
            assert try_code(again, room["code"])["code"] == "RATE_LIMITED"
            clock.advance(1)
            join_room(photographer, again, room)


def test_an_expired_code_counts_as_wrong_and_a_full_room_does_not(client, settings, clock):
    settings.join_failure_limit = 1
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        join_room(photographer, subject, room)
        with client.websocket_connect("/ws") as late:
            assert try_code(late, room["code"])["code"] == "FULL"
            with client.websocket_connect("/ws") as other:
                waiting = create_room(other)
                clock.advance(settings.room_ttl_seconds)
                assert try_code(late, waiting["code"])["code"] == "EXPIRED"
                assert assert_error(other, "EXPIRED")
                assert try_code(late, room["code"])["code"] == "RATE_LIMITED"


# --- Reconnection (Design 2.6.7) ---------------------------------------------------------------------------------------


def test_each_place_gets_a_rejoin_token_of_its_own(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
    assert room["rejoinToken"] != joined["rejoinToken"]


def test_a_subject_whose_socket_dropped_takes_its_place_again(client, turn_api):
    with client.websocket_connect("/ws") as photographer:
        room = create_room(photographer)
        with client.websocket_connect("/ws") as subject:
            joined = join_room(photographer, subject, room)
        assert photographer.receive_json() == LOST
        with client.websocket_connect("/ws") as again:
            answer = session_join(again, room["sessionId"], joined["rejoinToken"])
            assert answer == rejoined(room["sessionId"], "SUBJECT")
            assert photographer.receive_json() == {"type": "peer.rejoined"}
            # The photographer's ICE restart reaches the new socket, and the answer comes back.
            photographer.send_json(OFFER)
            assert again.receive_json() == OFFER
            again.send_json(ANSWER)
            assert photographer.receive_json() == ANSWER
    # A phone that comes back keeps its credentials, so Cloudflare is not asked again.
    assert len(turn_api.requests) == 2


def test_a_phone_that_starts_a_new_connection_is_announced_as_joined(client):
    with client.websocket_connect("/ws") as photographer:
        room = create_room(photographer)
        with client.websocket_connect("/ws") as subject:
            joined = join_room(photographer, subject, room)
        assert photographer.receive_json() == LOST
        with client.websocket_connect("/ws") as again:
            answer = session_join(again, room["sessionId"], joined["rejoinToken"], rejoin=False)
            assert answer == rejoined(room["sessionId"], "SUBJECT")
            assert photographer.receive_json() == {"type": "peer.joined"}


def wait_for_connections(client, expected):
    """A closed socket frees its place in the store from a task of its own, so the count can take a moment to drop,
    longer against a real Redis."""
    deadline = time.monotonic() + 2
    while client.get("/internal/stats").json()["connections"] != expected and time.monotonic() < deadline:
        time.sleep(0.01)
    assert client.get("/internal/stats").json()["connections"] == expected


def test_a_rejoin_takes_the_place_from_the_phones_old_socket(client):
    # The server notices a phone's lost socket only when its pings go unanswered, up to 40 s later, so the place is
    # often still held by it when the phone comes back.
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as new:
        room = create_room(photographer)
        with client.websocket_connect("/ws") as old:
            joined = join_room(photographer, old, room)
            assert session_join(new, room["sessionId"], joined["rejoinToken"]) == rejoined(room["sessionId"], "SUBJECT")
            assert photographer.receive_json() == {"type": "peer.rejoined"}
            old.send_json(ice_signal())
            assert_error(old, "INVALID_STATE")
        # The old socket's disconnect changes nothing: no peer.left, and the new socket keeps the place.
        wait_for_connections(client, 2)
        photographer.send_json(ice_signal("after"))
        assert new.receive_json() == ice_signal("after")
        assert_nothing_more(photographer, new)


def test_a_photographer_whose_socket_dropped_takes_its_room_back(client):
    with client.websocket_connect("/ws") as subject:
        with client.websocket_connect("/ws") as photographer:
            room = create_room(photographer)
            join_room(photographer, subject, room)
        assert subject.receive_json() == LOST
        with client.websocket_connect("/ws") as again:
            answer = session_join(again, room["sessionId"], room["rejoinToken"])
            assert answer == rejoined(room["sessionId"], "PHOTOGRAPHER")
            assert subject.receive_json() == {"type": "peer.rejoined"}
            again.send_json(OFFER)
            assert subject.receive_json() == OFFER


def test_a_waiting_room_keeps_its_code_when_the_photographer_comes_back(client):
    with client.websocket_connect("/ws") as photographer:
        room = create_room(photographer)
    with client.websocket_connect("/ws") as again, client.websocket_connect("/ws") as subject:
        assert session_join(again, room["sessionId"], room["rejoinToken"]) == rejoined(
            room["sessionId"], "PHOTOGRAPHER"
        )
        join_room(again, subject, room)


def test_the_token_decides_the_place(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        join_room(photographer, subject, room)
        with client.websocket_connect("/ws") as other:
            answer = session_join(other, room["sessionId"], room["rejoinToken"])
            assert answer == rejoined(room["sessionId"], "PHOTOGRAPHER")
            assert subject.receive_json() == {"type": "peer.rejoined"}
            subject.send_json(ice_signal())
            assert other.receive_json() == ice_signal()


def test_an_unknown_token_or_session_is_refused_and_the_socket_stays_usable(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as stranger:
        room = create_room(photographer)
        assert session_join(stranger, room["sessionId"], "t" * 22)["code"] == "FORBIDDEN"
        assert session_join(stranger, SESSION_ID, room["rejoinToken"])["code"] == "NOT_FOUND"
        create_room(stranger)


def test_a_subject_who_left_cannot_come_back_with_its_token(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
        subject.send_json({"type": "leave"})
        assert photographer.receive_json() == {"type": "peer.left", "reason": "PEER_LEFT"}
        assert session_join(subject, room["sessionId"], joined["rejoinToken"])["code"] == "FORBIDDEN"


def test_a_new_subject_makes_the_earlier_subjects_token_useless(client):
    with client.websocket_connect("/ws") as photographer:
        room = create_room(photographer)
        with client.websocket_connect("/ws") as first:
            joined = join_room(photographer, first, room)
        assert photographer.receive_json() == LOST
        with client.websocket_connect("/ws") as second, client.websocket_connect("/ws") as again:
            join_room(photographer, second, room)
            assert session_join(again, room["sessionId"], joined["rejoinToken"])["code"] == "FORBIDDEN"


def test_a_session_the_photographer_left_is_not_found(client):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
        photographer.send_json({"type": "leave"})
        assert subject.receive_json() == {"type": "peer.left", "reason": "PEER_LEFT"}
        assert session_join(subject, room["sessionId"], joined["rejoinToken"])["code"] == "NOT_FOUND"


@pytest.mark.parametrize(("seconds", "kept"), [(59, True), (60, False)])
def test_a_session_waits_sixty_seconds_after_its_last_socket(client, clock, seconds, kept):
    with client.websocket_connect("/ws") as photographer, client.websocket_connect("/ws") as subject:
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
    clock.advance(seconds)
    with client.websocket_connect("/ws") as again:
        answer = session_join(again, room["sessionId"], joined["rejoinToken"])
    if kept:
        assert answer == rejoined(room["sessionId"], "SUBJECT")
    else:
        assert answer["code"] == "NOT_FOUND"


def test_a_socket_rejoins_only_its_own_place(client):
    with ExitStack() as stack:
        photographer, subject, other = [stack.enter_context(client.websocket_connect("/ws")) for _ in range(3)]
        room = create_room(photographer)
        joined = join_room(photographer, subject, room)
        other_room = create_room(other)
        # Its own place again changes nothing, apart from telling the other phone.
        assert session_join(subject, room["sessionId"], joined["rejoinToken"]) == rejoined(room["sessionId"], "SUBJECT")
        assert photographer.receive_json() == {"type": "peer.rejoined"}
        # Not the other place of its session, and not a place in another session.
        assert session_join(subject, room["sessionId"], room["rejoinToken"])["code"] == "INVALID_STATE"
        assert session_join(other, room["sessionId"], joined["rejoinToken"])["code"] == "INVALID_STATE"
        assert session_join(photographer, other_room["sessionId"], other_room["rejoinToken"])["code"] == "INVALID_STATE"
        subject.send_json(ice_signal())
        assert photographer.receive_json() == ice_signal()
