# AI-generated with Claude Code, 2026-10-25, reviewed by Sungmin Jo
"""`TurnRelay` against a fake of Cloudflare's TURN API (Design Documentation 2.5.2, 2.6.7, AD-14).

Partitions: the key and the token set, or either one missing or empty (no request); Cloudflare answering with ICE
servers, with an error status, unreachable, or later than the timeout; an answer in another shape (not JSON, not an
object, without `iceServers`, an empty list, an entry that is not an object, `urls` missing, empty, or not text, a
username or credential that is not text) and one with extra fields or a single URL as text, which the app still reads;
the token and the credential never in the log.
"""

import json
import logging

import httpx2
import pytest
from pydantic import SecretStr

from pix_server.turn import TurnRelay, stun_only

STUN_ONLY = [{"urls": ["stun:stun.cloudflare.com:3478"]}]


async def ice_servers(settings, handler) -> list[dict]:
    async with httpx2.AsyncClient(transport=httpx2.MockTransport(handler)) as http:
        return await TurnRelay(settings, http).ice_servers()


async def test_configured_relay_asks_cloudflare_for_credentials_and_passes_them_on(settings, turn_api):
    servers = await ice_servers(settings, turn_api)

    assert servers == turn_api.servers
    request = turn_api.requests[0]
    assert request.method == "POST"
    assert str(request.url) == (
        "https://rtc.live.cloudflare.com/v1/turn/keys/test-turn-key/credentials/generate-ice-servers"
    )
    assert request.headers["Authorization"] == "Bearer test-turn-token"
    assert json.loads(request.content) == {"ttl": 14400}


@pytest.mark.parametrize(
    ("key_id", "token"), [(None, "test-turn-token"), ("", "test-turn-token"), ("key", None), ("key", "")]
)
async def test_relay_without_a_key_or_token_gives_stun_only_and_asks_nothing(settings, turn_api, key_id, token):
    settings.turn_key_id = key_id
    settings.turn_api_token = SecretStr(token) if token is not None else None

    assert await ice_servers(settings, turn_api) == STUN_ONLY
    assert turn_api.requests == []


@pytest.mark.parametrize("status", [400, 401, 404, 500, 503])
async def test_an_error_status_gives_stun_only(settings, turn_api, status):
    turn_api.status = status

    assert await ice_servers(settings, turn_api) == STUN_ONLY


async def test_an_unreachable_api_gives_stun_only(settings):
    def refuse(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ConnectError("Connection refused", request=request)

    assert await ice_servers(settings, refuse) == STUN_ONLY


async def test_an_answer_later_than_the_timeout_gives_stun_only(settings, turn_api):
    settings.turn_timeout_seconds = 0.05
    turn_api.delay = 1

    assert await ice_servers(settings, turn_api) == STUN_ONLY


@pytest.mark.parametrize(
    "body",
    [
        ["not", "an", "object"],
        {"servers": []},
        {"iceServers": []},
        {"iceServers": {"urls": ["stun:stun.cloudflare.com:3478"]}},
        {"iceServers": ["stun:stun.cloudflare.com:3478"]},
        {"iceServers": [{"username": "u", "credential": "c"}]},
        {"iceServers": [{"urls": []}]},
        {"iceServers": [{"urls": [3478]}]},
        {"iceServers": [{"urls": ["turn:turn.cloudflare.com:3478"], "username": 7, "credential": "c"}]},
        {"iceServers": [{"urls": ["turn:turn.cloudflare.com:3478"], "username": "u", "credential": None}]},
    ],
)
async def test_an_answer_in_another_shape_gives_stun_only(settings, turn_api, body):
    turn_api.body = body

    assert await ice_servers(settings, turn_api) == STUN_ONLY


async def test_an_answer_that_is_not_json_gives_stun_only(settings):
    def garbled(request: httpx2.Request) -> httpx2.Response:
        return httpx2.Response(201, text="<html>Bad gateway</html>")

    assert await ice_servers(settings, garbled) == STUN_ONLY


async def test_extra_fields_are_dropped_and_a_single_url_becomes_a_list(settings, turn_api):
    turn_api.body = {
        "iceServers": [
            {"urls": "stun:stun.cloudflare.com:3478", "note": "extra"},
            {"urls": ["turn:turn.cloudflare.com:3478?transport=udp"], "username": "u", "credential": "c", "ttl": 60},
        ],
        "expires": 60,
    }

    assert await ice_servers(settings, turn_api) == [
        {"urls": ["stun:stun.cloudflare.com:3478"]},
        {"urls": ["turn:turn.cloudflare.com:3478?transport=udp"], "username": "u", "credential": "c"},
    ]


async def test_the_log_names_the_status_but_never_the_token_or_the_credential(settings, turn_api, caplog):
    turn_api.status = 401
    turn_api.body = {"errors": [{"message": "bad token test-turn-token"}], "credential": "test-turn-credential"}

    with caplog.at_level(logging.DEBUG, logger="pix_server.turn"):
        await ice_servers(settings, turn_api)
        turn_api.status = 201
        turn_api.body = {"iceServers": turn_api.servers}
        await ice_servers(settings, turn_api)

    assert "401" in caplog.text
    assert "test-turn-token" not in caplog.text
    assert "test-turn-credential" not in caplog.text
    assert len(caplog.records) == 1


def test_stun_only_is_a_new_list_each_time():
    first = stun_only()
    first[0]["urls"].append("stun:example.com:3478")

    assert stun_only() == STUN_ONLY
