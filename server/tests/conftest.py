# AI-generated with Claude Code, 2026-10-26, reviewed by Jaewan Park and Dongje Park
import asyncio
import base64
import io
import json
import os
from dataclasses import dataclass

import fakeredis
import httpx2
import pytest
import redis
from argon2 import PasswordHasher
from fastapi.testclient import TestClient
from PIL import Image
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from pix_server.accounts.google import GoogleAccount, GoogleUnavailable, InvalidGoogleToken
from pix_server.config import Settings
from pix_server.main import create_app
from pix_server.signaling.registry import SessionRegistry
from pix_server.store import SessionStore


@dataclass
class Clock:
    now: float = 1_000

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


def encode_image(image_format: str = "JPEG", size: tuple[int, int] = (768, 1024), exif: bool = False) -> bytes:
    metadata = Image.Exif()
    if exif:
        metadata[0x010F] = "Pix test camera"
    buffer = io.BytesIO()
    Image.new("RGB", size, "teal").save(buffer, image_format, exif=metadata)
    return buffer.getvalue()


TIP = {
    "shot": "full body",
    "pose": "standing",
    "camera_now": "chest",
    "camera_best": "waist",
    "angle": "up",
    "framing": "closer",
    "text": "Zoom in or take a step closer, hold the phone at your waist and tilt it up a little - the legs look longer.",
}

SUGGESTED_POSES = [
    "Lean one shoulder on the door and smile at the camera",
    "Walk toward the camera and look to the side",
    "Sit on the step with the elbows on the knees",
    "Wave with one hand and laugh",
]


class ImageApi:
    """Stands in for OpenRouter. The Image API records each request in `requests` and answers with `status` and
    `body` after `delay`; chat completions, which suggest poses, record theirs in `suggestion_requests` and answer with
    `suggestion_status` and `suggestion_body` after `suggestion_delay`, except tip requests (the `tip` schema), which
    go to `tip_requests` and get `tip_status` and `tip_body` after `tip_delay`."""

    def __init__(self) -> None:
        self.requests: list[httpx2.Request] = []
        self.suggestion_requests: list[httpx2.Request] = []
        self.tip_requests: list[httpx2.Request] = []
        self.delay = 0.0
        self.suggestion_delay = 0.0
        self.tip_delay = 0.0
        self.cancelled = asyncio.Event()
        self.returns(encode_image())
        self.suggests(SUGGESTED_POSES)
        self.tips(TIP)

    def returns(self, image: bytes) -> None:
        self.status = 200
        self.body: object = {"data": [{"b64_json": base64.b64encode(image).decode()}]}

    def fails(self, status: int, message: str = "Provider returned error") -> None:
        self.status = status
        self.body = {"error": {"code": status, "message": message}}

    def suggests(self, poses: object) -> None:
        self.answers(json.dumps({"poses": poses}))

    def answers(self, content: object) -> None:
        """The suggestion model's message, as written; `suggests` writes the JSON the prompt asks for."""
        self.suggestion_status = 200
        self.suggestion_body: object = {"choices": [{"message": {"role": "assistant", "content": content}}]}

    def suggestion_fails(self, status: int, message: str = "Provider returned error") -> None:
        self.suggestion_status = status
        self.suggestion_body = {"error": {"code": status, "message": message}}

    def tips(self, fields: dict) -> None:
        self.tip_answers(json.dumps(fields))

    def tip_answers(self, content: object) -> None:
        """The tip model's message, as written; `tips` writes the JSON the schema asks for."""
        self.tip_status = 200
        self.tip_body: object = {"choices": [{"message": {"role": "assistant", "content": content}}]}

    def tip_fails(self, status: int, message: str = "Provider returned error") -> None:
        self.tip_status = status
        self.tip_body = {"error": {"code": status, "message": message}}

    @property
    def payloads(self) -> list[dict]:
        return [json.loads(request.content) for request in self.requests]

    @property
    def suggestion_payloads(self) -> list[dict]:
        return [json.loads(request.content) for request in self.suggestion_requests]

    @property
    def tip_payloads(self) -> list[dict]:
        return [json.loads(request.content) for request in self.tip_requests]

    async def __call__(self, request: httpx2.Request) -> httpx2.Response:
        schema = json.loads(request.content or b"{}").get("response_format", {}).get("json_schema", {})
        if request.url.path == "/api/v1/chat/completions" and schema.get("name") == "tip":
            self.tip_requests.append(request)
            await asyncio.sleep(self.tip_delay)
            return httpx2.Response(self.tip_status, json=self.tip_body)
        if request.url.path == "/api/v1/chat/completions":
            self.suggestion_requests.append(request)
            await asyncio.sleep(self.suggestion_delay)
            return httpx2.Response(self.suggestion_status, json=self.suggestion_body)
        self.requests.append(request)
        try:
            await asyncio.sleep(self.delay)
        except asyncio.CancelledError:
            self.cancelled.set()
            raise
        return httpx2.Response(self.status, json=self.body)


class FakeGoogle:
    """Stands in for Google's token check. A token `google:<subject>` or `google:<subject>:<name>` is valid; any
    other is invalid. `available = False` makes every check fail as if Google's keys could not be fetched, and
    `delay` makes Google slow to answer; `asked` is set once a check has started."""

    def __init__(self) -> None:
        self.available = True
        self.delay = 0.0
        self.asked = asyncio.Event()

    async def verify(self, id_token: str) -> GoogleAccount:
        self.asked.set()
        await asyncio.sleep(self.delay)
        if not self.available:
            raise GoogleUnavailable("Google's keys could not be fetched")
        prefix, _, rest = id_token.partition(":")
        subject, _, name = rest.partition(":")
        if prefix != "google" or not subject:
            raise InvalidGoogleToken("Not a test token")
        return GoogleAccount(subject=subject, name=name or None)


async def empty_database(url: str) -> None:
    engine = create_async_engine(url)
    async with engine.begin() as connection:
        await connection.execute(text("DROP SCHEMA public CASCADE"))
        await connection.execute(text("CREATE SCHEMA public"))
    await engine.dispose()


@pytest.fixture
def clock():
    return Clock()


@pytest.fixture
def make_image():
    return encode_image


@pytest.fixture
def image_api():
    return ImageApi()


@pytest.fixture
def database_url(tmp_path):
    """A new SQLite file per test, or, with PIX_TEST_DATABASE_URL set, that PostgreSQL emptied first, which shows
    that the migrations and queries behave the same on the servers' database."""
    url = os.environ.get("PIX_TEST_DATABASE_URL")
    if url is None:
        return f"sqlite+aiosqlite:///{tmp_path / 'pix.db'}"
    asyncio.run(empty_database(url))
    return url


@pytest.fixture
def settings(database_url):
    return Settings(
        _env_file=None,
        database_url=database_url,
        google_client_id="test-client.apps.googleusercontent.com",
        jwt_secret="test-jwt-secret-that-is-long-enough-for-hs256",
        access_token_minutes=15,
        session_ttl_days=30,
        room_ttl_seconds=600,
        empty_session_ttl_seconds=60,
        cleanup_interval_seconds=0.01,
        max_message_bytes=65_536,
        outbound_queue_size=128,
        send_timeout_seconds=5,
        openrouter_api_key="test-key",
        pose_model="bytedance-seed/seedream-5-0-flash",
        pose_upstream_timeout_seconds=25,
        pose_rate_limit=20,
        pose_rate_window_seconds=3600,
        pose_global_limit=500,
        pose_global_window_seconds=86400,
        suggestion_model="z-ai/glm-5.3-flash",
        suggestion_timeout_seconds=8,
        suggestion_rate_limit=10,
        suggestion_global_limit=500,
        tip_model="z-ai/glm-5.3-flash",
        tip_reasoning="low",
        tip_timeout_seconds=10,
        tip_rate_limit=20,
        tip_global_limit=1000,
        join_failure_limit=10,
        join_failure_window_seconds=600,
    )


@pytest.fixture
def redis_server():
    """The in-process Redis of one test. `connected = False` on it makes every command fail, like an outage.

    With PIX_TEST_REDIS_URL set the tests use that Redis instead, which shows that the scripts behave the same
    on the real server; tests that need an outage are skipped then.
    """
    url = os.environ.get("PIX_TEST_REDIS_URL")
    if url is None:
        return fakeredis.FakeServer()
    with redis.Redis.from_url(url) as real:
        real.flushdb()
    return None


@pytest.fixture
def open_store(redis_server):
    """Opens one more connection to the test's Redis; each app or registry gets its own, like each worker does."""
    if redis_server is None:
        return lambda: redis.asyncio.Redis.from_url(os.environ["PIX_TEST_REDIS_URL"], decode_responses=True)
    return lambda: fakeredis.FakeAsyncRedis(server=redis_server, decode_responses=True)


@pytest.fixture
def outage(redis_server):
    """Switches the session store off and on."""
    if redis_server is None:
        pytest.skip("An outage can only be simulated on the in-process Redis")

    def switch(down: bool) -> None:
        redis_server.connected = not down

    return switch


@pytest.fixture
def restart_store(redis_server):
    """Empties the test's Redis, as a restart does, since the session store keeps nothing on disk."""

    def restart() -> None:
        if redis_server is None:
            with redis.Redis.from_url(os.environ["PIX_TEST_REDIS_URL"]) as real:
                real.flushdb()
        else:
            fakeredis.FakeRedis(server=redis_server).flushdb()

    return restart


@pytest.fixture
async def make_registry(settings, clock, open_store):
    """Starts registries that share the test's Redis: each one stands for a worker process.

    Nothing runs in the background unless a test shortens the interval, so a test decides when the sweeper runs.
    """
    settings.cleanup_interval_seconds = 3600
    started = []

    async def make() -> SessionRegistry:
        connection = open_store()
        registry = SessionRegistry(settings, SessionStore(connection, settings, clock))
        await registry.start()
        started.append((registry, connection))
        return registry

    yield make
    for registry, connection in started:
        await registry.stop()
        await connection.aclose()


@pytest.fixture
def google():
    return FakeGoogle()


@pytest.fixture
def client(settings, clock, image_api, open_store, google):
    app = create_app(
        settings,
        clock=clock,
        image_api=httpx2.MockTransport(image_api),
        redis=open_store,
        google=google,
        # Real argon2id at the lowest cost, so a test hashes in microseconds.
        password_hasher=PasswordHasher(time_cost=1, memory_cost=8, parallelism=1),
    )
    with TestClient(app) as client:
        yield client


@pytest.fixture
def sign_up(client):
    """Signs up a password account and returns the headers that authenticate it, and the account."""

    def sign_up(pix_id: str, name: str | None = None, password: str = "sunset2026") -> tuple[dict, dict]:
        response = client.post(
            "/api/v1/auth/signup", json={"pixId": pix_id, "password": password, "displayName": name or pix_id}
        )
        assert response.status_code == 201, response.text
        body = response.json()
        return {"Authorization": f"Bearer {body['accessToken']}"}, body["user"]

    return sign_up
