# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""The push sender against a fake of Google's token endpoint and FCM, and the friend request's push (Design 2.6.6,
AD-13; R&S FR-5.7).

Partitions:
- signing in: the signed request (its claims, key, and key id); the access token kept, until 61 s and from 60 s
  before it expires; dropped once FCM refuses it; Google refusing to give one
- sending: to each token; a token FCM no longer knows (returned for removal); FCM refusing otherwise (kept); FCM too
  slow; no tokens (nothing asked)
- the key file: unset (no pushes, no warning); missing, not JSON, not a service account (no pushes, a warning that
  shows no key)
- friend requests: a new request pushes to the receiver's phones; a request that makes friends pushes nothing
"""

import json
import logging

import httpx2
import jwt
import pytest

from pix_server.invitations.push import PushSender, load_service_account

DATA = {"type": "invitation", "invitationId": "inv_01JB2C3D4E5F6G7H8J9KAMNPQR"}


@pytest.fixture
async def push(fcm_api, fcm_key, clock):
    async with httpx2.AsyncClient(transport=httpx2.MockTransport(fcm_api)) as http:
        yield PushSender(load_service_account(fcm_key.path), http, clock, timeout=5)


def assertion_of(request):
    form = dict(pair.split("=", 1) for pair in request.content.decode().split("&"))
    assert form["grant_type"] == "urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
    return form["assertion"]


# --- signing in ----------------------------------------------------------------------------------------------------


async def test_the_sender_signs_in_with_the_service_accounts_key(push, fcm_api, fcm_key, clock):
    assert await push.send(["phone"], DATA, 30) == []
    (request,) = fcm_api.token_requests
    assert str(request.url) == "https://oauth2.googleapis.com/token"
    assertion = assertion_of(request)
    assert jwt.get_unverified_header(assertion)["kid"] == "test-key-id"
    claims = jwt.decode(
        assertion,
        fcm_key.public_pem,
        algorithms=["RS256"],
        audience="https://oauth2.googleapis.com/token",
        options={"verify_exp": False, "verify_iat": False},
    )
    assert claims == {
        "iss": "pix-fcm-sender@pix-test.iam.gserviceaccount.com",
        "scope": "https://www.googleapis.com/auth/firebase.messaging",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": int(clock.now),
        "exp": int(clock.now) + 3600,
    }


async def test_the_access_token_is_kept_until_a_minute_before_it_expires(push, fcm_api, clock):
    await push.send(["phone"], DATA, 30)
    # Google's token lasts 3599 s.
    clock.advance(3599 - 61)
    await push.send(["phone"], DATA, 30)
    assert len(fcm_api.token_requests) == 1
    clock.advance(1)
    await push.send(["phone"], DATA, 30)
    assert len(fcm_api.token_requests) == 2
    assert all(sent["authorization"] == "Bearer test-fcm-access" for sent in fcm_api.pushes)


async def test_an_access_token_fcm_refuses_is_replaced_at_the_next_push(push, fcm_api):
    fcm_api.status = 401
    assert await push.send(["phone"], DATA, 30) == []
    fcm_api.status = 200
    await push.send(["phone"], DATA, 30)
    assert len(fcm_api.token_requests) == 2


async def test_google_refusing_an_access_token_sends_nothing(push, fcm_api, caplog):
    fcm_api.token_status = 400
    with caplog.at_level(logging.WARNING, logger="pix_server.invitations.push"):
        assert await push.send(["phone"], DATA, 30) == []
    assert fcm_api.pushes == []
    assert "400" in caplog.text


# --- sending -------------------------------------------------------------------------------------------------------


async def test_each_token_gets_a_high_priority_data_message_with_its_ttl(push, fcm_api):
    await push.send(["phone", "tablet"], DATA, 30)
    assert sorted(sent["token"] for sent in fcm_api.pushes) == ["phone", "tablet"]
    for sent in fcm_api.pushes:
        assert sent["data"] == DATA
        assert sent["android"] == {"priority": "HIGH", "ttl": "30s"}
        assert "notification" not in sent


async def test_only_the_tokens_fcm_no_longer_knows_come_back_for_removal(push, fcm_api, caplog):
    fcm_api.unregistered.add("old-phone")
    assert await push.send(["phone", "old-phone"], DATA, 30) == ["old-phone"]
    fcm_api.status = 500
    with caplog.at_level(logging.WARNING, logger="pix_server.invitations.push"):
        assert await push.send(["phone"], DATA, 30) == []
    assert "500" in caplog.text
    assert "phone" not in caplog.text


async def test_fcm_too_slow_loses_the_push_without_failing(fcm_api, fcm_key, clock):
    fcm_api.delay = 0.2
    async with httpx2.AsyncClient(transport=httpx2.MockTransport(fcm_api)) as http:
        push = PushSender(load_service_account(fcm_key.path), http, clock, timeout=0.05)
        assert await push.send(["phone"], DATA, 30) == []


async def test_no_tokens_asks_nobody(push, fcm_api):
    assert await push.send([], DATA, 30) == []
    assert fcm_api.token_requests == []


# --- the key file --------------------------------------------------------------------------------------------------


def test_an_unset_key_file_sends_no_pushes_without_a_warning(caplog):
    with caplog.at_level(logging.WARNING, logger="pix_server.invitations.push"):
        assert load_service_account(None) is None
    assert caplog.text == ""


@pytest.mark.parametrize("content", [None, "not json", json.dumps({"type": "authorized_user", "private_key": "x"})])
def test_an_unusable_key_file_sends_no_pushes_and_says_so(tmp_path, caplog, content):
    path = tmp_path / "fcm-sender.json"
    if content is not None:
        path.write_text(content)
    with caplog.at_level(logging.WARNING, logger="pix_server.invitations.push"):
        assert load_service_account(str(path)) is None
    assert "PIX_FCM_CREDENTIALS_FILE" in caplog.text
    assert "private_key" not in caplog.text


# --- friend requests -----------------------------------------------------------------------------------------------


def test_a_friend_request_is_pushed_to_the_receivers_phones(client, sign_up, fcm_api):
    dongje, _ = sign_up("dongje.p", "Dongje")
    junhyeong, junhyeong_user = sign_up("junhyeong.h", "Junhyeong")
    client.post("/api/v1/devices", headers=junhyeong, json={"fcmToken": "junhyeong-phone", "platform": "android"})
    client.post("/api/v1/devices", headers=dongje, json={"fcmToken": "dongje-phone", "platform": "android"})
    sent = client.post("/api/v1/friend-requests", headers=dongje, json={"userId": junhyeong_user["id"]})
    assert sent.status_code == 201
    ((push),) = fcm_api.pushes
    assert push["token"] == "junhyeong-phone"
    assert push["data"] == {"type": "friend.request", "requestId": sent.json()["id"], "fromName": "Dongje"}
    assert push["android"] == {"priority": "HIGH", "ttl": "86400s"}


def test_a_request_that_makes_friends_pushes_nothing(client, sign_up, fcm_api):
    dongje, dongje_user = sign_up("dongje.p", "Dongje")
    junhyeong, junhyeong_user = sign_up("junhyeong.h", "Junhyeong")
    client.post("/api/v1/friend-requests", headers=dongje, json={"userId": junhyeong_user["id"]})
    client.post("/api/v1/devices", headers=dongje, json={"fcmToken": "dongje-phone", "platform": "android"})
    answer = client.post("/api/v1/friend-requests", headers=junhyeong, json={"userId": dongje_user["id"]})
    assert answer.status_code == 200
    assert fcm_api.pushes == []
