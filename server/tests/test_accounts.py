# AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
"""Sign-up, sign-in, the caller's account, and device tokens through the REST API (Design 2.5.3, R&S F5).

Partitions:
- sign-up with a password: valid (a JWT for the user; only a hash of the password stored); Pix ID invalid (each rule)
  or taken; password invalid (each rule); display name missing, blank, 30 and 31 characters; malformed body
- checking a Pix ID before sign-up: free; taken by a password account, or by a Google account that chose it; free
  again after that account changed it; breaking a rule (each rule, as sign-up names it); with no token or a bad one
- log-in: right password; capitals and spaces around the Pix ID; wrong password; unknown Pix ID; an account that
  has no password
- Google: unknown account (NOT_REGISTERED); sign-up with a name, without one, with a long one; sign-up twice;
  invalid token; Google unavailable
- adding Google to an account: the not-registered flow; the same Google again; a Google account another Pix account
  uses; a second Google account; two added at once
- adding a password to a Google account: valid; before a Pix ID; breaking a rule; an account that has one; at the
  moment another phone changes the Pix ID to it
- PUT /me: Pix ID for a Google account; invalid or taken Pix ID; display name only; the changed Pix ID as login name;
  a password account's change without its password, with a wrong one, and to the password itself in any case
- access token: missing, wrong scheme, not a JWT; signed with another secret, with "none", or changed after signing;
  another issuer; a claim missing; an unknown user; just before and at its 15 minutes; the secret unset
- refresh token: valid (rotated); unknown; empty; exchanged before, or several rotations before (ends the session);
  two refreshes with one token at once (ends the session); spent longer ago than a session lasts (forgotten); just
  before and at 30 days without use; kept alive by use; after sign-out
- no sign-in needed: pose templates and room codes, with no token or a bad one (FR-5.1)
- the account database down: unreachable (the driver cannot connect), or a connection lost; an OSError from
  anywhere else (a bug, so a 500)
- slow work holds no database: a sign-in waiting for its password check, and adding Google while Google answers
- devices: new token, the same token from another account, a rotated token, two registrations at once for one
  session, one token registered by two sessions at once, sign-out, invalid body
"""

import asyncio
import base64
import json
import logging
import threading
import time
from datetime import timedelta

import httpx2
import jwt
import pytest
from argon2 import PasswordHasher
from sqlalchemy import select
from sqlalchemy.exc import OperationalError
from sqlalchemy.ext.asyncio import async_sessionmaker

from pix_server.accounts.passwords import Passwords
from pix_server.config import Settings
from pix_server.db import open_database
from pix_server.db.models import AuthSession, Device, User
from pix_server.errors import handle_os_error, raised_by_database
from pix_server.main import HideQuery, jwt_secret

PASSWORD = "sunset2026"


def assert_error(response, status, code):
    assert response.status_code == status, response.text
    body = response.json()
    assert set(body) == {"error"}
    assert body["error"]["code"] == code
    assert body["error"]["message"]
    return body["error"]["message"]


def bearer(token):
    return {"Authorization": f"Bearer {token}"}


def log_in(client, pix_id, password=PASSWORD):
    return client.post("/api/v1/auth/login", json={"pixId": pix_id, "password": password})


def google_sign_up(client, token="google:sub-1:Junhyeong Han"):
    response = client.post("/api/v1/auth/google/signup", json={"idToken": token})
    assert response.status_code == 201, response.text
    return bearer(response.json()["accessToken"]), response.json()["user"]


def devices(client):
    async def read():
        async with client.app.state.database() as db:
            return [(device.user_id, device.fcm_token) for device in await db.scalars(select(Device))]

    return client.portal.call(read)


# --- sign-up with a password ---------------------------------------------------------------------------------------


def test_sign_up_returns_tokens_and_the_account(client, settings, clock):
    response = client.post(
        "/api/v1/auth/signup", json={"pixId": "dongje.p", "password": PASSWORD, "displayName": "  Dongje  "}
    )
    assert response.status_code == 201
    body = response.json()
    claims = jwt.decode(
        body["accessToken"], settings.jwt_secret.get_secret_value(), algorithms=["HS256"], options={"verify_exp": False}
    )
    assert claims == {
        "iss": "pix",
        "sub": body["user"]["id"],
        "sid": claims["sid"],
        "iat": 1000,
        "exp": 1000 + 15 * 60,
    }
    assert claims["sid"].startswith("ses_")
    assert body["expiresIn"] == 15 * 60
    assert len(body["refreshToken"]) >= 40
    user = body["user"]
    assert user["id"].startswith("u_")
    assert user == {
        "id": user["id"],
        "pixId": "dongje.p",
        "displayName": "Dongje",
        "hasPassword": True,
        "hasGoogle": False,
    }
    assert client.get("/api/v1/me", headers=bearer(body["accessToken"])).json() == user


def test_database_keeps_only_hashes_of_the_password_and_the_refresh_token(client):
    response = client.post(
        "/api/v1/auth/signup", json={"pixId": "dongje.p", "password": PASSWORD, "displayName": "Dongje"}
    )
    refresh_token = response.json()["refreshToken"]

    async def read():
        async with client.app.state.database() as db:
            return await db.scalar(select(User.password_hash)), await db.scalar(select(AuthSession.refresh_hash))

    password_hash, refresh_hash = client.portal.call(read)
    assert password_hash.startswith("$argon2id$")
    assert PASSWORD not in password_hash
    assert refresh_token not in refresh_hash


@pytest.mark.parametrize(
    "pix_id, rule",
    [
        ("abc", "at least 4"),
        ("a" * 21, "at most 20"),
        ("Dongje.p", "lowercase"),
        ("dong je", "lowercase"),
        ("dongje-p", "lowercase"),
    ],
)
def test_sign_up_with_an_invalid_pix_id_names_the_rule_and_saves_nothing(client, pix_id, rule):
    response = client.post("/api/v1/auth/signup", json={"pixId": pix_id, "password": PASSWORD, "displayName": "D"})
    assert rule in assert_error(response, 400, "PIX_ID_INVALID")
    assert_error(log_in(client, pix_id), 401, "INVALID_CREDENTIALS")


@pytest.mark.parametrize(
    "password, rule",
    [
        ("abcde12", "at least 8"),
        ("abcdefg1" * 8 + "x", "at most 64"),
        ("abcdefgh", "one letter and one digit"),
        ("12345678", "one letter and one digit"),
        ("dongje.p1", None),
    ],
)
def test_sign_up_with_an_invalid_password_names_the_rule(client, password, rule):
    response = client.post(
        "/api/v1/auth/signup", json={"pixId": "dongje.p1", "password": password, "displayName": "Dongje"}
    )
    message = assert_error(response, 400, "PASSWORD_INVALID")
    assert rule is None or rule in message


def test_sign_up_with_a_taken_pix_id_answers_taken(client, sign_up):
    sign_up("dongje.p")
    response = client.post("/api/v1/auth/signup", json={"pixId": "dongje.p", "password": PASSWORD, "displayName": "D"})
    assert_error(response, 409, "PIX_ID_TAKEN")


@pytest.mark.parametrize("name", [None, "", "   ", "x" * 31])
def test_sign_up_without_a_usable_display_name_is_invalid(client, name):
    body = {"pixId": "dongje.p", "password": PASSWORD}
    if name is not None:
        body["displayName"] = name
    assert_error(client.post("/api/v1/auth/signup", json=body), 400, "INVALID_REQUEST")


def test_sign_up_accepts_a_display_name_of_thirty_characters(client, sign_up):
    _, user = sign_up("dongje.p", name="x" * 30)
    assert user["displayName"] == "x" * 30


def test_malformed_sign_up_body_is_invalid(client):
    response = client.post("/api/v1/auth/signup", content=b"{", headers={"Content-Type": "application/json"})
    assert_error(response, 400, "INVALID_REQUEST")


# --- checking a Pix ID before sign-up --------------------------------------------------------------------------------


def check_pix_id(client, pix_id, headers=None):
    return client.post("/api/v1/auth/check-pix-id", headers=headers or {}, json={"pixId": pix_id})


def test_pix_id_check_answers_whether_sign_up_could_take_it(client, sign_up):
    sign_up("test1")
    assert check_pix_id(client, "test5").json() == {"available": True}
    assert check_pix_id(client, "test1").json() == {"available": False}
    # The answer is the sign-up's own.
    response = client.post("/api/v1/auth/signup", json={"pixId": "test5", "password": PASSWORD, "displayName": "T"})
    assert response.status_code == 201
    assert check_pix_id(client, "test5").json() == {"available": False}


def test_pix_id_check_follows_the_pix_id_a_google_account_chooses_or_changes(client):
    headers, _ = google_sign_up(client)
    client.put("/api/v1/me", headers=headers, json={"pixId": "test2"})
    assert check_pix_id(client, "test2").json() == {"available": False}
    client.put("/api/v1/me", headers=headers, json={"pixId": "test3"})
    assert check_pix_id(client, "test2").json() == {"available": True}
    assert check_pix_id(client, "test3").json() == {"available": False}


@pytest.mark.parametrize(
    "pix_id, rule",
    [("abc", "at least 4"), ("a" * 21, "at most 20"), ("Test1", "lowercase"), ("test 1", "lowercase")],
)
def test_pix_id_check_of_an_invalid_pix_id_names_the_rule_as_sign_up_does(client, pix_id, rule):
    assert rule in assert_error(check_pix_id(client, pix_id), 400, "PIX_ID_INVALID")


@pytest.mark.parametrize("headers", [{}, bearer("not-a-jwt")])
def test_pix_id_check_needs_no_sign_in(client, headers):
    assert check_pix_id(client, "test5", headers).json() == {"available": True}


# --- log-in ----------------------------------------------------------------------------------------------------------


def test_log_in_issues_a_new_token_and_the_first_one_stays_valid(client, sign_up):
    first, user = sign_up("dongje.p")
    response = log_in(client, "dongje.p")
    assert response.status_code == 200
    second = bearer(response.json()["accessToken"])
    assert second != first
    assert response.json()["user"] == user
    assert client.get("/api/v1/me", headers=first).status_code == 200
    assert client.get("/api/v1/me", headers=second).status_code == 200


def test_log_in_ignores_capitals_and_spaces_around_the_pix_id(client, sign_up):
    sign_up("dongje.p")
    assert log_in(client, "  Dongje.P ").status_code == 200


@pytest.mark.parametrize("pix_id, password", [("dongje.p", "sunset2027"), ("nobody.here", PASSWORD)])
def test_wrong_password_and_unknown_pix_id_get_the_same_answer(client, sign_up, pix_id, password):
    sign_up("dongje.p")
    message = assert_error(log_in(client, pix_id, password), 401, "INVALID_CREDENTIALS")
    assert message == "The Pix ID or the password is wrong"


def test_an_account_without_a_password_cannot_log_in_with_one(client):
    headers, _ = google_sign_up(client)
    client.put("/api/v1/me", headers=headers, json={"pixId": "junhyeong.h"})
    assert_error(log_in(client, "junhyeong.h"), 401, "INVALID_CREDENTIALS")


# --- Google --------------------------------------------------------------------------------------------------------


def test_google_sign_in_of_an_unknown_google_account_answers_not_registered(client):
    response = client.post("/api/v1/auth/google", json={"idToken": "google:sub-1:Junhyeong"})
    assert_error(response, 404, "NOT_REGISTERED")


def test_google_sign_up_creates_an_account_without_a_pix_id_and_signs_in_later(client):
    headers, user = google_sign_up(client, "google:sub-1:Junhyeong Han")
    assert user == {
        "id": user["id"],
        "pixId": None,
        "displayName": "Junhyeong Han",
        "hasPassword": False,
        "hasGoogle": True,
    }
    response = client.post("/api/v1/auth/google", json={"idToken": "google:sub-1"})
    assert response.status_code == 200
    assert response.json()["user"]["id"] == user["id"]


@pytest.mark.parametrize("token, name", [("google:sub-1", "Pix user"), ("google:sub-1:" + "y" * 40, "y" * 30)])
def test_google_sign_up_names_the_account_from_google_within_the_limit(client, token, name):
    _, user = google_sign_up(client, token)
    assert user["displayName"] == name


def test_google_sign_up_twice_with_one_google_account_answers_in_use(client):
    google_sign_up(client)
    response = client.post("/api/v1/auth/google/signup", json={"idToken": "google:sub-1"})
    assert_error(response, 409, "GOOGLE_IN_USE")


@pytest.mark.parametrize("path", ["/api/v1/auth/google", "/api/v1/auth/google/signup"])
def test_invalid_google_token_answers_invalid_token(client, path):
    assert_error(client.post(path, json={"idToken": "forged"}), 401, "INVALID_TOKEN")
    assert_error(client.post(path, json={"idToken": ""}), 400, "INVALID_REQUEST")


def test_google_sign_in_answers_unavailable_while_google_cannot_be_reached(client, google):
    google.available = False
    response = client.post("/api/v1/auth/google/signup", json={"idToken": "google:sub-1"})
    assert_error(response, 503, "UNAVAILABLE")


# --- adding Google to an account -----------------------------------------------------------------------------------


def test_not_registered_google_account_is_added_to_an_existing_password_account(client, sign_up):
    _, user = sign_up("dongje.p")
    # The app asks whether the person has a Pix account, signs in with it, and adds the same Google account.
    assert_error(client.post("/api/v1/auth/google", json={"idToken": "google:sub-1"}), 404, "NOT_REGISTERED")
    headers = bearer(log_in(client, "dongje.p").json()["accessToken"])
    response = client.put("/api/v1/me/google", headers=headers, json={"idToken": "google:sub-1"})
    assert response.status_code == 200
    assert response.json() == {**user, "hasGoogle": True}
    by_google = client.post("/api/v1/auth/google", json={"idToken": "google:sub-1"})
    assert by_google.json()["user"]["id"] == user["id"]
    assert log_in(client, "dongje.p").status_code == 200


def test_adding_the_same_google_account_again_changes_nothing(client, sign_up):
    headers, _ = sign_up("dongje.p")
    client.put("/api/v1/me/google", headers=headers, json={"idToken": "google:sub-1"})
    response = client.put("/api/v1/me/google", headers=headers, json={"idToken": "google:sub-1"})
    assert response.status_code == 200
    assert response.json()["hasGoogle"] is True


def test_adding_a_google_account_that_another_pix_account_uses_answers_in_use(client, sign_up):
    google_sign_up(client, "google:sub-1")
    headers, _ = sign_up("dongje.p")
    response = client.put("/api/v1/me/google", headers=headers, json={"idToken": "google:sub-1"})
    assert_error(response, 409, "GOOGLE_IN_USE")
    assert client.get("/api/v1/me", headers=headers).json()["hasGoogle"] is False


def test_adding_a_second_google_account_answers_already_added(client, sign_up):
    headers, _ = sign_up("dongje.p")
    client.put("/api/v1/me/google", headers=headers, json={"idToken": "google:sub-1"})
    response = client.put("/api/v1/me/google", headers=headers, json={"idToken": "google:sub-2"})
    assert_error(response, 409, "GOOGLE_ALREADY_ADDED")


def test_two_google_accounts_added_at_once_leave_the_first_and_refuse_the_second(client, sign_up):
    first, _ = sign_up("dongje.p")
    second = bearer(log_in(client, "dongje.p").json()["accessToken"])

    async def both():
        transport = httpx2.ASGITransport(app=client.app)
        async with httpx2.AsyncClient(transport=transport, base_url="http://pix") as http:
            return await asyncio.gather(
                http.put("/api/v1/me/google", headers=first, json={"idToken": "google:sub-1"}),
                http.put("/api/v1/me/google", headers=second, json={"idToken": "google:sub-2"}),
            )

    answers = client.portal.call(both)
    assert sorted(answer.status_code for answer in answers) == [200, 409]
    (added,) = [
        subject for subject, answer in zip(("sub-1", "sub-2"), answers, strict=True) if answer.status_code == 200
    ]
    (refused,) = {"sub-1", "sub-2"} - {added}
    assert client.post("/api/v1/auth/google", json={"idToken": f"google:{added}"}).status_code == 200
    assert_error(client.post("/api/v1/auth/google", json={"idToken": f"google:{refused}"}), 404, "NOT_REGISTERED")


def test_adding_google_with_an_invalid_token_answers_invalid_token(client, sign_up):
    headers, _ = sign_up("dongje.p")
    assert_error(client.put("/api/v1/me/google", headers=headers, json={"idToken": "x"}), 401, "INVALID_TOKEN")


# --- adding a password to a Google account ------------------------------------------------------------------------


def test_google_account_adds_a_password_and_then_logs_in_with_either(client):
    headers, user = google_sign_up(client, "google:sub-1:Junhyeong")
    client.put("/api/v1/me", headers=headers, json={"pixId": "junhyeong.h"})
    response = client.put("/api/v1/me/password", headers=headers, json={"password": PASSWORD})
    assert response.status_code == 200
    assert response.json() == {**user, "pixId": "junhyeong.h", "hasPassword": True}
    by_password = log_in(client, "junhyeong.h")
    assert by_password.status_code == 200
    assert by_password.json()["user"]["id"] == user["id"]
    by_google = client.post("/api/v1/auth/google", json={"idToken": "google:sub-1"})
    assert by_google.json()["user"]["id"] == user["id"]


def test_adding_a_password_before_choosing_a_pix_id_answers_pix_id_required(client):
    headers, _ = google_sign_up(client)
    assert_error(
        client.put("/api/v1/me/password", headers=headers, json={"password": PASSWORD}), 403, "PIX_ID_REQUIRED"
    )
    assert client.get("/api/v1/me", headers=headers).json()["hasPassword"] is False


@pytest.mark.parametrize("password, rule", [("abcde12", "at least 8"), ("junhyeong.h1", "Pix ID")])
def test_adding_a_password_that_breaks_a_rule_names_it_and_saves_nothing(client, password, rule):
    headers, _ = google_sign_up(client)
    client.put("/api/v1/me", headers=headers, json={"pixId": "junhyeong.h1"})
    response = client.put("/api/v1/me/password", headers=headers, json={"password": password})
    assert rule in assert_error(response, 400, "PASSWORD_INVALID")
    assert client.get("/api/v1/me", headers=headers).json()["hasPassword"] is False
    assert_error(log_in(client, "junhyeong.h1", password), 401, "INVALID_CREDENTIALS")


def test_account_with_a_password_cannot_set_another(client, sign_up):
    headers, _ = sign_up("dongje.p")
    response = client.put("/api/v1/me/password", headers=headers, json={"password": "another2026"})
    assert_error(response, 409, "PASSWORD_ALREADY_SET")
    assert log_in(client, "dongje.p").status_code == 200
    assert_error(log_in(client, "dongje.p", "another2026"), 401, "INVALID_CREDENTIALS")


def test_pix_id_change_and_new_password_at_once_never_leave_the_two_equal(client):
    """A Google account signed in on two phones: one changes the Pix ID to "sunset2026" while the other adds that as
    the password. Whichever runs second sees the first and refuses, so they never end up the same."""
    first, _ = google_sign_up(client)
    client.put("/api/v1/me", headers=first, json={"pixId": "junhyeong.h"})
    second = bearer(client.post("/api/v1/auth/google", json={"idToken": "google:sub-1"}).json()["accessToken"])

    async def both():
        transport = httpx2.ASGITransport(app=client.app)
        async with httpx2.AsyncClient(transport=transport, base_url="http://pix") as http:
            return await asyncio.gather(
                http.put("/api/v1/me", headers=first, json={"pixId": "sunset2026"}),
                http.put("/api/v1/me/password", headers=second, json={"password": "sunset2026"}),
            )

    pix_id_change, new_password = client.portal.call(both)
    assert sorted([pix_id_change.status_code, new_password.status_code]) in ([200, 400], [200, 401])
    account = client.get("/api/v1/me", headers=first).json()
    assert not (account["pixId"] == "sunset2026" and account["hasPassword"])


# --- PUT /me -------------------------------------------------------------------------------------------------------


def test_google_account_chooses_its_pix_id(client):
    headers, user = google_sign_up(client)
    response = client.put("/api/v1/me", headers=headers, json={"pixId": "junhyeong.h"})
    assert response.status_code == 200
    assert response.json() == {**user, "pixId": "junhyeong.h"}
    assert client.get("/api/v1/me", headers=headers).json()["pixId"] == "junhyeong.h"


@pytest.mark.parametrize("pix_id, status, code", [("ab", 400, "PIX_ID_INVALID"), ("taken.id", 409, "PIX_ID_TAKEN")])
def test_rejected_pix_id_keeps_the_old_one(client, sign_up, pix_id, status, code):
    sign_up("taken.id")
    headers, _ = sign_up("dongje.p")
    response = client.put("/api/v1/me", headers=headers, json={"pixId": pix_id, "password": PASSWORD})
    assert_error(response, status, code)
    assert client.get("/api/v1/me", headers=headers).json()["pixId"] == "dongje.p"


def test_put_me_changes_only_the_display_name_when_the_pix_id_is_left_out_or_the_same(client, sign_up):
    headers, user = sign_up("dongje.p")
    response = client.put("/api/v1/me", headers=headers, json={"displayName": "Dongje Park"})
    assert response.json() == {**user, "displayName": "Dongje Park"}
    response = client.put("/api/v1/me", headers=headers, json={"pixId": "dongje.p"})
    assert response.status_code == 200


def test_changed_pix_id_is_the_new_log_in_name(client, sign_up):
    headers, _ = sign_up("dongje.p")
    response = client.put("/api/v1/me", headers=headers, json={"pixId": "dongje.park", "password": PASSWORD})
    assert response.json()["pixId"] == "dongje.park"
    assert_error(log_in(client, "dongje.p"), 401, "INVALID_CREDENTIALS")
    assert log_in(client, "dongje.park").status_code == 200


@pytest.mark.parametrize("password", [None, "sunset2027"])
def test_password_account_changes_its_pix_id_only_with_its_password(client, sign_up, password):
    headers, _ = sign_up("dongje.p")
    body = {"pixId": "dongje.park"} | ({"password": password} if password else {})
    assert_error(client.put("/api/v1/me", headers=headers, json=body), 401, "INVALID_CREDENTIALS")
    assert client.get("/api/v1/me", headers=headers).json()["pixId"] == "dongje.p"


@pytest.mark.parametrize("password", ["sunset2026", "Sunset2026"])
def test_pix_id_cannot_become_the_password(client, sign_up, password):
    headers, _ = sign_up("dongje.p", password=password)
    response = client.put("/api/v1/me", headers=headers, json={"pixId": "sunset2026", "password": password})
    assert "same as the password" in assert_error(response, 400, "PIX_ID_INVALID")
    assert client.get("/api/v1/me", headers=headers).json()["pixId"] == "dongje.p"


# --- session tokens ------------------------------------------------------------------------------------------------


@pytest.mark.parametrize(
    "method, path",
    [
        ("GET", "/api/v1/me"),
        ("PUT", "/api/v1/me"),
        ("PUT", "/api/v1/me/google"),
        ("PUT", "/api/v1/me/password"),
        ("DELETE", "/api/v1/auth/session"),
        ("POST", "/api/v1/devices"),
        ("POST", "/api/v1/users/lookup"),
        ("GET", "/api/v1/friend-requests"),
        ("POST", "/api/v1/friend-requests"),
        ("POST", "/api/v1/friend-requests/fr_1/accept"),
        ("POST", "/api/v1/friend-requests/fr_1/decline"),
        ("DELETE", "/api/v1/friend-requests/fr_1"),
        ("GET", "/api/v1/friends"),
        ("DELETE", "/api/v1/friends/u_1"),
    ],
)
@pytest.mark.parametrize("headers", [{}, {"Authorization": "Basic abc"}, bearer("not-a-jwt")])
def test_account_endpoints_need_a_valid_session_token(client, method, path, headers):
    assert_error(client.request(method, path, headers=headers, json={}), 401, "UNAUTHENTICATED")


def test_access_token_expires_after_fifteen_minutes(client, clock, sign_up):
    headers, _ = sign_up("dongje.p")
    clock.advance(15 * 60 - 1)
    assert client.get("/api/v1/me", headers=headers).status_code == 200
    clock.advance(1)
    assert_error(client.get("/api/v1/me", headers=headers), 401, "UNAUTHENTICATED")


# --- refresh tokens ------------------------------------------------------------------------------------------------


def sign_up_with_tokens(client, pix_id="dongje.p"):
    response = client.post("/api/v1/auth/signup", json={"pixId": pix_id, "password": PASSWORD, "displayName": "D"})
    return response.json()


def refresh(client, refresh_token):
    return client.post("/api/v1/auth/refresh", json={"refreshToken": refresh_token})


def test_refresh_gives_a_new_pair_for_the_same_sign_in(client, settings, clock):
    tokens = sign_up_with_tokens(client)
    clock.advance(15 * 60)
    response = refresh(client, tokens["refreshToken"])
    assert response.status_code == 200
    renewed = response.json()
    assert set(renewed) == {"accessToken", "refreshToken", "expiresIn"}
    assert renewed["refreshToken"] != tokens["refreshToken"]
    assert claims_of(settings, renewed["accessToken"])["sid"] == claims_of(settings, tokens["accessToken"])["sid"]
    assert client.get("/api/v1/me", headers=bearer(renewed["accessToken"])).json()["pixId"] == "dongje.p"


def test_unknown_or_empty_refresh_token_is_refused(client):
    sign_up_with_tokens(client)
    assert_error(refresh(client, "unknown-refresh-token"), 401, "INVALID_REFRESH_TOKEN")
    assert_error(refresh(client, ""), 400, "INVALID_REQUEST")


def test_refresh_token_used_twice_ends_the_sign_in(client):
    tokens = sign_up_with_tokens(client)
    renewed = refresh(client, tokens["refreshToken"]).json()
    # The old token comes back, from the phone after a lost answer or from someone who copied it.
    assert_error(refresh(client, tokens["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")
    assert_error(refresh(client, renewed["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")
    assert log_in(client, "dongje.p").status_code == 200


def test_sign_in_ends_after_thirty_days_without_a_refresh(client, clock):
    tokens = sign_up_with_tokens(client)
    clock.advance(timedelta(days=30).total_seconds() - 1)
    renewed = refresh(client, tokens["refreshToken"])
    assert renewed.status_code == 200
    clock.advance(timedelta(days=30).total_seconds())
    assert_error(refresh(client, renewed.json()["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")


def test_refresh_token_from_rotations_ago_still_ends_the_sign_in(client):
    first = sign_up_with_tokens(client)
    second = refresh(client, first["refreshToken"]).json()
    third = refresh(client, second["refreshToken"]).json()
    assert_error(refresh(client, first["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")
    assert_error(refresh(client, third["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")


def test_two_refreshes_with_one_token_at_once_end_the_sign_in(client):
    """Whichever wins, the other finds the token spent, so neither keeps the session."""
    tokens = sign_up_with_tokens(client)

    async def both():
        transport = httpx2.ASGITransport(app=client.app)
        async with httpx2.AsyncClient(transport=transport, base_url="http://pix") as http:
            return await asyncio.gather(
                *(http.post("/api/v1/auth/refresh", json={"refreshToken": tokens["refreshToken"]}) for _ in range(2))
            )

    answers = client.portal.call(both)
    assert sorted(answer.status_code for answer in answers) == [200, 401]
    (winner,) = [answer.json() for answer in answers if answer.status_code == 200]
    assert_error(refresh(client, winner["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")


def test_spent_tokens_are_forgotten_once_they_would_have_expired(client, clock):
    day = 86400
    first = sign_up_with_tokens(client)
    clock.advance(20 * day)
    second = refresh(client, first["refreshToken"]).json()
    clock.advance(20 * day)
    third = refresh(client, second["refreshToken"]).json()
    clock.advance(20 * day)
    # The first token was spent 40 days ago, longer than a sign-in lasts, so it is no longer recognized and no
    # longer ends the session; it would have expired 30 days after it was issued anyway.
    fourth = refresh(client, third["refreshToken"]).json()
    assert_error(refresh(client, first["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")
    assert refresh(client, fourth["refreshToken"]).status_code == 200


def test_a_token_spent_long_ago_is_forgotten_even_before_a_refresh_prunes_it(client, clock):
    day = 86400
    first = sign_up_with_tokens(client)
    clock.advance(20 * day)
    second = refresh(client, first["refreshToken"]).json()
    clock.advance(20 * day)
    third = refresh(client, second["refreshToken"]).json()
    clock.advance(20 * day)
    # No refresh since day 40, so the first token, spent on day 20, is still stored; it is 40 days old, so it no
    # longer ends the session, which lasts until day 70.
    assert_error(refresh(client, first["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")
    assert refresh(client, third["refreshToken"]).status_code == 200


def test_sign_out_ends_the_refresh_token_of_this_phone_only(client):
    phone = sign_up_with_tokens(client)
    other_phone = log_in(client, "dongje.p").json()
    assert client.delete("/api/v1/auth/session", headers=bearer(phone["accessToken"])).status_code == 204
    assert_error(refresh(client, phone["refreshToken"]), 401, "INVALID_REFRESH_TOKEN")
    assert refresh(client, other_phone["refreshToken"]).status_code == 200


def claims_of(settings, token):
    return jwt.decode(
        token, settings.jwt_secret.get_secret_value(), algorithms=["HS256"], options={"verify_exp": False}
    )


def forged(claims, secret="another-secret-that-is-long-enough-for-hs256", algorithm="HS256"):
    return jwt.encode(claims, secret, algorithm=algorithm)


def changed_after_signing(token, **claims):
    """The token with its payload replaced and the original signature kept."""
    header, payload, signature = token.split(".")
    body = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4))) | claims
    encoded = base64.urlsafe_b64encode(json.dumps(body).encode()).rstrip(b"=").decode()
    return f"{header}.{encoded}.{signature}"


@pytest.mark.parametrize(
    "make",
    [
        lambda settings, token, other: forged(claims_of(settings, token)),
        lambda settings, token, other: jwt.encode(claims_of(settings, token), None, algorithm="none"),
        lambda settings, token, other: changed_after_signing(token, sub=other["id"]),
        lambda settings, token, other: forged(
            claims_of(settings, token) | {"iss": "evil"}, settings.jwt_secret.get_secret_value()
        ),
        lambda settings, token, other: forged(
            {k: v for k, v in claims_of(settings, token).items() if k != "sid"}, settings.jwt_secret.get_secret_value()
        ),
        lambda settings, token, other: forged(
            claims_of(settings, token) | {"sub": "u_UNKNOWN"}, settings.jwt_secret.get_secret_value()
        ),
    ],
    ids=["another-secret", "alg-none", "changed-payload", "another-issuer", "no-sid", "unknown-user"],
)
def test_token_not_signed_by_this_server_for_a_user_is_refused(client, settings, sign_up, make):
    headers, _ = sign_up("dongje.p")
    _, other = sign_up("junhyeong.h")
    token = headers["Authorization"].removeprefix("Bearer ")
    assert_error(client.get("/api/v1/me", headers=bearer(make(settings, token, other))), 401, "UNAUTHENTICATED")


def test_each_sign_in_is_a_session_of_its_own(client, settings, sign_up):
    headers, _ = sign_up("dongje.p")
    first = claims_of(settings, headers["Authorization"].removeprefix("Bearer "))
    second = claims_of(settings, log_in(client, "dongje.p").json()["accessToken"])
    assert first["sub"] == second["sub"]
    assert first["sid"] != second["sid"]


def test_without_a_secret_each_process_signs_with_its_own(monkeypatch):
    monkeypatch.delenv("PIX_JWT_SECRET", raising=False)
    unset = Settings(_env_file=None)
    assert len(jwt_secret(unset)) == 64
    assert jwt_secret(unset) != jwt_secret(unset)
    assert jwt_secret(Settings(_env_file=None, jwt_secret="configured-secret")) == "configured-secret"


# --- devices -------------------------------------------------------------------------------------------------------


def register(client, headers, token):
    return client.post("/api/v1/devices", headers=headers, json={"fcmToken": token, "platform": "android"})


def test_device_token_is_kept_for_the_signed_in_user(client, sign_up):
    headers, user = sign_up("dongje.p")
    assert register(client, headers, "fcm-1").status_code == 204
    assert register(client, headers, "fcm-1").status_code == 204
    assert devices(client) == [(user["id"], "fcm-1")]


def test_device_token_moves_to_the_account_that_signs_in_on_the_phone(client, sign_up):
    first, _ = sign_up("dongje.p")
    second, user = sign_up("junhyeong.h")
    register(client, first, "fcm-1")
    register(client, second, "fcm-1")
    assert devices(client) == [(user["id"], "fcm-1")]


def test_rotated_device_token_replaces_the_old_one(client, sign_up):
    headers, user = sign_up("dongje.p")
    register(client, headers, "fcm-1")
    register(client, headers, "fcm-2")
    assert devices(client) == [(user["id"], "fcm-2")]


def test_sign_out_forgets_the_phones_device_token(client, sign_up):
    phone, user = sign_up("dongje.p")
    other_phone = bearer(log_in(client, "dongje.p").json()["accessToken"])
    register(client, phone, "fcm-1")
    register(client, other_phone, "fcm-2")
    client.delete("/api/v1/auth/session", headers=phone)
    assert devices(client) == [(user["id"], "fcm-2")]


def test_signed_out_phone_cannot_register_a_device_with_its_unexpired_access_token(client, sign_up):
    phone, _ = sign_up("dongje.p")
    client.delete("/api/v1/auth/session", headers=phone)
    assert_error(register(client, phone, "fcm-1"), 401, "UNAUTHENTICATED")
    assert devices(client) == []


# --- the account database unavailable -------------------------------------------------------------------------------


class LostConnection:
    """An account database whose connection drops in the middle of a query."""

    def __call__(self):
        return self

    async def __aenter__(self):
        raise OperationalError("SELECT 1", {}, Exception("connection lost"))

    async def __aexit__(self, *exc):
        return False


@pytest.fixture
def unreachable_database():
    """A PostgreSQL address where nothing listens, so the driver itself fails to connect, as when the database
    container is down."""
    engine = open_database("postgresql+asyncpg://pix:pix@127.0.0.1:1/pix")
    yield async_sessionmaker(engine, expire_on_commit=False)


@pytest.mark.parametrize("failure", ["unreachable", "connection-lost"])
def test_account_endpoints_answer_unavailable_while_the_database_is_down(
    client, sign_up, unreachable_database, failure
):
    headers, _ = sign_up("dongje.p")
    database = client.app.state.database
    client.app.state.database = unreachable_database if failure == "unreachable" else LostConnection()
    try:
        assert_error(log_in(client, "dongje.p"), 503, "UNAVAILABLE")
        assert_error(client.get("/api/v1/friends", headers=headers), 503, "UNAVAILABLE")
        # Pose templates and rooms do not use the database.
        assert client.get("/api/v1/pose-templates").status_code == 200
    finally:
        client.app.state.database = database
    assert client.get("/api/v1/me", headers=headers).status_code == 200


async def test_only_an_os_error_from_the_database_driver_means_unavailable(unreachable_database):
    async with unreachable_database() as db:
        with pytest.raises(OSError) as failed:
            await db.execute(select(User))
    assert raised_by_database(failed.value)
    assert (await handle_os_error(None, failed.value)).status_code == 503
    # Any other OSError is a bug, so it is raised again and answers 500.
    other = OSError("No such file or directory")
    assert not raised_by_database(other)
    with pytest.raises(OSError):
        await handle_os_error(None, other)


# --- slow work holds no database ----------------------------------------------------------------------------------


class SlowChecks:
    """argon2 at its lowest cost, whose checks take a while; `checking` is set once one has started."""

    def __init__(self) -> None:
        self._hasher = PasswordHasher(time_cost=1, memory_cost=8, parallelism=1)
        self.checking = threading.Event()

    def hash(self, password: str) -> str:
        return self._hasher.hash(password)

    def verify(self, stored: str, password: str) -> bool:
        self.checking.set()
        time.sleep(0.4)
        return self._hasher.verify(stored, password)


def finishes_before(client, slow, started, quick):
    """Starts `slow`, waits until `started` says it is in its slow part, then sends `quick`, and returns whether
    `quick` finished first. Each is `(method, path, headers, json)`."""

    async def run():
        transport = httpx2.ASGITransport(app=client.app)
        async with httpx2.AsyncClient(transport=transport, base_url="http://pix") as http:
            slow_task = asyncio.create_task(http.request(slow[0], slow[1], headers=slow[2], json=slow[3]))
            await started()
            quick_task = asyncio.create_task(http.request(quick[0], quick[1], headers=quick[2], json=quick[3]))
            done, _ = await asyncio.wait({slow_task, quick_task}, return_when=asyncio.FIRST_COMPLETED)
            await asyncio.gather(slow_task, quick_task)
            return quick_task in done and slow_task not in done, quick_task.result().status_code

    return client.portal.call(run)


def test_a_sign_in_waiting_for_its_password_check_holds_up_no_other_request(client, sign_up):
    """Sign-in needs no account and has no attempt limit, so a burst of them must not hold the database while their
    checks wait their turn: on SQLite the write lock, on PostgreSQL the pool's connections."""
    headers, _ = sign_up("dongje.p")
    checks = SlowChecks()
    client.app.state.passwords = Passwords(checks, at_once=1)
    first, status = finishes_before(
        client,
        ("POST", "/api/v1/auth/login", None, {"pixId": "dongje.p", "password": PASSWORD}),
        lambda: asyncio.to_thread(checks.checking.wait),
        ("GET", "/api/v1/me", headers, None),
    )
    assert (first, status) == (True, 200)


def test_adding_google_holds_no_database_while_google_answers(client, sign_up, google):
    adding, _ = sign_up("dongje.p")
    other, _ = sign_up("junhyeong.h")
    google.delay = 0.4
    first, status = finishes_before(
        client,
        ("PUT", "/api/v1/me/google", adding, {"idToken": "google:sub-1"}),
        google.asked.wait,
        ("GET", "/api/v1/me", other, None),
    )
    assert (first, status) == (True, 200)


# --- no sign-in needed ---------------------------------------------------------------------------------------------


@pytest.mark.parametrize("headers", [{}, bearer("not-a-jwt")])
def test_pose_templates_and_room_codes_need_no_sign_in(client, headers):
    assert client.get("/api/v1/pose-templates", headers=headers).status_code == 200
    with client.websocket_connect("/ws", headers=headers) as socket:
        socket.send_json({"type": "room.create"})
        assert socket.receive_json()["type"] == "room.created"


def test_two_registrations_at_once_leave_one_device_for_the_session(client, sign_up):
    headers, user = sign_up("dongje.p")

    async def both():
        transport = httpx2.ASGITransport(app=client.app)
        async with httpx2.AsyncClient(transport=transport, base_url="http://pix") as http:
            return await asyncio.gather(
                *(
                    http.post("/api/v1/devices", headers=headers, json={"fcmToken": token, "platform": "android"})
                    for token in ("fcm-1", "fcm-2")
                )
            )

    assert [answer.status_code for answer in client.portal.call(both)] == [204, 204]
    (device,) = devices(client)
    assert device[0] == user["id"]
    assert device[1] in {"fcm-1", "fcm-2"}


def test_one_token_registered_by_two_sessions_at_once_ends_up_in_one(client, sign_up):
    first, user = sign_up("dongje.p")
    second = bearer(log_in(client, "dongje.p").json()["accessToken"])

    async def both():
        transport = httpx2.ASGITransport(app=client.app)
        async with httpx2.AsyncClient(transport=transport, base_url="http://pix") as http:
            return await asyncio.gather(
                *(
                    http.post("/api/v1/devices", headers=headers, json={"fcmToken": "fcm-1", "platform": "android"})
                    for headers in (first, second)
                )
            )

    assert [answer.status_code for answer in client.portal.call(both)] == [204, 204]
    assert devices(client) == [(user["id"], "fcm-1")]


@pytest.mark.parametrize("body", [{"fcmToken": "", "platform": "android"}, {"fcmToken": "fcm-1", "platform": "ios"}])
def test_invalid_device_registration_is_rejected(client, sign_up, body):
    headers, _ = sign_up("dongje.p")
    assert_error(client.post("/api/v1/devices", headers=headers, json=body), 400, "INVALID_REQUEST")


# --- logs ----------------------------------------------------------------------------------------------------------


def test_access_log_leaves_out_the_query():
    record = logging.LogRecord(
        "uvicorn.access", logging.INFO, "", 0, '%s - "%s %s HTTP/%s" %d',
        ("10.0.0.1:5000", "GET", "/api/v1/pose-templates?pixId=dongje.p", "1.1", 200), None,
    )  # fmt: skip
    assert HideQuery().filter(record)
    assert record.getMessage() == '10.0.0.1:5000 - "GET /api/v1/pose-templates HTTP/1.1" 200'
