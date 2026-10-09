# AI-generated with Claude Code, 2026-10-04, reviewed by Jaewan Park
import asyncio
import base64
import io
import json
from dataclasses import dataclass

import httpx2
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from pix_server.config import Settings
from pix_server.main import create_app


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
def client(settings, clock, image_api):
    app = create_app(settings, clock=clock, image_api=httpx2.MockTransport(image_api))
    with TestClient(app) as client:
        yield client
