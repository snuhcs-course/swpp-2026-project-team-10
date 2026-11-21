# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""Deleting an account through `DELETE /me` (Design 2.5.3, 2.7; R&S FR-5.18, NFR-13).

Partitions:
- the account: a password account; a Google account without a Pix ID; without a sign-in; deleted again with the same
  token, as a phone whose first answer was lost does (success)
- what goes with it: this phone's sign-in and another phone's, spent refresh tokens, devices, friendships, friend
  requests sent and received, invitations sent and received
- afterwards: its access and refresh tokens on every phone; its Pix ID and its Google account, free for a new account;
  a friend's list, requests, and invitations; the friendships of other accounts, untouched
- a friend request that read the account just before it was deleted and takes the pair lock after: the other user
  gone (not found), the caller gone (signed out), never a row naming a missing user
- any other write for an account deleted meanwhile: the rows it holds leave the deleted one out; a sign-in whose
  password check ends after the deletion answers as for a wrong password and saves no session
"""

import pytest
from sqlalchemy import or_, select

from pix_server.accounts.auth import TokenClaims, held_users
from pix_server.accounts.friends import lock_pair
from pix_server.accounts.router import delete_me
from pix_server.db.models import AuthSession, Device, FriendRequest, Friendship, Invitation, SpentRefreshToken, User
from pix_server.errors import ApiError

PASSWORD = "sunset2026"


def assert_error(response, status, code):
    assert response.status_code == status, response.text
    assert response.json()["error"]["code"] == code


def bearer(token):
    return {"Authorization": f"Bearer {token}"}


def delete_account(client, headers):
    return client.delete("/api/v1/me", headers=headers)


def log_in(client, pix_id):
    response = client.post("/api/v1/auth/login", json={"pixId": pix_id, "password": PASSWORD})
    assert response.status_code == 200, response.text
    return response.json()


def befriend(client, first, second):
    (headers, _), (other_headers, other_user) = first, second
    sent = client.post("/api/v1/friend-requests", headers=headers, json={"userId": other_user["id"]})
    accepted = client.post(f"/api/v1/friend-requests/{sent.json()['id']}/accept", headers=other_headers)
    assert accepted.status_code == 200, accepted.text


def friends_of(client, headers):
    response = client.get("/api/v1/friends", headers=headers)
    assert response.status_code == 200, response.text
    return [friend["pixId"] for friend in response.json()["friends"]]


def requests_of(client, headers):
    response = client.get("/api/v1/friend-requests", headers=headers)
    assert response.status_code == 200, response.text
    return response.json()


def incoming_invitations(client, headers):
    response = client.get("/api/v1/invitations/incoming", headers=headers)
    assert response.status_code == 200, response.text
    return response.json()["invitations"]


def rows_of(client, user_id):
    """Every row that names the user, by table."""

    async def read():
        async with client.app.state.database() as db:
            sessions = select(AuthSession.id).where(AuthSession.user_id == user_id)
            return {
                "users": (await db.scalars(select(User.id).where(User.id == user_id))).all(),
                "sessions": (await db.scalars(sessions)).all(),
                "spent_refresh_tokens": (
                    await db.scalars(
                        select(SpentRefreshToken.token_hash).where(SpentRefreshToken.session_id.in_(sessions))
                    )
                ).all(),
                "devices": (await db.scalars(select(Device.id).where(Device.user_id == user_id))).all(),
                "friend_requests": (
                    await db.scalars(
                        select(FriendRequest.id).where(
                            or_(FriendRequest.from_user_id == user_id, FriendRequest.to_user_id == user_id)
                        )
                    )
                ).all(),
                "friendships": (
                    await db.scalars(
                        select(Friendship.user_a).where(or_(Friendship.user_a == user_id, Friendship.user_b == user_id))
                    )
                ).all(),
                "invitations": (
                    await db.scalars(
                        select(Invitation.id).where(
                            or_(Invitation.from_user_id == user_id, Invitation.to_user_id == user_id)
                        )
                    )
                ).all(),
            }

    return client.portal.call(read)


def test_a_deleted_account_no_longer_signs_in_and_its_pix_id_is_free(client, sign_up):
    headers, _ = sign_up("test1", "Test Account 1")
    assert delete_account(client, headers).status_code == 204
    assert_error(
        client.post("/api/v1/auth/login", json={"pixId": "test1", "password": PASSWORD}), 401, "INVALID_CREDENTIALS"
    )
    assert client.post("/api/v1/auth/check-pix-id", json={"pixId": "test1"}).json() == {"available": True}
    # Someone else may take the Pix ID at once.
    sign_up("test1", "Test Account 2")


def test_the_account_is_signed_out_on_every_phone(client, sign_up):
    headers, _ = sign_up("test1", "Test Account 1")
    other_phone = log_in(client, "test1")
    assert delete_account(client, headers).status_code == 204
    # The other phone's next request fails, and so does its refresh, so it signs out.
    assert_error(client.get("/api/v1/me", headers=bearer(other_phone["accessToken"])), 401, "UNAUTHENTICATED")
    assert_error(
        client.post("/api/v1/auth/refresh", json={"refreshToken": other_phone["refreshToken"]}),
        401,
        "INVALID_REFRESH_TOKEN",
    )
    assert_error(client.get("/api/v1/me", headers=headers), 401, "UNAUTHENTICATED")


def test_friends_no_longer_list_the_account_or_its_requests_and_invitations(client, sign_up):
    test1 = sign_up("test1", "Test Account 1")
    test2 = sign_up("test2", "Test Account 2")
    test3 = sign_up("test3", "Test Account 3")
    test4 = sign_up("test4", "Test Account 4")
    befriend(client, test1, test2)
    befriend(client, test2, test3)
    # test1 asks test3, and test4 asks test1.
    assert client.post("/api/v1/friend-requests", headers=test1[0], json={"userId": test3[1]["id"]}).is_success
    assert client.post("/api/v1/friend-requests", headers=test4[0], json={"userId": test1[1]["id"]}).is_success
    assert client.post("/api/v1/invitations", headers=test1[0], json={"friendId": test2[1]["id"]}).is_success
    assert [invitation["from"]["pixId"] for invitation in incoming_invitations(client, test2[0])] == ["test1"]

    assert delete_account(client, test1[0]).status_code == 204

    assert friends_of(client, test2[0]) == ["test3"]
    assert friends_of(client, test3[0]) == ["test2"]
    assert requests_of(client, test3[0])["incoming"] == []
    assert requests_of(client, test4[0])["outgoing"] == []
    assert incoming_invitations(client, test2[0]) == []
    assert client.post("/api/v1/users/lookup", headers=test2[0], json={"pixId": "test1"}).status_code == 404


def test_the_database_keeps_no_row_of_the_account(client, sign_up):
    test1 = sign_up("test1", "Test Account 1")
    test2 = sign_up("test2", "Test Account 2")
    test3 = sign_up("test3", "Test Account 3")
    befriend(client, test1, test2)
    # An accepted request is deleted, so test3's stays pending.
    assert client.post("/api/v1/friend-requests", headers=test3[0], json={"userId": test1[1]["id"]}).is_success
    assert client.post("/api/v1/invitations", headers=test2[0], json={"friendId": test1[1]["id"]}).is_success
    other_phone = log_in(client, "test1")
    # A refresh keeps the spent token, and a device registers this phone.
    assert client.post("/api/v1/auth/refresh", json={"refreshToken": other_phone["refreshToken"]}).is_success
    device = {"fcmToken": "fcm-test1", "platform": "android"}
    assert client.post("/api/v1/devices", headers=test1[0], json=device).status_code == 204
    assert all(rows_of(client, test1[1]["id"]).values())

    assert delete_account(client, test1[0]).status_code == 204

    assert rows_of(client, test1[1]["id"]) == {
        "users": [],
        "sessions": [],
        "spent_refresh_tokens": [],
        "devices": [],
        "friend_requests": [],
        "friendships": [],
        "invitations": [],
    }
    assert rows_of(client, test2[1]["id"])["users"] == [test2[1]["id"]]


def test_a_deleted_google_account_can_sign_up_again(client):
    signed_up = client.post("/api/v1/auth/google/signup", json={"idToken": "google:sub-1:Test Account 1"})
    assert signed_up.status_code == 201, signed_up.text
    assert delete_account(client, bearer(signed_up.json()["accessToken"])).status_code == 204
    assert_error(client.post("/api/v1/auth/google", json={"idToken": "google:sub-1"}), 404, "NOT_REGISTERED")
    assert client.post("/api/v1/auth/google/signup", json={"idToken": "google:sub-1"}).status_code == 201


@pytest.mark.parametrize("headers", [{}, bearer("not-a-jwt")])
def test_delete_needs_a_sign_in(client, headers):
    assert_error(delete_account(client, headers), 401, "UNAUTHENTICATED")


def test_deleting_again_with_the_same_token_succeeds(client, sign_up):
    """A phone whose first answer was lost tries again with the same access token, and learns the account is gone."""
    headers, _ = sign_up("test1", "Test Account 1")
    assert delete_account(client, headers).status_code == 204
    assert delete_account(client, headers).status_code == 204
    assert_error(client.get("/api/v1/me", headers=headers), 401, "UNAUTHENTICATED")


def test_the_pair_lock_finds_an_account_deleted_meanwhile(client, sign_up):
    """`send_friend_request` reads the other user, then waits for the pair lock; on PostgreSQL a deletion that commits
    meanwhile leaves the lock with one row. The lock tells, so the request answers instead of inserting a row that
    names a missing user."""
    headers, me = sign_up("test1", "Test Account 1")
    other_headers, other = sign_up("test2", "Test Account 2")
    assert delete_account(client, other_headers).status_code == 204

    async def lock(first, second):
        async with client.app.state.database() as db:
            try:
                return await lock_pair(db, first, second)
            except ApiError as error:
                return error.status, error.code

    assert client.portal.call(lock, me["id"], other["id"]) is False
    assert client.portal.call(lock, other["id"], me["id"]) == (401, "UNAUTHENTICATED")


def test_held_users_leave_out_an_account_deleted_meanwhile(client, sign_up):
    _, me = sign_up("test1", "Test Account 1")
    other_headers, other = sign_up("test2", "Test Account 2")
    assert delete_account(client, other_headers).status_code == 204

    async def held(*user_ids):
        async with client.app.state.database() as db:
            return await held_users(db, *user_ids)

    assert client.portal.call(held, me["id"], other["id"]) == {me["id"]}


def test_a_sign_in_whose_password_check_ends_after_the_deletion_saves_no_session(client, sign_up, monkeypatch):
    """The check waits for a hash slot outside any transaction; another phone deletes the account meanwhile."""
    _, me = sign_up("test1", "Test Account 1")
    passwords = client.app.state.passwords
    check = passwords.verify

    async def check_while_deleted(stored, password):
        right = await check(stored, password)
        async with client.app.state.database() as db:
            await delete_me(TokenClaims(user_id=me["id"], session_id="ses_other_phone"), db)
        return right

    monkeypatch.setattr(passwords, "verify", check_while_deleted)
    response = client.post("/api/v1/auth/login", json={"pixId": "test1", "password": PASSWORD})

    assert_error(response, 401, "INVALID_CREDENTIALS")
    assert rows_of(client, me["id"])["sessions"] == []
