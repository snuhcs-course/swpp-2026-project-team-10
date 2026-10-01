import json
import re
from contextlib import ExitStack

import pytest
from starlette.websockets import WebSocketDisconnect


def create_room(photographer):
    photographer.send_json({"type": "room.create"})
    room = photographer.receive_json()
    assert room["type"] == "room.created"
    assert re.fullmatch(r"[0-9]{6}", room["code"])
    assert room["sessionId"].startswith("s_")
    assert room["iceServers"] == []
    assert set(room) == {"type", "code", "sessionId", "iceServers"}
    return room


def join_room(photographer, subject, room):
    subject.send_json({"type": "room.join", "code": room["code"]})
    assert subject.receive_json() == {
        "type": "session.joined",
        "sessionId": room["sessionId"],
        "role": "SUBJECT",
        "iceServers": [],
    }
    assert photographer.receive_json() == {"type": "peer.joined"}


def assert_error(socket, code):
    message = socket.receive_json()
    assert message["type"] == "error"
    assert message["code"] == code
    assert isinstance(message["message"], str)
    return message


def ice_signal(tag="test"):
    return {"type": "signal", "candidate": {"sdpMid": "0", "sdpMLineIndex": 0, "candidate": tag}}


def test_health_and_no_image_generation_api(client):
    assert client.get("/health").json() == {"status": "ok"}
    for path in ("/poses", "/templates", "/api/v1/poses", "/api/v1/templates"):
        assert client.get(path).status_code == 404
        assert client.post(path).status_code == 404


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
