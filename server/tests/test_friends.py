# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Lookup, friend requests, the friend list, and presence through the REST API and the signaling socket
(Design 2.5.3, R&S FR-5.5–5.10, NFR-15).

Partitions:
- lookup: unknown Pix ID; the caller (SELF); a friend; a request sent (REQUESTED) or received (INCOMING); none;
  capitals and spaces; a user without a Pix ID; a caller without a Pix ID; no query
- send: a new user; the caller; an unknown id; a user without a Pix ID; a friend; sent twice; the other already
  asked (accepted instead)
- answer: accept or decline by the receiver; by the sender; an unknown or answered request; withdraw by the sender or
  by the receiver; ask again after a decline; accept a request for a pair who are already friends
- friend list: empty, one, several (ordered by name), both directions
- remove: a friend, both directions, twice, with a pending request, and adding again
- presence: no socket; a signed-in socket; in a room; an anonymous, invalid-token, or expired-token socket; two
  sockets, one closed
"""

import time
from datetime import UTC, datetime

import pytest
from sqlalchemy.exc import IntegrityError

from pix_server.db.models import FriendRequest


def assert_error(response, status, code):
    assert response.status_code == status, response.text
    assert response.json()["error"]["code"] == code


def find(client, headers, pix_id):
    return client.get("/api/v1/users", headers=headers, params={"pixId": pix_id})


def request_friend(client, headers, user):
    return client.post("/api/v1/friend-requests", headers=headers, json={"userId": user["id"]})


def requests_of(client, headers):
    response = client.get("/api/v1/friend-requests", headers=headers)
    assert response.status_code == 200
    return response.json()


def friends_of(client, headers):
    response = client.get("/api/v1/friends", headers=headers)
    assert response.status_code == 200
    return response.json()["friends"]


def public(user):
    return {key: user[key] for key in ("id", "pixId", "displayName")}


def offline(user):
    return {**public(user), "online": False, "inSession": False}


@pytest.fixture
def two(sign_up):
    """Two password accounts: (headers, account) each."""
    return sign_up("dongje.p", "Dongje"), sign_up("junhyeong.h", "Junhyeong")


def befriend(client, two):
    (dongje, dongje_user), (junhyeong, junhyeong_user) = two
    request_id = request_friend(client, dongje, junhyeong_user).json()["id"]
    assert client.post(f"/api/v1/friend-requests/{request_id}/accept", headers=junhyeong).status_code == 200


# --- lookup --------------------------------------------------------------------------------------------------------


def test_lookup_finds_only_an_exact_pix_id(client, two):
    (dongje, _), (_, junhyeong_user) = two
    response = find(client, dongje, "junhyeong.h")
    assert response.status_code == 200
    assert response.json() == {"user": public(junhyeong_user), "relation": "NONE", "requestId": None}
    assert_error(find(client, dongje, "junhyeong"), 404, "NOT_FOUND")
    assert_error(find(client, dongje, "junhyeong.h2"), 404, "NOT_FOUND")


def test_lookup_ignores_capitals_and_spaces(client, two):
    (dongje, _), _ = two
    assert find(client, dongje, " Junhyeong.H ").json()["relation"] == "NONE"


def test_lookup_marks_the_caller_and_a_friend(client, two):
    (dongje, _), (junhyeong, _) = two
    assert find(client, dongje, "dongje.p").json()["relation"] == "SELF"
    befriend(client, two)
    assert find(client, dongje, "junhyeong.h").json()["relation"] == "FRIEND"
    assert find(client, junhyeong, "dongje.p").json()["relation"] == "FRIEND"


def test_lookup_marks_a_pending_request_from_either_side_with_its_id(client, two):
    (dongje, _), (junhyeong, junhyeong_user) = two
    request_id = request_friend(client, dongje, junhyeong_user).json()["id"]
    sent = find(client, dongje, "junhyeong.h").json()
    assert (sent["relation"], sent["requestId"]) == ("REQUESTED", request_id)
    received = find(client, junhyeong, "dongje.p").json()
    assert (received["relation"], received["requestId"]) == ("INCOMING", request_id)


def test_a_user_without_a_pix_id_cannot_be_found_or_use_friends(client, two):
    (dongje, _), _ = two
    response = client.post("/api/v1/auth/google/signup", json={"idToken": "google:sub-1:Seongmin"})
    newcomer = {"Authorization": f"Bearer {response.json()['accessToken']}"}
    assert_error(request_friend(client, dongje, response.json()["user"]), 404, "NOT_FOUND")
    for call in (
        lambda: find(client, newcomer, "dongje.p"),
        lambda: client.get("/api/v1/friends", headers=newcomer),
        lambda: client.get("/api/v1/friend-requests", headers=newcomer),
    ):
        assert_error(call(), 403, "PIX_ID_REQUIRED")


def test_lookup_without_a_pix_id_query_is_invalid(client, two):
    (dongje, _), _ = two
    assert_error(client.get("/api/v1/users", headers=dongje), 400, "INVALID_REQUEST")


# --- sending -------------------------------------------------------------------------------------------------------


def test_request_and_accept_make_each_user_appear_in_the_others_friend_list(client, two):
    (dongje, dongje_user), (junhyeong, junhyeong_user) = two
    sent = request_friend(client, dongje, junhyeong_user)
    assert sent.status_code == 201
    request_id = sent.json()["id"]
    assert request_id.startswith("fr_")
    assert sent.json() == {"id": request_id, "to": public(junhyeong_user), "createdAt": 1_000_000}
    assert requests_of(client, junhyeong) == {
        "incoming": [{"id": request_id, "from": public(dongje_user), "createdAt": 1_000_000}],
        "outgoing": [],
    }
    assert requests_of(client, dongje)["outgoing"] == [sent.json()]

    accepted = client.post(f"/api/v1/friend-requests/{request_id}/accept", headers=junhyeong)
    assert accepted.status_code == 200
    assert accepted.json() == {"friend": offline(dongje_user)}
    assert friends_of(client, dongje) == [offline(junhyeong_user)]
    assert friends_of(client, junhyeong) == [offline(dongje_user)]
    assert requests_of(client, dongje) == {"incoming": [], "outgoing": []}
    assert requests_of(client, junhyeong) == {"incoming": [], "outgoing": []}


def test_requests_are_listed_newest_first(client, clock, sign_up):
    me, _ = sign_up("dongje.p")
    targets = [sign_up(f"friend.{n}")[1] for n in range(3)]
    for target in targets:
        request_friend(client, me, target)
        clock.advance(1)
    assert [item["to"]["pixId"] for item in requests_of(client, me)["outgoing"]] == ["friend.2", "friend.1", "friend.0"]


def test_request_to_oneself_unknown_or_twice_is_refused(client, two):
    (dongje, dongje_user), (_, junhyeong_user) = two
    assert_error(request_friend(client, dongje, dongje_user), 400, "INVALID_REQUEST")
    assert_error(request_friend(client, dongje, {"id": "u_UNKNOWN"}), 404, "NOT_FOUND")
    request_friend(client, dongje, junhyeong_user)
    assert_error(request_friend(client, dongje, junhyeong_user), 409, "ALREADY_REQUESTED")


def test_request_to_a_friend_answers_already_friends(client, two):
    (dongje, _), (_, junhyeong_user) = two
    befriend(client, two)
    assert_error(request_friend(client, dongje, junhyeong_user), 409, "ALREADY_FRIENDS")


def test_request_to_someone_who_already_asked_accepts_their_request(client, two):
    (dongje, dongje_user), (junhyeong, junhyeong_user) = two
    request_friend(client, dongje, junhyeong_user)
    response = request_friend(client, junhyeong, dongje_user)
    assert response.status_code == 200
    assert response.json() == {"friend": offline(dongje_user)}
    assert friends_of(client, dongje) == [offline(junhyeong_user)]
    assert requests_of(client, dongje) == {"incoming": [], "outgoing": []}


# --- answering -----------------------------------------------------------------------------------------------------


def test_only_the_receiver_can_accept_and_only_once(client, two):
    (dongje, _), (junhyeong, junhyeong_user) = two
    request_id = request_friend(client, dongje, junhyeong_user).json()["id"]
    assert_error(client.post(f"/api/v1/friend-requests/{request_id}/accept", headers=dongje), 404, "NOT_FOUND")
    assert client.post(f"/api/v1/friend-requests/{request_id}/accept", headers=junhyeong).status_code == 200
    assert_error(client.post(f"/api/v1/friend-requests/{request_id}/accept", headers=junhyeong), 404, "NOT_FOUND")
    assert_error(client.post("/api/v1/friend-requests/fr_UNKNOWN/accept", headers=junhyeong), 404, "NOT_FOUND")


def test_declined_request_is_gone_and_can_be_sent_again(client, two):
    (dongje, _), (junhyeong, junhyeong_user) = two
    request_id = request_friend(client, dongje, junhyeong_user).json()["id"]
    assert_error(client.post(f"/api/v1/friend-requests/{request_id}/decline", headers=dongje), 404, "NOT_FOUND")
    assert client.post(f"/api/v1/friend-requests/{request_id}/decline", headers=junhyeong).status_code == 204
    assert requests_of(client, junhyeong) == {"incoming": [], "outgoing": []}
    assert friends_of(client, dongje) == []
    assert request_friend(client, dongje, junhyeong_user).status_code == 201


def test_only_the_sender_can_withdraw_a_request(client, two):
    (dongje, _), (junhyeong, junhyeong_user) = two
    request_id = request_friend(client, dongje, junhyeong_user).json()["id"]
    assert_error(client.delete(f"/api/v1/friend-requests/{request_id}", headers=junhyeong), 404, "NOT_FOUND")
    assert client.delete(f"/api/v1/friend-requests/{request_id}", headers=dongje).status_code == 204
    assert requests_of(client, junhyeong)["incoming"] == []
    assert_error(client.delete(f"/api/v1/friend-requests/{request_id}", headers=dongje), 404, "NOT_FOUND")


# --- the friend list -----------------------------------------------------------------------------------------------


def test_friend_list_starts_empty_and_is_ordered_by_name(client, sign_up):
    me, _ = sign_up("dongje.p", "Dongje")
    assert friends_of(client, me) == []
    for pix_id, name in [("zoe.k", "Zoe"), ("amy.l", "Amy"), ("min.s", "Min")]:
        headers, user = sign_up(pix_id, name)
        request_id = request_friend(client, me, user).json()["id"]
        client.post(f"/api/v1/friend-requests/{request_id}/accept", headers=headers)
    assert [friend["displayName"] for friend in friends_of(client, me)] == ["Amy", "Min", "Zoe"]


def test_removed_friend_is_gone_for_both_and_can_be_added_again(client, two):
    (dongje, _), (junhyeong, junhyeong_user) = two
    befriend(client, two)
    assert client.delete(f"/api/v1/friends/{junhyeong_user['id']}", headers=dongje).status_code == 204
    assert friends_of(client, dongje) == []
    assert friends_of(client, junhyeong) == []
    assert client.delete(f"/api/v1/friends/{junhyeong_user['id']}", headers=dongje).status_code == 204
    assert request_friend(client, dongje, junhyeong_user).status_code == 201


def test_removing_a_user_also_drops_pending_requests_between_the_two(client, two):
    (dongje, _), (junhyeong, junhyeong_user) = two
    request_friend(client, dongje, junhyeong_user)
    client.delete(f"/api/v1/friends/{junhyeong_user['id']}", headers=dongje)
    assert requests_of(client, junhyeong)["incoming"] == []


# --- presence ------------------------------------------------------------------------------------------------------


def presence(client, headers):
    """`(online, inSession)` of the one friend in the list."""
    (friend,) = friends_of(client, headers)
    return friend["online"], friend["inSession"]


def registered(socket):
    """The answer to a first message shows that the server has registered the socket."""
    socket.send_json({"type": "hello"})
    assert socket.receive_json()["type"] == "error"


def wait_for_presence(client, headers, expected):
    """A closed socket frees its place in the store from a task of its own, so it can take a moment."""
    deadline = time.monotonic() + 2
    while presence(client, headers) != expected and time.monotonic() < deadline:
        time.sleep(0.01)
    assert presence(client, headers) == expected


def test_signed_in_socket_makes_a_friend_online_and_a_room_puts_them_in_a_session(client, two):
    (dongje, _), (junhyeong, _) = two
    befriend(client, two)
    assert presence(client, dongje) == (False, False)
    with client.websocket_connect("/ws", headers=junhyeong) as socket:
        registered(socket)
        assert presence(client, dongje) == (True, False)
        socket.send_json({"type": "room.create"})
        assert socket.receive_json()["type"] == "room.created"
        assert presence(client, dongje) == (True, True)
    wait_for_presence(client, dongje, (False, False))


@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer not-a-jwt"}])
def test_anonymous_or_unknown_token_socket_works_but_shows_nobody_online(client, two, headers):
    (dongje, _), _ = two
    befriend(client, two)
    with client.websocket_connect("/ws", headers=headers) as socket:
        socket.send_json({"type": "room.create"})
        assert socket.receive_json()["type"] == "room.created"
        assert presence(client, dongje) == (False, False)


def test_friend_stays_online_while_one_of_two_sockets_is_open(client, two):
    (dongje, _), (junhyeong, _) = two
    befriend(client, two)
    with client.websocket_connect("/ws", headers=junhyeong) as first:
        with client.websocket_connect("/ws", headers=junhyeong) as second:
            second.send_json({"type": "room.create"})
            second.receive_json()
            first.send_json({"type": "room.create"})
            first.receive_json()
        wait_for_presence(client, dongje, (True, True))
        first.send_json({"type": "leave"})
        wait_for_presence(client, dongje, (True, False))


def test_socket_with_an_expired_token_works_but_shows_nobody_online(client, clock, two):
    (dongje, _), (junhyeong, _) = two
    befriend(client, two)
    clock.advance(15 * 60)
    with client.websocket_connect("/ws", headers=junhyeong) as socket:
        socket.send_json({"type": "room.create"})
        assert socket.receive_json()["type"] == "room.created"
        # Both access tokens expired at once, so the friend list is read with a new one.
        fresh = client.post("/api/v1/auth/login", json={"pixId": "dongje.p", "password": "sunset2026"})
        assert presence(client, {"Authorization": f"Bearer {fresh.json()['accessToken']}"}) == (False, False)


def test_accepting_when_already_friends_keeps_one_friendship(client, two):
    """Both users answer at the same moment: the second answer finds the friendship already there."""
    (dongje, dongje_user), (junhyeong, junhyeong_user) = two
    request_friend(client, dongje, junhyeong_user)
    second = client.post("/api/v1/friend-requests", headers=junhyeong, json={"userId": dongje_user["id"]})
    assert second.status_code == 200
    # A request that slipped in before the friendship was seen is answered without a second row.
    late_request = "fr_LATE"

    async def insert_late_request():
        async with client.app.state.database() as db:
            db.add(
                FriendRequest(
                    id=late_request,
                    from_user_id=dongje_user["id"],
                    to_user_id=junhyeong_user["id"],
                    created_at=datetime.now(UTC),
                )
            )
            await db.commit()

    client.portal.call(insert_late_request)
    accepted = client.post(f"/api/v1/friend-requests/{late_request}/accept", headers=junhyeong)
    assert accepted.status_code == 200
    assert friends_of(client, dongje) == [offline(junhyeong_user)]
    assert friends_of(client, junhyeong) == [offline(dongje_user)]


def test_requests_between_two_users_are_unique_in_one_direction(client, two):
    """The database refuses a duplicate even if two requests race past the API's own check."""
    (_, dongje_user), (_, junhyeong_user) = two

    async def insert_twice():
        async with client.app.state.database() as db:
            for request_id in ("fr_1", "fr_2"):
                db.add(
                    FriendRequest(
                        id=request_id,
                        from_user_id=dongje_user["id"],
                        to_user_id=junhyeong_user["id"],
                        created_at=datetime.now(UTC),
                    )
                )
                try:
                    await db.commit()
                except IntegrityError:
                    return request_id
        return None

    assert client.portal.call(insert_twice) == "fr_2"
