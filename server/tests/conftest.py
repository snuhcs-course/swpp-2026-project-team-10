# AI-generated with Claude Code, 2026-10-14, reviewed by Jaewan Park
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
from fastapi.testclient import TestClient
from PIL import Image

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


class ImageApi:
    """Stands in for OpenRouter's Image API: records each request and answers with `status` and `body`."""

    def __init__(self) -> None:
        self.requests: list[httpx2.Request] = []
        self.delay = 0.0
        self.cancelled = asyncio.Event()
        self.returns(encode_image())

    def returns(self, image: bytes) -> None:
        self.status = 200
        self.body: object = {"data": [{"b64_json": base64.b64encode(image).decode()}]}

    def fails(self, status: int, message: str = "Provider returned error") -> None:
        self.status = status
        self.body = {"error": {"code": status, "message": message}}

    @property
    def payloads(self) -> list[dict]:
        return [json.loads(request.content) for request in self.requests]

    async def __call__(self, request: httpx2.Request) -> httpx2.Response:
        self.requests.append(request)
        try:
            await asyncio.sleep(self.delay)
        except asyncio.CancelledError:
            self.cancelled.set()
            raise
        return httpx2.Response(self.status, json=self.body)


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
def settings():
    return Settings(
        _env_file=None,
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
def client(settings, clock, image_api, open_store):
    app = create_app(settings, clock=clock, image_api=httpx2.MockTransport(image_api), redis=open_store)
    with TestClient(app) as client:
        yield client
