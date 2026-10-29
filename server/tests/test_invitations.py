# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""Invitations through the REST API and the signaling socket (Design 2.5.3, 2.6.6; R&S FR-6.1–6.4, FR-6.9).

Partitions:
- invite: a friend, offline or online; someone who is not a friend, an unknown user, oneself; a friend in a session
  (BUSY, nothing saved); a caller signed out or without a Pix ID; the session store down (nothing saved)
- open: pending; 29 s, 30 s, and 31 s after it was sent; answered; unknown; without a sign-in
- answer: accept, decline, or cancel by the one who may (the friend, the friend, the photographer), or by anyone else;
  after an earlier answer; accepting at 29 s, 30 s, and 31 s; accepting once the room has ended; declining while the
  session store is down
- the session: the photographer takes its place before or after the friend; a friend who accepts in the last second
  still has time to join, and one who never joins lets the room expire; a decline or the expiry tells a waiting
  photographer; a cancel frees the photographer's socket
- the push: to each of the friend's phones, with the data the app needs, at high priority, for the invitation's 30 s;
  not to the photographer; a device FCM no longer knows is removed; FCM failing leaves the invitation working
- the link: an invitation id gives the page that opens Pix and names nobody; anything else is not found
"""

import asyncio
import re
import time

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

INVITATION_ID = re.compile(r"inv_[0-9A-HJKMNP-TV-Z]{26}")


def assert_error(response, status, code):
    assert response.status_code == status, response.text
    assert response.json()["error"]["code"] == code


def public(user):
    return {key: user[key] for key in ("id", "pixId", "displayName")}


@pytest.fixture
def friends(client, sign_up):
    """Dongje and Junhyeong, who are friends: (headers, account) each."""
    dongje = sign_up("dongje.p", "Dongje")
    junhyeong = sign_up("junhyeong.h", "Junhyeong")
    sent = client.post("/api/v1/friend-requests", headers=dongje[0], json={"userId": junhyeong[1]["id"]})
    assert client.post(f"/api/v1/friend-requests/{sent.json()['id']}/accept", headers=junhyeong[0]).status_code == 200
    return dongje, junhyeong


def invite(client, headers, friend):
    return client.post("/api/v1/invitations", headers=headers, json={"friendId": friend["id"]})


def sent(client, friends):
    """Dongje invites Junhyeong."""
    (dongje, _), (_, junhyeong_user) = friends
    response = invite(client, dongje, junhyeong_user)
    assert response.status_code == 201, response.text
    return response.json()


def answer(client, headers, invitation, action):
    return client.post(f"/api/v1/invitations/{invitation['id']}/{action}", headers=headers)


def accepted(client, friends, invitation):
    (_, _), (junhyeong, _) = friends
    response = answer(client, junhyeong, invitation, "accept")
    assert response.status_code == 200, response.text
    return response.json()


def status_of(client, invitation):
    return client.get(f"/api/v1/invitations/{invitation['id']}").json()["status"]


def take_place(socket, place):
    """Takes the place that the token of `place` (the invitation or the accept's answer) gives, and returns the
    answer."""
    socket.send_json(
        {"type": "session.join", "sessionId": place["sessionId"], "rejoin": False, "rejoinToken": place["rejoinToken"]}
    )
    return socket.receive_json()


def offer():
    return {"type": "signal", "sdp": {"type": "offer", "sdp": "v=0\r\n"}}


def register_device(client, headers, token):
    response = client.post("/api/v1/devices", headers=headers, json={"fcmToken": token, "platform": "android"})
    assert response.status_code == 204, response.text


def invitation_rows(settings):
    async def count():
        engine = create_async_engine(settings.database_url)
        async with engine.connect() as connection:
            rows = (await connection.execute(text("SELECT count(*) FROM invitations"))).scalar_one()
        await engine.dispose()
        return rows

    return asyncio.run(count())


# --- invite --------------------------------------------------------------------------------------------------------


def test_inviting_a_friend_answers_with_the_link_and_the_photographers_place(client, clock, friends, turn_api):
    (dongje, _), (_, junhyeong_user) = friends
    invitation = sent(client, friends)
    assert set(invitation) == {"id", "to", "status", "expiresAt", "link", "sessionId", "rejoinToken", "iceServers"}
    assert INVITATION_ID.fullmatch(invitation["id"])
    assert invitation["to"] == public(junhyeong_user)
    assert invitation["status"] == "PENDING"
    assert invitation["expiresAt"] == (clock.now + 30) * 1000
    assert invitation["link"] == f"http://testserver/i/{invitation['id']}"
    assert invitation["iceServers"] == turn_api.servers
    with client.websocket_connect("/ws", headers=dongje) as photographer:
        joined = take_place(photographer, invitation)
    assert joined == {
        "type": "session.joined",
        "sessionId": invitation["sessionId"],
        "role": "PHOTOGRAPHER",
        "iceServers": [],
    }


def test_inviting_an_online_friend_who_is_in_no_session_works(client, friends):
    (_, _), (junhyeong, _) = friends
    with client.websocket_connect("/ws", headers=junhyeong) as presence:
        presence.send_json({"type": "hello"})
        assert presence.receive_json()["type"] == "error"
        sent(client, friends)


@pytest.mark.parametrize("who", ["stranger", "unknown", "self"])
def test_only_a_friend_can_be_invited(client, sign_up, friends, settings, who):
    (dongje, dongje_user), _ = friends
    targets = {
        "stranger": lambda: sign_up("stranger.k", "Stranger")[1],
        "unknown": lambda: {"id": "u_01JB2C3D4E5F6G7H8J9KAMNPQR"},
        "self": lambda: dongje_user,
    }
    assert_error(invite(client, dongje, targets[who]()), 403, "NOT_FRIENDS")
    assert invitation_rows(settings) == 0


def test_a_friend_in_another_session_is_busy_and_nothing_is_saved(client, friends, settings, fcm_api):
    (dongje, _), (junhyeong, junhyeong_user) = friends
    register_device(client, junhyeong, "junhyeong-phone")
    with client.websocket_connect("/ws", headers=junhyeong) as busy:
        busy.send_json({"type": "room.create"})
        assert busy.receive_json()["type"] == "room.created"
        response = invite(client, dongje, junhyeong_user)
        assert_error(response, 409, "BUSY")
        assert response.json()["error"]["message"] == "Junhyeong is in another session"
        busy.send_json({"type": "leave"})
        assert invitation_rows(settings) == 0
        assert fcm_api.pushes == []
        # The room is gone once the leave is handled, so the friend can be invited again.
        deadline = time.monotonic() + 2
        while invite(client, dongje, junhyeong_user).status_code == 409 and time.monotonic() < deadline:
            time.sleep(0.01)
    assert invitation_rows(settings) == 1


def test_inviting_needs_a_sign_in_and_a_pix_id(client, friends):
    (_, _), (_, junhyeong_user) = friends
    assert_error(invite(client, {}, junhyeong_user), 401, "UNAUTHENTICATED")
    google = client.post("/api/v1/auth/google/signup", json={"idToken": "google:sub-1:Seoyeon"}).json()
    assert_error(
        invite(client, {"Authorization": f"Bearer {google['accessToken']}"}, junhyeong_user), 403, "PIX_ID_REQUIRED"
    )


def test_inviting_while_the_session_store_is_down_saves_nothing(client, friends, settings, outage):
    (dongje, _), (_, junhyeong_user) = friends
    outage(True)
    try:
        assert_error(invite(client, dongje, junhyeong_user), 503, "UNAVAILABLE")
    finally:
        outage(False)
    assert invitation_rows(settings) == 0


# --- open ----------------------------------------------------------------------------------------------------------


@pytest.mark.parametrize(("seconds", "status"), [(29, "PENDING"), (30, "EXPIRED"), (31, "EXPIRED")])
def test_opening_shows_who_invites_without_a_sign_in_until_it_expires(client, clock, friends, seconds, status):
    (_, dongje_user), _ = friends
    invitation = sent(client, friends)
    clock.advance(seconds)
    opened = client.get(f"/api/v1/invitations/{invitation['id']}")
    assert opened.status_code == 200
    assert opened.json() == {
        "id": invitation["id"],
        "from": public(dongje_user),
        "status": status,
        "expiresAt": invitation["expiresAt"],
    }


def test_opening_an_unknown_invitation_is_not_found(client):
    assert_error(client.get("/api/v1/invitations/inv_01JB2C3D4E5F6G7H8J9KAMNPQR"), 404, "NOT_FOUND")


# --- answer --------------------------------------------------------------------------------------------------------


@pytest.mark.parametrize(("seconds", "status"), [(29, 200), (30, 410), (31, 410)])
def test_accepting_works_until_the_invitation_expires(client, clock, friends, turn_api, seconds, status):
    (_, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    clock.advance(seconds)
    response = answer(client, junhyeong, invitation, "accept")
    assert response.status_code == status, response.text
    if status == 200:
        place = response.json()
        assert set(place) == {"sessionId", "rejoinToken", "iceServers"}
        assert place["sessionId"] == invitation["sessionId"]
        assert place["rejoinToken"] != invitation["rejoinToken"]
        assert place["iceServers"] == turn_api.servers
        assert status_of(client, invitation) == "ACCEPTED"
    else:
        assert response.json()["error"]["code"] == "INVITATION_GONE"


@pytest.mark.parametrize("action", ["decline", "cancel"])
@pytest.mark.parametrize(("seconds", "status"), [(29, 204), (30, 410)])
def test_declining_and_cancelling_work_until_the_invitation_expires(client, clock, friends, action, seconds, status):
    """Unlike an accept, neither needs the room, so only the invitation's own expiry refuses them."""
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    clock.advance(seconds)
    response = answer(client, dongje if action == "cancel" else junhyeong, invitation, action)
    assert response.status_code == status, response.text


def test_only_the_friend_answers_and_only_the_photographer_cancels(client, sign_up, friends):
    (dongje, _), (junhyeong, _) = friends
    stranger, _ = sign_up("stranger.k", "Stranger")
    invitation = sent(client, friends)
    for headers, action in [
        (stranger, "accept"),
        (stranger, "decline"),
        (stranger, "cancel"),
        (dongje, "accept"),
        (dongje, "decline"),
        (junhyeong, "cancel"),
    ]:
        assert_error(answer(client, headers, invitation, action), 404, "NOT_FOUND")
    assert_error(answer(client, junhyeong, {"id": "inv_01JB2C3D4E5F6G7H8J9KAMNPQR"}, "accept"), 404, "NOT_FOUND")
    assert_error(answer(client, {}, invitation, "accept"), 401, "UNAUTHENTICATED")
    assert status_of(client, invitation) == "PENDING"


@pytest.mark.parametrize(
    ("first", "status"), [("accept", "ACCEPTED"), ("decline", "DECLINED"), ("cancel", "CANCELLED")]
)
def test_an_answered_invitation_is_gone_for_every_later_answer(client, friends, first, status):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    by = dongje if first == "cancel" else junhyeong
    assert answer(client, by, invitation, first).status_code in (200, 204)
    for headers, action in [(junhyeong, "accept"), (junhyeong, "decline"), (dongje, "cancel")]:
        assert_error(answer(client, headers, invitation, action), 410, "INVITATION_GONE")
    assert status_of(client, invitation) == status


def test_accepting_once_the_photographer_left_is_gone_and_leaves_it_pending(client, friends):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    with client.websocket_connect("/ws", headers=dongje) as photographer:
        take_place(photographer, invitation)
        photographer.send_json({"type": "leave"})
        # The leave is handled once a later message on the socket is answered.
        photographer.send_json({"type": "hello"})
        assert photographer.receive_json()["type"] == "error"
    assert_error(answer(client, junhyeong, invitation, "accept"), 410, "INVITATION_GONE")
    assert status_of(client, invitation) == "PENDING"


def test_declining_while_the_session_store_is_down_changes_nothing(client, friends, outage):
    (_, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    outage(True)
    try:
        assert_error(answer(client, junhyeong, invitation, "decline"), 503, "UNAVAILABLE")
    finally:
        outage(False)
    assert status_of(client, invitation) == "PENDING"


# --- the session ---------------------------------------------------------------------------------------------------


def test_the_friend_accepts_and_the_photographer_hears_peer_joined(client, friends):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    with (
        client.websocket_connect("/ws", headers=dongje) as photographer,
        client.websocket_connect("/ws", headers=junhyeong) as subject,
    ):
        assert take_place(photographer, invitation)["role"] == "PHOTOGRAPHER"
        place = accepted(client, friends, invitation)
        assert take_place(subject, place) == {
            "type": "session.joined",
            "sessionId": invitation["sessionId"],
            "role": "SUBJECT",
            "iceServers": [],
        }
        assert photographer.receive_json() == {"type": "peer.joined"}
        photographer.send_json(offer())
        assert subject.receive_json() == offer()


def test_a_friend_who_joins_first_is_announced_to_the_photographer_when_it_comes(client, friends):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    place = accepted(client, friends, invitation)
    with (
        client.websocket_connect("/ws", headers=junhyeong) as subject,
        client.websocket_connect("/ws", headers=dongje) as photographer,
    ):
        assert take_place(subject, place)["role"] == "SUBJECT"
        assert take_place(photographer, invitation)["role"] == "PHOTOGRAPHER"
        assert photographer.receive_json() == {"type": "peer.joined"}
        photographer.send_json(offer())
        # The offer is the subject's next message: nothing was announced to it.
        assert subject.receive_json() == offer()


def test_a_decline_tells_the_waiting_photographer_and_frees_its_socket(client, friends):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    with client.websocket_connect("/ws", headers=dongje) as photographer:
        take_place(photographer, invitation)
        assert answer(client, junhyeong, invitation, "decline").status_code == 204
        assert photographer.receive_json() == {
            "type": "error",
            "code": "DECLINED",
            "message": "Your friend declined the invitation",
        }
        photographer.send_json({"type": "room.create"})
        assert photographer.receive_json()["type"] == "room.created"


def test_an_unanswered_invitation_expires_and_tells_the_photographer(client, clock, friends):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    with client.websocket_connect("/ws", headers=dongje) as photographer:
        take_place(photographer, invitation)
        clock.advance(30)
        assert photographer.receive_json() == {
            "type": "error",
            "code": "EXPIRED",
            "message": "Nobody joined in time; the room has expired",
        }
    assert_error(answer(client, junhyeong, invitation, "accept"), 410, "INVITATION_GONE")


def test_a_cancel_ends_the_room_and_frees_the_photographers_socket(client, friends):
    (dongje, _), _ = friends
    invitation = sent(client, friends)
    with client.websocket_connect("/ws", headers=dongje) as photographer:
        take_place(photographer, invitation)
        assert answer(client, dongje, invitation, "cancel").status_code == 204
        photographer.send_json({"type": "room.create"})
        assert photographer.receive_json()["type"] == "room.created"
    assert status_of(client, invitation) == "CANCELLED"


def test_a_friend_who_accepts_in_the_last_second_still_has_time_to_join(client, clock, friends):
    (dongje, _), (junhyeong, _) = friends
    invitation = sent(client, friends)
    with (
        client.websocket_connect("/ws", headers=dongje) as photographer,
        client.websocket_connect("/ws", headers=junhyeong) as subject,
    ):
        take_place(photographer, invitation)
        clock.advance(29)
        place = accepted(client, friends, invitation)
        clock.advance(14)
        time.sleep(0.05)  # A few sweeps, which leave the room alone.
        assert take_place(subject, place)["role"] == "SUBJECT"
        assert photographer.receive_json() == {"type": "peer.joined"}
        clock.advance(3_600)
        photographer.send_json(offer())
        assert subject.receive_json() == offer()


def test_a_friend_who_accepts_and_never_joins_lets_the_room_expire(client, clock, friends):
    (dongje, _), _ = friends
    invitation = sent(client, friends)
    with client.websocket_connect("/ws", headers=dongje) as photographer:
        take_place(photographer, invitation)
        clock.advance(29)
        accepted(client, friends, invitation)
        clock.advance(15)
        assert photographer.receive_json()["code"] == "EXPIRED"


# --- the push ------------------------------------------------------------------------------------------------------


def test_the_invitation_is_pushed_to_each_of_the_friends_phones(client, friends, fcm_api):
    (dongje, _), (junhyeong, _) = friends
    other_phone = client.post("/api/v1/auth/login", json={"pixId": "junhyeong.h", "password": "sunset2026"}).json()
    register_device(client, junhyeong, "junhyeong-phone")
    register_device(client, {"Authorization": f"Bearer {other_phone['accessToken']}"}, "junhyeong-tablet")
    register_device(client, dongje, "dongje-phone")
    invitation = sent(client, friends)
    assert sorted(push["token"] for push in fcm_api.pushes) == ["junhyeong-phone", "junhyeong-tablet"]
    for push in fcm_api.pushes:
        assert push["data"] == {
            "type": "invitation",
            "invitationId": invitation["id"],
            "fromName": "Dongje",
            "expiresAt": str(invitation["expiresAt"]),
        }
        assert push["android"] == {"priority": "HIGH", "ttl": "30s"}
        assert push["authorization"] == "Bearer test-fcm-access"
        assert push["url"] == "https://fcm.googleapis.com/v1/projects/pix-test/messages:send"
    assert len(fcm_api.token_requests) == 1


def test_a_device_fcm_no_longer_knows_is_removed(client, friends, fcm_api):
    (dongje, _), (junhyeong, _) = friends
    other_phone = client.post("/api/v1/auth/login", json={"pixId": "junhyeong.h", "password": "sunset2026"}).json()
    register_device(client, junhyeong, "junhyeong-phone")
    register_device(client, {"Authorization": f"Bearer {other_phone['accessToken']}"}, "junhyeong-old-phone")
    fcm_api.unregistered.add("junhyeong-old-phone")
    first = sent(client, friends)
    assert answer(client, dongje, first, "cancel").status_code == 204
    fcm_api.pushes.clear()
    sent(client, friends)
    assert [push["token"] for push in fcm_api.pushes] == ["junhyeong-phone"]


@pytest.mark.parametrize("failing", ["token", "push"])
def test_fcm_failing_leaves_the_invitation_working(client, friends, fcm_api, failing):
    (_, _), (junhyeong, _) = friends
    register_device(client, junhyeong, "junhyeong-phone")
    if failing == "token":
        fcm_api.token_status = 500
    else:
        fcm_api.status = 500
    invitation = sent(client, friends)
    accepted(client, friends, invitation)
    # A push FCM refused keeps its device.
    fcm_api.status = 200
    fcm_api.token_status = 200
    fcm_api.pushes.clear()
    sent(client, friends)
    assert [push["token"] for push in fcm_api.pushes] == ["junhyeong-phone"]


# --- the link ------------------------------------------------------------------------------------------------------


def test_the_link_opens_a_page_that_hands_the_invitation_to_pix(client, friends):
    invitation = sent(client, friends)
    page = client.get(invitation["link"].removeprefix("http://testserver"))
    assert page.status_code == 200
    assert page.headers["content-type"].startswith("text/html")
    intent = f"intent://invite/{invitation['id']}#Intent;scheme=pix;package=com.lastpenguin.pix;end"
    assert f'href="{intent}"' in page.text
    assert "Dongje" not in page.text


@pytest.mark.parametrize(
    "path",
    [
        "not-an-invitation",
        "inv_01JB2C3D4E5F6G7H8J9KAMNPQ",  # 25 characters
        "inv_01jb2c3d4e5f6g7h8j9kamnpqr",
        "inv_01JB2C3D4E5F6G7H8J9KAMNPQU",  # U is not in the alphabet
        "inv_01JB2C3D4E5F6G7H8J9KAMNPQR%22%3E",
    ],
)
def test_anything_but_an_invitation_id_has_no_link_page(client, path):
    assert client.get(f"/i/{path}").status_code == 404
